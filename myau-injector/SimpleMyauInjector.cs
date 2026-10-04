// MyauInjector - modern GUI injector for the Myau client.
// Injection technique (from InjectMyau): stage myau_native.dll + the client jar
// into %TEMP%, point the dll at the jar via its sidecar (.dll.txt), then
// CreateRemoteThread + LoadLibraryW. The JVMTI agent finds the running JVM in
// DllMain and appends the jar to the game classloader.
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Text;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Windows.Forms;

namespace MyauInjector
{
    internal class Candidate
    {
        public int Pid;
        public string Image;
        public string CommandLine;
        public int Score;
        public string Label;
    }

    // Rounded flat button with hover/pressed states.
    internal class ModernButton : Button
    {
        private bool hover;
        private bool pressed;

        public ModernButton()
        {
            FlatStyle = FlatStyle.Flat;
            FlatAppearance.BorderSize = 0;
            BackColor = Color.FromArgb(38, 40, 50);
            ForeColor = Color.FromArgb(232, 236, 244);
            Cursor = Cursors.Hand;
            Font = new Font("Segoe UI", 10F, FontStyle.Regular);
            Size = new Size(120, 36);
        }

        protected override void OnMouseEnter(EventArgs e) { hover = true; Invalidate(); base.OnMouseEnter(e); }
        protected override void OnMouseLeave(EventArgs e) { hover = false; pressed = false; Invalidate(); base.OnMouseLeave(e); }
        protected override void OnMouseDown(MouseEventArgs e) { pressed = true; Invalidate(); base.OnMouseDown(e); }
        protected override void OnMouseUp(MouseEventArgs e) { pressed = false; Invalidate(); base.OnMouseUp(e); }

        protected override void OnPaint(PaintEventArgs pevent)
        {
            pevent.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            RectangleF box = new RectangleF(0, 0, Width - 1, Height - 1);
            float radius = Height / 2f;
            using (GraphicsPath path = Rounded(box, radius))
            {
                Color fill = pressed ? Color.FromArgb(28, 30, 38)
                    : hover ? Color.FromArgb(52, 55, 68) : Color.FromArgb(38, 40, 50);
                using (SolidBrush brush = new SolidBrush(fill))
                {
                    pevent.Graphics.FillPath(brush, path);
                }
            }
            TextRenderer.DrawText(pevent.Graphics, Text, Font, ClientRectangle,
                Enabled ? ForeColor : Color.FromArgb(120, 124, 134),
                TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter);
        }

        internal static GraphicsPath Rounded(RectangleF box, float radius)
        {
            GraphicsPath path = new GraphicsPath();
            float d = radius * 2f;
            path.AddArc(box.X, box.Y, d, d, 180, 90);
            path.AddArc(box.Right - d, box.Y, d, d, 270, 90);
            path.AddArc(box.Right - d, box.Bottom - d, d, d, 0, 90);
            path.AddArc(box.X, box.Bottom - d, d, d, 90, 90);
            path.CloseFigure();
            return path;
        }
    }

    // Dark themed text box.
    internal class ModernBox : TextBox
    {
        public ModernBox()
        {
            BackColor = Color.FromArgb(20, 21, 28);
            ForeColor = Color.FromArgb(220, 224, 232);
            BorderStyle = BorderStyle.FixedSingle;
            Font = new Font("Segoe UI", 9.5F);
        }
    }

    // Dark themed combo.
    internal class ModernCombo : ComboBox
    {
        public ModernCombo()
        {
            DropDownStyle = ComboBoxStyle.DropDownList;
            BackColor = Color.FromArgb(20, 21, 28);
            ForeColor = Color.FromArgb(220, 224, 232);
            FlatStyle = FlatStyle.Flat;
            Font = new Font("Segoe UI", 9.5F);
        }
    }

    public class MainForm : Form
    {
        private readonly List<Candidate> candidates = new List<Candidate>();
        private ModernCombo cmbProcess;
        private ModernBox txtJar;
        private ModernBox txtDll;
        private Button btnRefresh;
        private Button btnBrowseJar;
        private Button btnBrowseDll;
        private Button btnInject;
        private RichTextBox logBox;
        private Label lblStatus;
        private bool working;
        private readonly object logLock = new object();

