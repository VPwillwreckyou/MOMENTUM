using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Net;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Windows.Forms;

internal static class Program
{
    [STAThread]
    private static void Main()
    {
        try
        {
            Run();
        }
        catch (Exception exception)
        {
            Log("launcher failed: " + exception);
            MessageBox.Show(exception.Message, "MOMENTUM", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }

    private static void Run()
    {
        string baseDir = AppDomain.CurrentDomain.BaseDirectory.TrimEnd(Path.DirectorySeparatorChar);
        Log("launcher start user=" + Environment.UserName
            + " localappdata=" + Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData));
        LoadEnvFile(Path.Combine(baseDir, ".env"));

        string port = EnvironmentValue("MOMENTUM_PORT", "8787");
        string host = EnvironmentValue("MOMENTUM_HOST", "127.0.0.1");
        if (host == "0.0.0.0") host = "127.0.0.1";
        string url = "http://" + host + ":" + port;

        Environment.SetEnvironmentVariable("MOMENTUM_PORT", port);
        Environment.SetEnvironmentVariable("MOMENTUM_HOST", host);
        Environment.SetEnvironmentVariable("MOMENTUM_PUBLIC_URL", url);
        Environment.SetEnvironmentVariable("MOMENTUM_APP_URL", url);
        Environment.SetEnvironmentVariable("MOMENTUM_COOKIE_SECURE", "false");
        if (string.IsNullOrWhiteSpace(Environment.GetEnvironmentVariable("MOMENTUM_DATA_DIR")))
            Environment.SetEnvironmentVariable("MOMENTUM_DATA_DIR", Path.Combine(baseDir, "data"));
        if (string.IsNullOrWhiteSpace(Environment.GetEnvironmentVariable("MOMENTUM_WEB_DIST")))
            Environment.SetEnvironmentVariable("MOMENTUM_WEB_DIST", Path.Combine(baseDir, "apps", "web", "dist"));

        if (!IsHealthy(url))
        {
            Log("service not running, starting " + url);
            string jar = Path.Combine(baseDir, "services", "api", "build", "momentum-api.jar");
            if (!File.Exists(jar))
                throw new FileNotFoundException("MOMENTUM service is missing:\n" + jar);
            string sqliteJar = Path.Combine(baseDir, "services", "api", "lib", "sqlite-jdbc.jar");
            string slf4jJar = Path.Combine(baseDir, "services", "api", "lib", "slf4j-api.jar");

            string java = FindJava();
            string classpath = jar;
            if (File.Exists(sqliteJar)) classpath += ";" + sqliteJar;
            if (File.Exists(slf4jJar)) classpath += ";" + slf4jJar;
            var service = new ProcessStartInfo(java)
            {
                Arguments = "-cp \"" + classpath + "\" dev.momentum.MomentumServer",
                WorkingDirectory = Path.GetDirectoryName(jar),
                UseShellExecute = false,
                CreateNoWindow = true,
                WindowStyle = ProcessWindowStyle.Hidden,
            };
            Process.Start(service);

            if (!WaitForHealth(url, 60000))
                throw new Exception("The local MOMENTUM service did not start. Check that Java 21+ is installed.");
        }

        OpenAppWindow(url);
    }

    private static void OpenAppWindow(string url)
    {
        string profile = ResolveProfileDirectory();
        Log("app profile: " + profile);

        if (FocusExistingAppWindow()) return;

        var failures = new List<string>();

        foreach (string browser in FindBrowsers())
        {
            bool needsReset;
            if (TryOpenAppWindow(browser, url, profile, failures, out needsReset))
            {
                Log("opened app window with " + browser);
                return;
            }

            if (needsReset && ResetProfile(profile))
            {
                Log("resetting app profile and retrying " + browser);
                if (TryOpenAppWindow(browser, url, profile, failures, out needsReset))
                {
                    Log("opened app window with " + browser + " after profile reset");
                    return;
                }
            }
        }

        try
        {
            Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });
            Log("opened URL in default browser");
            return;
        }
        catch (Exception exception)
        {
            failures.Add("default browser: " + exception.Message);
        }

