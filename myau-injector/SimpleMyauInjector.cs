// SimpleMyauInjector - a minimal GUI injector for the myau JVMTI agent.
// Injection technique: LoadLibraryW via CreateRemoteThread (the same payload
// myau_native.dll expects - it finds the running JVM itself in DllMain).
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Windows.Forms;

namespace MyauInjector
{
    public class MainForm : Form
    {
        private Label lblProcess;
        private ComboBox cmbProcess;
        private Button btnRefresh;
        private Label lblDll;
        private TextBox txtDll;
        private Button btnBrowse;
        private Button btnInject;
        private RichTextBox logBox;
        private readonly Dictionary<string, int> processMap = new Dictionary<string, int>();

        public MainForm()
        {
            Text = "Myau Injector";
            ClientSize = new Size(520, 330);
            FormBorderStyle = FormBorderStyle.FixedSingle;
            MaximizeBox = false;
            StartPosition = FormStartPosition.CenterScreen;
            Font = new Font("Segoe UI", 9F);

            lblProcess = new Label { Text = "Java process:", Location = new Point(12, 18), AutoSize = true };
            cmbProcess = new ComboBox { Location = new Point(120, 15), Width = 240, DropDownStyle = ComboBoxStyle.DropDownList };
            btnRefresh = new Button { Text = "Refresh", Location = new Point(368, 14), Size = new Size(70, 25) };
            btnRefresh.Click += (s, e) => RefreshProcesses();

            lblDll = new Label { Text = "myau_native.dll:", Location = new Point(12, 58), AutoSize = true };
            txtDll = new TextBox { Location = new Point(120, 55), Width = 240 };
            btnBrowse = new Button { Text = "Browse...", Location = new Point(368, 54), Size = new Size(70, 25) };
            btnBrowse.Click += (s, e) => BrowseDll();

            btnInject = new Button { Text = "Inject", Location = new Point(12, 95), Size = new Size(100, 32), BackColor = Color.FromArgb(45, 45, 45), ForeColor = Color.White };
            btnInject.Click += (s, e) => Inject();

            logBox = new RichTextBox { Location = new Point(12, 140), Size = new Size(496, 175), ReadOnly = true, BackColor = Color.Black, ForeColor = Color.LightGray, BorderStyle = BorderStyle.FixedSingle };

            Controls.AddRange(new Control[] { lblProcess, cmbProcess, btnRefresh, lblDll, txtDll, btnBrowse, btnInject, logBox });
            RefreshProcesses();
            string defaultDll = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "myau_native.dll");
            if (File.Exists(defaultDll)) txtDll.Text = defaultDll;
            else txtDll.Text = "";
        }

        private void Log(string message)
        {
            if (logBox.TextLength > 0) logBox.AppendText("\r\n");
            logBox.AppendText("[" + DateTime.Now.ToString("HH:mm:ss") + "] " + message);
            logBox.ScrollToCaret();
        }

        private void RefreshProcesses()
        {
            cmbProcess.Items.Clear();
            processMap.Clear();
            foreach (Process p in Process.GetProcesses())
            {
                string name = p.ProcessName.ToLowerInvariant();
                if (name != "java" && name != "javaw" && name != "javaws") continue;
                string title = string.IsNullOrEmpty(p.MainWindowTitle) ? "(no window)" : p.MainWindowTitle;
                string label = "PID " + p.Id + "  " + p.ProcessName + "  " + title;
                cmbProcess.Items.Add(label);
                processMap[label] = p.Id;
            }
            if (cmbProcess.Items.Count > 0) cmbProcess.SelectedIndex = 0;
            Log("Found " + cmbProcess.Items.Count + " Java process(es).");
        }

        private void BrowseDll()
        {
            using (OpenFileDialog dlg = new OpenFileDialog())
            {
                dlg.Filter = "DLL files (*.dll)|*.dll|All files (*.*)|*.*";
                dlg.Title = "Select myau_native.dll";
                if (dlg.ShowDialog(this) == DialogResult.OK) txtDll.Text = dlg.FileName;
            }
        }