        public MainForm()
        {
            Text = "Myau Injector";
            ClientSize = new Size(560, 520);
            FormBorderStyle = FormBorderStyle.FixedSingle;
            MaximizeBox = false;
            StartPosition = FormStartPosition.CenterScreen;
            BackColor = Color.FromArgb(14, 14, 18);
            Font = new Font("Segoe UI", 9.5F);
            ForeColor = Color.FromArgb(210, 214, 224);

            // ---- brand header ----
            Label brand = new Label
            {
                Text = "MYAU  INJECTOR",
                Location = new Point(18, 14),
                AutoSize = true,
                ForeColor = Color.FromArgb(240, 242, 248),
                Font = new Font("Segoe UI", 15F, FontStyle.Bold)
            };
            lblStatus = new Label
            {
                Text = "idle",
                Location = new Point(18, 44),
                AutoSize = true,
                ForeColor = Color.FromArgb(140, 144, 156),
                Font = new Font("Segoe UI", 9F)
            };
            Label hint = new Label
            {
                Text = "injects the Myau client jar into a running Minecraft (Java) process",
                Location = new Point(18, 62),
                AutoSize = true,
                ForeColor = Color.FromArgb(120, 124, 134),
                Font = new Font("Segoe UI", 8.5F)
            };

            // ---- process row ----
            Label lblProcess = new Label { Text = "Minecraft process", Location = new Point(18, 96), AutoSize = true, ForeColor = Color.FromArgb(180, 184, 194) };
            cmbProcess = new ModernCombo { Location = new Point(18, 116), Width = 430 };
            btnRefresh = new ModernButton { Text = "Refresh", Location = new Point(456, 115), Size = new Size(86, 30) };
            btnRefresh.Click += (s, e) => RefreshProcesses();

            // ---- jar row ----
            Label lblJar = new Label { Text = "Client jar (embedded into the game classloader)", Location = new Point(18, 158), AutoSize = true, ForeColor = Color.FromArgb(180, 184, 194) };
            txtJar = new ModernBox { Location = new Point(18, 178), Width = 430 };
            btnBrowseJar = new ModernButton { Text = "Browse", Location = new Point(456, 177), Size = new Size(86, 30) };
            btnBrowseJar.Click += (s, e) => Browse(txtJar, "JAR files (*.jar)|*.jar|All files (*.*)|*.*");

            // ---- dll row ----
            Label lblDll = new Label { Text = "myau_native.dll (JVMTI agent)", Location = new Point(18, 220), AutoSize = true, ForeColor = Color.FromArgb(180, 184, 194) };
            txtDll = new ModernBox { Location = new Point(18, 240), Width = 430 };
            btnBrowseDll = new ModernButton { Text = "Browse", Location = new Point(456, 239), Size = new Size(86, 30) };
            btnBrowseDll.Click += (s, e) => Browse(txtDll, "DLL files (*.dll)|*.dll|All files (*.*)|*.*");

            // ---- inject button ----
            btnInject = new ModernButton
            {
                Text = "INJECT",
                Location = new Point(18, 286),
                Size = new Size(140, 42),
                Font = new Font("Segoe UI", 11F, FontStyle.Bold),
                BackColor = Color.FromArgb(46, 160, 67),
                ForeColor = Color.White
            };
            btnInject.Click += (s, e) => BeginInject();

            // ---- log ----
            logBox = new RichTextBox
            {
                Location = new Point(18, 344),
                Size = new Size(524, 158),
                ReadOnly = true,
                BackColor = Color.FromArgb(8, 8, 12),
                ForeColor = Color.FromArgb(190, 194, 204),
                BorderStyle = BorderStyle.FixedSingle,
                Font = new Font("Consolas", 9F)
            };

            Controls.AddRange(new Control[] { brand, lblStatus, hint, lblProcess, cmbProcess, btnRefresh,
                lblJar, txtJar, btnBrowseJar, lblDll, txtDll, btnBrowseDll, btnInject, logBox });

            string baseDir = AppDomain.CurrentDomain.BaseDirectory;
            string defaultJar = Path.Combine(baseDir, "Myau-1.0.0.jar");
            if (!File.Exists(defaultJar))
            {
                string[] jars = Directory.GetFiles(baseDir, "*.jar");
                defaultJar = jars.Length > 0 ? jars[0] : "";
            }
            txtJar.Text = defaultJar;

            string defaultDll = Path.Combine(baseDir, "myau_native.dll");
            txtDll.Text = File.Exists(defaultDll) ? defaultDll : "";

            RefreshProcesses();
        }