        string details = string.Join(Environment.NewLine, failures.ToArray());
        Log("could not open app window:" + Environment.NewLine + details);
        throw new Exception("Could not open the MOMENTUM window." + Environment.NewLine + Environment.NewLine + details);
    }

    private static bool TryOpenAppWindow(string browser, string url, string profile, List<string> failures, out bool needsReset)
    {
        needsReset = false;
        string arguments = "--app=" + url
            + " --user-data-dir=\"" + profile + "\""
            + " --no-first-run --no-default-browser-check";

        var info = new ProcessStartInfo(browser)
        {
            Arguments = arguments,
            UseShellExecute = false,
            CreateNoWindow = true,
            RedirectStandardError = true,
            RedirectStandardOutput = true,
        };

        Process process;
        try
        {
            process = Process.Start(info);
            Log("started " + browser + " pid=" + (process == null ? "null" : process.Id.ToString()));
        }
        catch (Exception exception)
        {
            failures.Add(browser + ": " + exception.Message);
            Log("failed to start " + browser + ": " + exception.Message);
            return false;
        }

        if (process == null)
        {
            failures.Add(browser + ": process did not start");
            return false;
        }

        var stderr = new StringBuilder();
        process.ErrorDataReceived += delegate(object sender, DataReceivedEventArgs args)
        {
            if (args.Data != null) AppendLine(stderr, args.Data);
        };
        process.OutputDataReceived += delegate(object sender, DataReceivedEventArgs args)
        {
            if (args.Data != null) AppendLine(stderr, args.Data);
        };
        process.BeginErrorReadLine();
        process.BeginOutputReadLine();

        var watch = Stopwatch.StartNew();
        string lastTitle = string.Empty;
        while (watch.ElapsedMilliseconds < 25000)
        {
            if (process.HasExited)
            {
                if (process.ExitCode == 0 && FocusExistingAppWindow())
                {
                    Log(browser + " handed off to the existing app window");
                    return true;
                }

                failures.Add(browser + ": exited with code " + process.ExitCode + StderrSuffix(stderr));
                Log(browser + " exited with code " + process.ExitCode + StderrSuffix(stderr));
                needsReset = process.ExitCode != 0;
                return false;
            }

            process.Refresh();
            if (process.MainWindowHandle != IntPtr.Zero)
            {
                lastTitle = process.MainWindowTitle;
                if (lastTitle.IndexOf("MOMENTUM", StringComparison.OrdinalIgnoreCase) >= 0)
                    return true;
            }

            Thread.Sleep(250);
        }

        failures.Add(browser + ": window did not load the app (title: '" + lastTitle + "')" + StderrSuffix(stderr));
        Log(browser + ": window did not load the app (title: '" + lastTitle + "')");
        KillTree(process);
        needsReset = true;
        return false;
    }

    private static void KillTree(Process process)
    {
        try
        {
            if (process.HasExited) return;
        }
        catch
        {
            return;
        }

        try
        {
            var killer = Process.Start(new ProcessStartInfo("taskkill", "/PID " + process.Id + " /T /F")
            {
                UseShellExecute = false,
                CreateNoWindow = true,
            });
            if (killer != null) killer.WaitForExit(5000);
        }
        catch (Exception exception)
        {
            Log("could not kill " + process.Id + ": " + exception.Message);
        }
    }

    private static bool ResetProfile(string profile)
    {
        try
        {
            if (Directory.Exists(profile)) Directory.Delete(profile, true);
            Directory.CreateDirectory(profile);
            return true;
        }
        catch (Exception exception)
        {
            Log("could not reset app profile: " + exception.Message);
            return false;
        }
    }

    private static void AppendLine(StringBuilder buffer, string line)
    {
        lock (buffer)
        {
            buffer.AppendLine(line);
            if (buffer.Length > 4000) buffer.Remove(0, buffer.Length - 4000);
        }
    }

    private static string StderrSuffix(StringBuilder buffer)
    {
        string text;
        lock (buffer) text = buffer.ToString().Trim();
        return text.Length == 0 ? string.Empty : Environment.NewLine + text;
    }

    private static void Log(string message)
    {
        string line = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss") + " " + message + Environment.NewLine;
        string[] targets =
        {
            Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "launcher.log"),
            Path.Combine(Path.GetTempPath(), "momentum", "launcher.log"),
        };
        foreach (string target in targets)
        {
            try
            {
                string directory = Path.GetDirectoryName(target);
                if (!string.IsNullOrEmpty(directory)) Directory.CreateDirectory(directory);
                File.AppendAllText(target, line);
                return;
            }
            catch
            {
                // Try the next location.
            }
        }
    }

    private static string ResolveProfileDirectory()
    {
        string root = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
        if (string.IsNullOrWhiteSpace(root))
            throw new Exception("The Windows LocalAppData folder could not be resolved.");

        string profile = Path.Combine(root, "MOMENTUMAppProfile");
        try
        {
            Directory.CreateDirectory(profile);
        }
        catch (Exception exception)
        {
            throw new Exception("Could not create the MOMENTUM app profile folder:" + Environment.NewLine + profile + Environment.NewLine + exception.Message);
        }
        return profile;
    }

    private delegate bool EnumWindowsProc(IntPtr window, IntPtr extraData);

    [DllImport("user32.dll")]
    private static extern bool EnumWindows(EnumWindowsProc callback, IntPtr extraData);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int GetWindowText(IntPtr window, StringBuilder text, int maxCount);

    [DllImport("user32.dll")]
    private static extern bool IsWindowVisible(IntPtr window);

    [DllImport("user32.dll")]
    private static extern bool SetForegroundWindow(IntPtr window);

    [DllImport("user32.dll")]
    private static extern bool ShowWindow(IntPtr window, int command);

    private static bool FocusExistingAppWindow()
    {
        IntPtr target = IntPtr.Zero;
        EnumWindows(delegate(IntPtr window, IntPtr extraData)
        {
            if (!IsWindowVisible(window)) return true;
            var title = new StringBuilder(512);
            if (GetWindowText(window, title, title.Capacity) <= 0) return true;
            if (title.ToString().IndexOf("MOMENTUM", StringComparison.OrdinalIgnoreCase) < 0) return true;
            target = window;
            return false;
        }, IntPtr.Zero);

        if (target == IntPtr.Zero) return false;

        ShowWindow(target, 9);
        SetForegroundWindow(target);
        Log("focused the existing app window");
        return true;
    }

    private static IEnumerable<string> FindBrowsers()
    {
        string programFiles = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles);
        string programFilesX86 = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86);
        string localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
        var found = new List<string>();
        string[] candidates =
        {
            Path.Combine(programFilesX86, "Microsoft", "Edge", "Application", "msedge.exe"),
            Path.Combine(programFiles, "Microsoft", "Edge", "Application", "msedge.exe"),
            Path.Combine(localAppData, "Microsoft", "Edge", "Application", "msedge.exe"),
            Path.Combine(programFiles, "Google", "Chrome", "Application", "chrome.exe"),
            Path.Combine(programFilesX86, "Google", "Chrome", "Application", "chrome.exe"),
            Path.Combine(localAppData, "Google", "Chrome", "Application", "chrome.exe"),
        };
        foreach (string candidate in candidates)
            if (File.Exists(candidate) && !found.Contains(candidate)) found.Add(candidate);
        return found;
    }

    private static string FindJava()
    {
        string javaHome = Environment.GetEnvironmentVariable("JAVA_HOME");
        if (!string.IsNullOrWhiteSpace(javaHome))
        {
            foreach (string name in new[] { "javaw.exe", "java.exe" })
            {
                string candidate = Path.Combine(javaHome, "bin", name);
                if (File.Exists(candidate)) return candidate;
            }
        }

        string path = Environment.GetEnvironmentVariable("PATH") ?? string.Empty;
        foreach (string directory in path.Split(Path.PathSeparator))
        {
            if (string.IsNullOrWhiteSpace(directory)) continue;
            foreach (string name in new[] { "javaw.exe", "java.exe" })
            {
                try
                {
                    string candidate = Path.Combine(directory.Trim(), name);
                    if (File.Exists(candidate)) return candidate;
                }
                catch (ArgumentException)
                {
                    // Ignore malformed PATH entries.
                }
            }
        }

        return "javaw.exe";
    }

    private static bool IsHealthy(string url)
    {
        try
        {
            var request = (HttpWebRequest)WebRequest.Create(url + "/api/health");
            request.Timeout = 1500;
            request.ReadWriteTimeout = 1500;
            using (var response = (HttpWebResponse)request.GetResponse())
            using (var reader = new StreamReader(response.GetResponseStream()))
            {
                string body = reader.ReadToEnd();
                return response.StatusCode == HttpStatusCode.OK && body.Contains("\"status\"");
            }
        }
        catch
        {
            return false;
        }
    }

    private static bool WaitForHealth(string url, int timeoutMilliseconds)
    {
        var watch = Stopwatch.StartNew();
        while (watch.ElapsedMilliseconds < timeoutMilliseconds)
        {
            if (IsHealthy(url)) return true;
            Thread.Sleep(300);
        }
        return false;
    }

    private static void LoadEnvFile(string filePath)
    {
        if (!File.Exists(filePath)) return;
        foreach (string line in File.ReadAllLines(filePath))
        {
            string trimmed = line.Trim();
            if (trimmed.Length == 0 || trimmed.StartsWith("#")) continue;
            int separator = trimmed.IndexOf('=');
            if (separator <= 0) continue;
            string key = trimmed.Substring(0, separator).Trim();
            string value = trimmed.Substring(separator + 1).Trim().Trim('"').Trim('\'');
            if (Environment.GetEnvironmentVariable(key) == null)
                Environment.SetEnvironmentVariable(key, value);
        }
    }

    private static string EnvironmentValue(string key, string fallback)
    {
        string value = Environment.GetEnvironmentVariable(key);
        return string.IsNullOrWhiteSpace(value) ? fallback : value;
    }
}
