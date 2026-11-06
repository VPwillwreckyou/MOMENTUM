'use strict';

const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const { spawn } = require('node:child_process');

const DEFAULT_PORT = 8799;

function parseEnv(contents) {
  const values = {};
  for (const line of contents.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const separator = trimmed.indexOf('=');
    if (separator <= 0) continue;
    const key = trimmed.slice(0, separator).trim();
    let value = trimmed.slice(separator + 1).trim();
    if (
      (value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1);
    }
    values[key] = value;
  }
  return values;
}

function readEnvFile(filePath) {
  try {
    return parseEnv(fs.readFileSync(filePath, 'utf8'));
  } catch {
    return {};
  }
}

function findJavaExecutable() {
  const executable = process.platform === 'win32' ? 'java.exe' : 'java';
  if (process.env.JAVA_HOME) {
    const candidate = path.join(process.env.JAVA_HOME, 'bin', executable);
    if (fs.existsSync(candidate)) return candidate;
  }
  return process.platform === 'win32' ? 'java.exe' : 'java';
}

function healthCheck(baseUrl, timeoutMs = 900) {
  return new Promise((resolve) => {
    let settled = false;
    const finish = (value) => {
      if (settled) return;
      settled = true;
      resolve(value);
    };
    const request = http.get(`${baseUrl}/api/health`, { timeout: timeoutMs }, (response) => {
      let body = '';
      response.setEncoding('utf8');
      response.on('data', (chunk) => {
        body += chunk;
        if (body.length > 8192) request.destroy();
      });
      response.on('end', () => {
        try {
          const parsed = JSON.parse(body);
          finish(response.statusCode === 200 && parsed.status === 'ok' && Boolean(parsed.version));
        } catch {
          finish(false);
        }
      });
    });
    request.on('timeout', () => {
      request.destroy();
      finish(false);
    });
    request.on('error', () => finish(false));
  });
}

async function waitForHealth(baseUrl, timeoutMs = 25000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (await healthCheck(baseUrl)) return true;
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  return false;
}

function resolveRuntime(options) {
  const isPackaged = Boolean(options.isPackaged);
  const resourcesPath = options.resourcesPath;
  const repoRoot = options.repoRoot;
  const port = Number(options.port || process.env.MOMENTUM_DESKTOP_PORT || DEFAULT_PORT);
  const baseUrl = `http://127.0.0.1:${port}`;
  return {
    port,
    baseUrl,
    webRoot: isPackaged
      ? path.join(resourcesPath, 'web')
      : path.join(repoRoot, 'apps', 'web', 'dist'),
    jarPath: isPackaged
      ? path.join(resourcesPath, 'api', 'momentum-api.jar')
      : path.join(repoRoot, 'services', 'api', 'build', 'momentum-api.jar'),
    envFile: isPackaged
      ? path.join(resourcesPath, 'runtime.env')
      : path.join(repoRoot, '.env'),
    dataDir: path.join(options.userDataPath, 'data'),
    logDir: path.join(options.userDataPath, 'logs'),
    isPackaged,
  };
}

async function startLocalBackend(options) {
  const runtime = resolveRuntime(options);
  if (await healthCheck(runtime.baseUrl)) {
    return {
      started: false,
      baseUrl: runtime.baseUrl,
      logPath: null,
      stop: async () => {},
    };
  }

  if (!fs.existsSync(runtime.jarPath)) {
    throw new Error(`MOMENTUM service jar is missing: ${runtime.jarPath}`);
  }
  if (!fs.existsSync(path.join(runtime.webRoot, 'index.html'))) {
    throw new Error(`MOMENTUM web build is missing: ${runtime.webRoot}`);
  }

  fs.mkdirSync(runtime.dataDir, { recursive: true });
  fs.mkdirSync(runtime.logDir, { recursive: true });
  const logPath = path.join(runtime.logDir, 'local-service.log');
  const logFd = fs.openSync(logPath, 'a');
  const runtimeEnv = readEnvFile(runtime.envFile);
  const env = {
    ...process.env,
    ...runtimeEnv,
    MOMENTUM_HOST: '127.0.0.1',
    MOMENTUM_PORT: String(runtime.port),
    MOMENTUM_PUBLIC_URL: runtime.baseUrl,
    MOMENTUM_APP_URL: runtime.baseUrl,
    MOMENTUM_COOKIE_SECURE: 'false',
    MOMENTUM_DATA_DIR: runtime.dataDir,
    MOMENTUM_WEB_DIST: runtime.webRoot,
  };

  const child = spawn(findJavaExecutable(), ['-jar', runtime.jarPath], {
    cwd: path.dirname(runtime.jarPath),
    env,
    windowsHide: true,
    stdio: ['ignore', logFd, logFd],
  });

  const startupFailure = new Promise((resolve) => {
    child.once('error', (error) => {
      resolve(new Error(`Could not start Java: ${error.message}`));
    });
    child.once('exit', (code) => {
      resolve(new Error(`Local service exited with code ${code}. See ${logPath}`));
    });
  });

  const startupResult = await Promise.race([
    waitForHealth(runtime.baseUrl, 25000).then((healthy) => healthy || new Error('timeout')),
    startupFailure,
  ]);
  if (startupResult !== true) {
    try {
      child.kill();
    } catch {
      // Ignore cleanup failure and report the startup error below.
    }
    fs.closeSync(logFd);
    const reason =
      startupResult instanceof Error && startupResult.message === 'timeout'
        ? `The local MOMENTUM service did not become ready. See ${logPath}`
        : startupResult.message;
    throw new Error(reason);
  }

  const stop = async () => {
    if (child.exitCode !== null || child.killed) return;
    try {
      child.kill();
    } catch {
      // Fall back to taskkill below on Windows.
    }
    if (process.platform === 'win32' && child.pid) {
      await new Promise((resolve) => {
        const killer = spawn('taskkill', ['/pid', String(child.pid), '/T', '/F'], {
          windowsHide: true,
          stdio: 'ignore',
        });
        killer.once('exit', resolve);
        killer.once('error', resolve);
      });
    }
    try {
      fs.closeSync(logFd);
    } catch {
      // Already closed.
    }
  };

  child.once('exit', () => {
    try {
      fs.closeSync(logFd);
    } catch {
      // Already closed.
    }
  });

  return {
    started: true,
    baseUrl: runtime.baseUrl,
    logPath,
    dataDir: runtime.dataDir,
    stop,
  };
}

module.exports = {
  DEFAULT_PORT,
  findJavaExecutable,
  healthCheck,
  parseEnv,
  readEnvFile,
  resolveRuntime,
  startLocalBackend,
  waitForHealth,
};