        private void Browse(TextBox target, string filter)
        {
            using (OpenFileDialog dlg = new OpenFileDialog())
            {
                dlg.Filter = filter;
                dlg.Title = "Select file";
                if (dlg.ShowDialog(this) == DialogResult.OK) target.Text = dlg.FileName;
            }
        }

        private void SetStatus(string text, Color colour)
        {
            if (InvokeRequired) { BeginInvoke((Action)(() => SetStatus(text, colour))); return; }
            lblStatus.Text = text;
            lblStatus.ForeColor = colour;
        }

        private void Log(string message)
        {
            if (InvokeRequired) { BeginInvoke((Action)(() => Log(message))); return; }
            lock (logLock)
            {
                logBox.AppendText("[" + DateTime.Now.ToString("HH:mm:ss") + "] " + message + "\n");
                logBox.ScrollToCaret();
            }
        }

        private void LogGood(string message) { Log(message); }
        private void LogBad(string message) { Log("!! " + message); }

        // ---------------------------------------------------------------- process detection
        private void RefreshProcesses()
        {
            List<Candidate> found;
            try { found = FindCandidates(); }
            catch (Exception ex) { LogBad("process scan failed: " + ex.Message); return; }

            cmbProcess.Items.Clear();
            candidates.Clear();
            found.Sort(delegate (Candidate a, Candidate b) { return b.Score.CompareTo(a.Score); });
            foreach (Candidate c in found)
            {
                if (c.Score <= 0) continue;
                string title = ShortLabel(c);
                cmbProcess.Items.Add(title);
                candidates.Add(c);
            }
            if (cmbProcess.Items.Count > 0) cmbProcess.SelectedIndex = 0;
            SetStatus(cmbProcess.Items.Count + " candidate(s) found", Color.FromArgb(140, 144, 156));
            if (cmbProcess.Items.Count == 0)
            {
                Log("no Minecraft process detected - start the game, then press Refresh");
            }
            else
            {
                Log("found " + cmbProcess.Items.Count + " candidate(s)");
            }
        }

        private string ShortLabel(Candidate c)
        {
            string name = c.Image;
            string line = c.CommandLine;
            if (line.IndexOf("lunarclient", StringComparison.OrdinalIgnoreCase) >= 0) name = "Lunar";
            else if (line.IndexOf("badlion", StringComparison.OrdinalIgnoreCase) >= 0) name = "Badlion";
            else if (line.IndexOf("net.minecraft.launchwrapper.launch", StringComparison.OrdinalIgnoreCase) >= 0
                     || line.IndexOf("forge", StringComparison.OrdinalIgnoreCase) >= 0) name = "Forge";
            else if (line.IndexOf("net.minecraft.client.main.main", StringComparison.OrdinalIgnoreCase) >= 0) name = "Vanilla";
            return "PID " + c.Pid + "   " + name + "   (score " + c.Score + ")";
        }

        private List<Candidate> FindCandidates()
        {
            List<Candidate> found = new List<Candidate>();
            foreach (Process p in Process.GetProcesses())
            {
                string image = p.ProcessName.ToLowerInvariant();
                if (image != "java" && image != "javaw") continue;
                Candidate c = new Candidate { Pid = p.Id, Image = p.ProcessName };
                try { c.CommandLine = CommandLineOf(p.Id); }
                catch { c.CommandLine = ""; }
                c.Score = ScoreCommandLine(c.CommandLine);
                found.Add(c);
            }
            return found;
        }

