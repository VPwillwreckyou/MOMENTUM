'use strict';

const { app, BrowserWindow, Menu, dialog, protocol, session, shell } = require('electron');
const fs = require('node:fs');
const fsp = require('node:fs/promises');
const path = require('node:path');
const { startLocalBackend } = require('./backend.cjs');

const scheme = 'momentum';
const repoRoot = path.resolve(__dirname, '..', '..');
const packagedWebRoot = path.join(process.resourcesPath, 'web');
const developmentWebRoot = path.resolve(__dirname, '..', 'web', 'dist');
const repositoryWebRoot = path.resolve(__dirname, '..', 'web', 'dist');
const fallbackWebRoot = app.isPackaged
  ? packagedWebRoot
  : fs.existsSync(developmentWebRoot)
    ? developmentWebRoot
    : repositoryWebRoot;

const devUrl = process.env.MOMENTUM_DESKTOP_DEV_URL || '';
let mainWindow = null;
let localService = null;
let quitting = false;

protocol.registerSchemesAsPrivileged([
  {
    scheme,
    privileges: {
      standard: true,
      secure: true,
      supportFetchAPI: true,
      corsEnabled: true,
      stream: true,
    },
  },
]);

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.webmanifest': 'application/manifest+json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.webp': 'image/webp',
  '.woff2': 'font/woff2',
  '.woff': 'font/woff',
  '.ico': 'image/x-icon',
};

const CSP = [
  "default-src 'self'",
  "script-src 'self'",
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob:",
  "font-src 'self' data:",
  "media-src 'self' blob:",
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  "frame-ancestors 'none'",
].join('; ');

async function resolveAsset(requestUrl) {
  const url = new URL(requestUrl);
  let pathname = decodeURIComponent(url.pathname);
  if (!pathname || pathname === '/') pathname = '/index.html';
  let target = path.resolve(fallbackWebRoot, `.${pathname}`);
  if (!target.startsWith(fallbackWebRoot)) return null;
  try {
    const stat = await fsp.stat(target);
    if (stat.isDirectory()) target = path.join(target, 'index.html');
    await fsp.access(target);
  } catch {
    if (path.extname(pathname)) return null;
    target = path.join(fallbackWebRoot, 'index.html');
  }
  try {
    await fsp.access(target);
    return target;
  } catch {
    return null;
  }
}

async function registerProtocol() {
  await protocol.handle(scheme, async (request) => {
    const target = await resolveAsset(request.url);
    if (!target) {
      return new Response('Not found', { status: 404, headers: { 'Content-Type': 'text/plain' } });
    }
    try {
      const data = await fsp.readFile(target);
      const extension = path.extname(target).toLowerCase();
      return new Response(data, {
        status: 200,
        headers: {
          'Content-Type': MIME[extension] || 'application/octet-stream',
          'Content-Security-Policy': CSP,
          'X-Content-Type-Options': 'nosniff',
          'Cache-Control': extension === '.html' ? 'no-cache' : 'public, max-age=3600',
        },
      });
    } catch {
      return new Response('Could not read application asset.', { status: 500 });
    }
  });
}

function createWindow(targetUrl) {
  const iconPath = path.join(fallbackWebRoot, 'icons', 'icon-512.png');
  mainWindow = new BrowserWindow({
    width: 1440,
    height: 960,
    minWidth: 900,
    minHeight: 640,
    show: false,
    backgroundColor: '#06080d',
    title: 'MOMENTUM',
    icon: fs.existsSync(iconPath) ? iconPath : undefined,
    autoHideMenuBar: true,
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      webSecurity: true,
      spellcheck: true,
    },
  });

  mainWindow.once('ready-to-show', () => mainWindow?.show());
  mainWindow.on('closed', () => {
    mainWindow = null;
  });

  let allowedOrigin = '';
  let allowedPrefix = '';
  try {
    const parsed = new URL(targetUrl);
    if (parsed.protocol === 'http:' || parsed.protocol === 'https:') {
      allowedOrigin = parsed.origin;
    } else {
      allowedPrefix = `${parsed.protocol}//${parsed.host}`;
    }
  } catch {
    allowedPrefix = `${scheme}://app`;
  }

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:/i.test(url)) void shell.openExternal(url);
    return { action: 'deny' };
  });

  mainWindow.webContents.on('will-navigate', (event, url) => {
    const permitted =
      (allowedOrigin && url.startsWith(allowedOrigin)) ||
      (allowedPrefix && url.startsWith(allowedPrefix));
    if (!permitted) {
      event.preventDefault();
      if (/^https?:/i.test(url)) void shell.openExternal(url);
    }
  });

  session.defaultSession.setPermissionRequestHandler((_webContents, _permission, callback) => {
    callback(false);
  });

  void mainWindow.loadURL(targetUrl);
  mainWindow.webContents.on('did-fail-load', (_event, code, description) => {
    console.error(`[desktop] Failed to load interface (${code}): ${description}`);
  });
}

function buildMenu() {
  const target = localService?.baseUrl ?? 'http://127.0.0.1:8799';
  const template = [
    {
      label: 'MOMENTUM',
      submenu: [{ role: 'about' }, { type: 'separator' }, { role: 'quit' }],
    },
    { role: 'editMenu' },
    {
      label: 'View',
      submenu: [
        { role: 'reload' },
        { role: 'forceReload' },
        { type: 'separator' },
        { role: 'resetZoom' },
        { role: 'zoomIn' },
        { role: 'zoomOut' },
        { type: 'separator' },
        { role: 'toggleDevTools' },
      ],
    },
    {
      role: 'help',
      submenu: [
        {
          label: 'Open in default browser',
          click: () => void shell.openExternal(target),
        },
        {
          label: 'Open local service log',
          click: () => {
            const logPath = localService?.logPath;
            if (logPath) void shell.openPath(logPath);
          },
        },
      ],
    },
  ];
  Menu.setApplicationMenu(Menu.buildFromTemplate(template));
}

async function start() {
  let targetUrl = `${scheme}://app/`;
  try {
    localService = await startLocalBackend({
      isPackaged: app.isPackaged,
      resourcesPath: process.resourcesPath,
      repoRoot,
      userDataPath: app.getPath('userData'),
      port: Number(process.env.MOMENTUM_DESKTOP_PORT || 8799),
    });
    targetUrl = devUrl || localService.baseUrl;
    console.log(`[desktop] local service ready at ${localService.baseUrl}`);
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    console.error('[desktop] local service failed', detail);
    dialog.showErrorBox(
      'MOMENTUM local service unavailable',
      `${detail}\n\nMOMENTUM will open in offline mode. Goals, planning, search, backups and exports still work. AI and encrypted sync need the local Java service.`,
    );
    targetUrl = devUrl || `${scheme}://app/`;
  }

  buildMenu();
  createWindow(targetUrl);
}

const gotLock = app.requestSingleInstanceLock();
if (!gotLock) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (!mainWindow) return;
    if (mainWindow.isMinimized()) mainWindow.restore();
    mainWindow.show();
    mainWindow.focus();
  });

  app.whenReady().then(async () => {
    app.setAppUserModelId('com.momentum.desktop');
    await registerProtocol();
    await start();
    app.on('activate', () => {
      if (BrowserWindow.getAllWindows().length === 0) void start();
    });
  });

  app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') app.quit();
  });

  app.on('before-quit', (event) => {
    if (quitting || !localService) return;
    event.preventDefault();
    quitting = true;
    void Promise.resolve(localService.stop()).finally(() => app.quit());
  });
}