        private void Inject()
        {
            if (cmbProcess.SelectedItem == null) { Log("Select a process first."); return; }
            string dllPath = txtDll.Text.Trim();
            if (!File.Exists(dllPath)) { Log("DLL not found: " + dllPath); return; }
            int pid = processMap[cmbProcess.SelectedItem.ToString()];

            IntPtr hProcess = OpenProcess(PROCESS_ALL_ACCESS, false, pid);
            if (hProcess == IntPtr.Zero) { Log("OpenProcess failed (pid " + pid + "), error " + Marshal.GetLastWin32Error()); return; }

            try
            {
                IntPtr kernel32 = GetModuleHandle("kernel32.dll");
                IntPtr loadLib = GetProcAddress(kernel32, "LoadLibraryW");
                if (loadLib == IntPtr.Zero) { Log("LoadLibraryW not found."); return; }

                byte[] pathBytes = Encoding.Unicode.GetBytes(dllPath + "\0");
                IntPtr remote = VirtualAllocEx(hProcess, IntPtr.Zero, (uint)pathBytes.Length, MEM_COMMIT | MEM_RESERVE, PAGE_READWRITE);
                if (remote == IntPtr.Zero) { Log("VirtualAllocEx failed, error " + Marshal.GetLastWin32Error()); return; }

                IntPtr writtenBytes;
                bool written = WriteProcessMemory(hProcess, remote, pathBytes, (uint)pathBytes.Length, out writtenBytes);
                if (!written) { Log("WriteProcessMemory failed, error " + Marshal.GetLastWin32Error()); VirtualFreeEx(hProcess, remote, 0, MEM_RELEASE); return; }

                uint threadId;
                IntPtr thread = CreateRemoteThread(hProcess, IntPtr.Zero, 0, loadLib, remote, 0, out threadId);
                if (thread == IntPtr.Zero) { Log("CreateRemoteThread failed, error " + Marshal.GetLastWin32Error()); VirtualFreeEx(hProcess, remote, 0, MEM_RELEASE); return; }

                Log("Injected '" + Path.GetFileName(dllPath) + "' into PID " + pid + ".");
                WaitForSingleObject(thread, 10000);
                VirtualFreeEx(hProcess, remote, 0, MEM_RELEASE);
                CloseHandle(thread);
            }
            finally
            {
                CloseHandle(hProcess);
            }
        }

        // --- Win32 ---
        private const uint PROCESS_ALL_ACCESS = 0x1F0FFF;
        private const uint MEM_COMMIT = 0x1000;
        private const uint MEM_RESERVE = 0x2000;
        private const uint MEM_RELEASE = 0x8000;
        private const uint PAGE_READWRITE = 0x04;

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern IntPtr OpenProcess(uint access, bool inherit, int pid);
        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool WriteProcessMemory(IntPtr process, IntPtr address, byte[] buffer, uint size, out IntPtr written);
        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern IntPtr VirtualAllocEx(IntPtr process, IntPtr address, uint size, uint allocType, uint protect);
        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool VirtualFreeEx(IntPtr process, IntPtr address, uint size, uint freeType);
        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern IntPtr CreateRemoteThread(IntPtr process, IntPtr threadAttributes, uint stackSize, IntPtr startAddress, IntPtr parameter, uint creationFlags, out uint threadId);
        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern uint WaitForSingleObject(IntPtr handle, uint milliseconds);
        [DllImport("kernel32.dll")]
        private static extern IntPtr GetModuleHandle(string name);
        [DllImport("kernel32.dll")]
        private static extern IntPtr GetProcAddress(IntPtr module, string procName);
        [DllImport("kernel32.dll")]
        private static extern bool CloseHandle(IntPtr handle);
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