        private static int ScoreCommandLine(string raw)
        {
            string cmd = raw.ToLowerInvariant();
            if (cmd.Length == 0) return 0;
            bool isGame = cmd.Contains("net.minecraft.client.main.main")
                || cmd.Contains("net.minecraft.launchwrapper.launch");
            if (!isGame && (cmd.Contains("hmcl") || cmd.Contains("multimc")
                || cmd.Contains("prismlauncher") || cmd.Contains("atlauncher")
                || cmd.Contains("gdlauncher") || cmd.Contains("org.gradle")
                || cmd.Contains("gradle-launcher") || cmd.Contains("myau")
                || cmd.Contains("ovson"))) return -100;
            int score = 0;
            if (isGame) score += 50;
            if (cmd.Contains("--gamedir")) score += 10;
            if (cmd.Contains("--assetindex") || cmd.Contains("--assetsdir")) score += 10;
            if (cmd.Contains("--accesstoken")) score += 10;
            if (cmd.Contains("--uuid")) score += 5;
            if (cmd.Contains("--versiontype")) score += 5;
            if (cmd.Contains("java.library.path") && cmd.Contains("natives")) score += 10;
            if (cmd.Contains("lunarclient") || cmd.Contains(".lunarclient")) score += 30;
            if (cmd.Contains("badlion")) score += 30;
            return score;
        }

        // ---- command line via PEB (NtQueryInformationProcess) ----
        private delegate int NtQueryInformationProcessDelegate(IntPtr process, int infoClass,
            IntPtr info, int length, out int returned);

        private static string CommandLineOf(int pid)
        {
            IntPtr process = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ, false, pid);
            if (process == IntPtr.Zero) return "";
            try
            {
                IntPtr ntdll = GetModuleHandle("ntdll.dll");
                IntPtr fn = GetProcAddress(ntdll, "NtQueryInformationProcess");
                NtQueryInformationProcessDelegate query = (NtQueryInformationProcessDelegate)
                    Marshal.GetDelegateForFunctionPointer(fn, typeof(NtQueryInformationProcessDelegate));

                PROCESS_BASIC_INFORMATION basic = new PROCESS_BASIC_INFORMATION();
                int returned;
                if (query(process, 0, Marshal.AllocHGlobal(Marshal.SizeOf(basic)), Marshal.SizeOf(basic), out returned) != 0)
                {
                    return "";
                }
                IntPtr pebPtr = Marshal.AllocHGlobal(Marshal.SizeOf(basic));
                query(process, 0, pebPtr, Marshal.SizeOf(basic), out returned);
                basic = (PROCESS_BASIC_INFORMATION)Marshal.PtrToStructure(pebPtr, typeof(PROCESS_BASIC_INFORMATION));
                Marshal.FreeHGlobal(pebPtr);

                if (basic.PebBaseAddress == IntPtr.Zero) return "";
                PEB peb = ReadStruct<PEB>(process, basic.PebBaseAddress);
                if (peb.ProcessParameters == IntPtr.Zero) return "";
                RTL_USER_PROCESS_PARAMETERS pars = ReadStruct<RTL_USER_PROCESS_PARAMETERS>(process, peb.ProcessParameters);
                if (pars.CommandLine.Length == 0 || pars.CommandLine.Buffer == IntPtr.Zero) return "";
                int bytes = pars.CommandLine.Length;
                if (bytes > 64 * 1024) bytes = 64 * 1024;
                byte[] buffer = new byte[bytes];
                IntPtr read;
                ReadProcessMemory(process, pars.CommandLine.Buffer, buffer, (uint)bytes, out read);
                return Encoding.Unicode.GetString(buffer, 0, bytes);
            }
            finally
            {
                CloseHandle(process);
            }
        }

        private static T ReadStruct<T>(IntPtr process, IntPtr address) where T : struct
        {
            IntPtr buffer = Marshal.AllocHGlobal(Marshal.SizeOf(typeof(T)));
            IntPtr read;
            ReadProcessMemory(process, address, buffer, (uint)Marshal.SizeOf(typeof(T)), out read);
            T value = (T)Marshal.PtrToStructure(buffer, typeof(T));
            Marshal.FreeHGlobal(buffer);
            return value;
        }

        // ---------------------------------------------------------------- injection
        private void BeginInject()
        {
            if (working) return;
            if (cmbProcess.SelectedIndex < 0 || cmbProcess.SelectedIndex >= candidates.Count)
            {
                LogBad("select a Minecraft process first");
                return;
            }
            int pid = candidates[cmbProcess.SelectedIndex].Pid;
            string dllPath = txtDll.Text.Trim();
            string jarPath = txtJar.Text.Trim();
            if (!File.Exists(dllPath)) { LogBad("myau_native.dll not found: " + dllPath); return; }
            if (jarPath.Length > 0 && !File.Exists(jarPath)) { LogBad("jar not found: " + jarPath); return; }

            working = true;
            btnInject.Enabled = false;
            btnInject.Text = "INJECTING...";
            Thread worker = new Thread(() => InjectWorker(pid, dllPath, jarPath));
            worker.IsBackground = true;
            worker.Start();
        }

        private void InjectWorker(int pid, string dllPath, string jarPath)
        {
            try
            {
                SetStatus("injecting into PID " + pid + " ...", Color.FromArgb(240, 200, 90));
                Log("target PID " + pid);

                IntPtr hProcess = OpenProcess(PROCESS_CREATE_THREAD | PROCESS_QUERY_INFORMATION
                    | PROCESS_VM_OPERATION | PROCESS_VM_WRITE | PROCESS_VM_READ, false, pid);
                if (hProcess == IntPtr.Zero)
                {
                    LogBad("OpenProcess failed (error " + Marshal.GetLastWin32Error()
                        + "). If the game runs elevated, run this injector as Administrator.");
                    return;
                }

                bool wow64;
                try
                {
                    if (IsWow64Process(hProcess, out wow64) && wow64)
                    {
                        LogBad("the target is 32-bit; myau_native.dll is x64 - they cannot mix");
                        return;
                    }
                }
                catch { /* IsWow64Process unavailable - skip the check */ }

                try
                {
                    // stage dll + jar next to each other in %TEMP%
                    string tempDir = Path.GetTempPath();
                    string stagedDll = Path.Combine(tempDir, "myau_native_" + pid + ".dll");
                    string stagedJar = Path.Combine(tempDir, "myau_client_" + pid + ".jar");

                    File.Copy(dllPath, stagedDll, true);
                    Log("staged dll: " + stagedDll + " (" + new FileInfo(stagedDll).Length + " bytes)");

                    // the dll finds the jar via its sidecar (.dll.txt) if we supply one
                    if (jarPath.Length > 0)
                    {
                        File.Copy(jarPath, stagedJar, true);
                        File.WriteAllText(stagedDll + ".txt", stagedJar, new UTF8Encoding(false));
                        Log("staged jar: " + stagedJar + " (" + new FileInfo(stagedJar).Length + " bytes)");
                    }
                    else
                    {
                        Log("no jar selected - the dll will use its embedded copy");
                    }

                    // remote thread: LoadLibraryW(stagedDll)
                    IntPtr kernel32 = GetModuleHandle("kernel32.dll");
                    IntPtr loadLib = GetProcAddress(kernel32, "LoadLibraryW");
                    if (loadLib == IntPtr.Zero) { LogBad("LoadLibraryW not found"); return; }

                    byte[] pathBytes = Encoding.Unicode.GetBytes(stagedDll + "\0");
                    IntPtr remote = VirtualAllocEx(hProcess, IntPtr.Zero, (uint)pathBytes.Length,
                        MEM_COMMIT | MEM_RESERVE, PAGE_READWRITE);
                    if (remote == IntPtr.Zero) { LogBad("VirtualAllocEx failed (error " + Marshal.GetLastWin32Error() + ")"); return; }

                    IntPtr written;
                    if (!WriteProcessMemory(hProcess, remote, pathBytes, (uint)pathBytes.Length, out written))
                    {
                        LogBad("WriteProcessMemory failed (error " + Marshal.GetLastWin32Error() + ")");
                        VirtualFreeEx(hProcess, remote, 0, MEM_RELEASE);
                        return;
                    }

                    uint threadId;
                    IntPtr thread = CreateRemoteThread(hProcess, IntPtr.Zero, 0, loadLib, remote, 0, out threadId);
                    if (thread == IntPtr.Zero)
                    {
                        LogBad("CreateRemoteThread failed (error " + Marshal.GetLastWin32Error() + ")");
                        VirtualFreeEx(hProcess, remote, 0, MEM_RELEASE);
                        return;
                    }

                    Log("remote thread started, waiting for LoadLibraryW...");
                    WaitForSingleObject(thread, 15000);
                    IntPtr module;
                    GetExitCodeThread(thread, out module);
                    CloseHandle(thread);
                    VirtualFreeEx(hProcess, remote, 0, MEM_RELEASE);

                    if (module == IntPtr.Zero)
                    {
                        LogBad("LoadLibraryW returned null - the dll did not load (architecture mismatch?)");
                        return;
                    }

                    LogGood("injected -> module 0x" + module.ToInt64().ToString("x"));
                    LogGood("client jar staged for the game classloader. Details: %TEMP%\\myau-native.log");
                    SetStatus("injected into PID " + pid, Color.FromArgb(70, 190, 90));
                }
                finally
                {
                    CloseHandle(hProcess);
                }
            }
            catch (Exception ex)
            {
                LogBad("injection failed: " + ex.Message);
            }
            finally
            {
                BeginInvoke((Action)(() =>
                {
                    working = false;
                    btnInject.Enabled = true;
                    btnInject.Text = "INJECT";
                }));
            }
        }

        // ---------------------------------------------------------------- win32
        private const uint PROCESS_CREATE_THREAD = 0x0002;
        private const uint PROCESS_QUERY_INFORMATION = 0x0400;
        private const uint PROCESS_VM_OPERATION = 0x0008;
        private const uint PROCESS_VM_WRITE = 0x0020;
        private const uint PROCESS_VM_READ = 0x0010;
        private const uint MEM_COMMIT = 0x1000;
        private const uint MEM_RESERVE = 0x2000;
        private const uint MEM_RELEASE = 0x8000;
        private const uint PAGE_READWRITE = 0x04;

        [StructLayout(LayoutKind.Sequential)]
        private struct PROCESS_BASIC_INFORMATION
        {
            public IntPtr ExitStatus;
            public IntPtr PebBaseAddress;
            public IntPtr AffinityMask;
            public IntPtr BasePriority;
            public IntPtr UniqueProcessId;
            public IntPtr InheritedFromUniqueProcessId;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct PEB
        {
            [MarshalAs(UnmanagedType.ByValArray, SizeConst = 2)] public byte[] Reserved1;
            public byte BeingDebugged;
            public byte Reserved2;
            public IntPtr Reserved3;
            public IntPtr Ldr;
            public IntPtr ProcessParameters;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct UNICODE_STRING
        {
            public ushort Length;
            public ushort MaximumLength;
            public IntPtr Buffer;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct RTL_USER_PROCESS_PARAMETERS
        {
            [MarshalAs(UnmanagedType.ByValArray, SizeConst = 16)] public byte[] Reserved1;
            [MarshalAs(UnmanagedType.ByValArray, SizeConst = 10)] public IntPtr[] Reserved2;
            public UNICODE_STRING ImagePathName;
            public UNICODE_STRING CommandLine;
        }

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern IntPtr OpenProcess(uint access, bool inherit, int pid);
        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool WriteProcessMemory(IntPtr process, IntPtr address, byte[] buffer, uint size, out IntPtr written);
        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool ReadProcessMemory(IntPtr process, IntPtr address, IntPtr buffer, uint size, out IntPtr read);
        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool ReadProcessMemory(IntPtr process, IntPtr address, byte[] buffer, uint size, out IntPtr read);
        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern IntPtr VirtualAllocEx(IntPtr process, IntPtr address, uint size, uint allocType, uint protect);
        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool VirtualFreeEx(IntPtr process, IntPtr address, uint size, uint freeType);
        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern IntPtr CreateRemoteThread(IntPtr process, IntPtr threadAttributes, uint stackSize, IntPtr startAddress, IntPtr parameter, uint creationFlags, out uint threadId);
        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern uint WaitForSingleObject(IntPtr handle, uint milliseconds);
        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool GetExitCodeThread(IntPtr thread, out IntPtr exitCode);
        [DllImport("kernel32.dll")]
        private static extern IntPtr GetModuleHandle(string name);
        [DllImport("kernel32.dll")]
        private static extern IntPtr GetProcAddress(IntPtr module, string procName);
        [DllImport("kernel32.dll")]
        private static extern bool CloseHandle(IntPtr handle);
        [DllImport("kernel32.dll")]
        private static extern bool IsWow64Process(IntPtr process, out bool wow64);
    }

    internal static class Program
    {
        [STAThread]
        private static void Main()
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new MainForm());
        }
    }
}
