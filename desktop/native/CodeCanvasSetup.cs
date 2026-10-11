using System;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Threading;
using System.Windows.Forms;
using Microsoft.Win32;

namespace CodeCanvasSetup
{
    internal static class SetupProgram
    {
        public const string AppVersion = "0.5.0";
        public const string AppName = "代码画布";
        private const string ResourceName = "CodeCanvasPayload.zip";

        [DllImport("user32.dll")]
        private static extern bool SetProcessDPIAware();

        [STAThread]
        private static int Main(string[] args)
        {
            try { SetProcessDPIAware(); } catch { }
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            bool silent = false;
            bool relaunch = false;
            bool uninstall = false;

            foreach (string a in args)
            {
                if (string.Equals(a, "--silent", StringComparison.OrdinalIgnoreCase) || string.Equals(a, "/S", StringComparison.OrdinalIgnoreCase))
                    silent = true;
                else if (string.Equals(a, "--relaunch", StringComparison.OrdinalIgnoreCase))
                    relaunch = true;
                else if (string.Equals(a, "--uninstall", StringComparison.OrdinalIgnoreCase))
                    uninstall = true;
            }

            string installDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Programs", "CodeCanvas");

            if (uninstall)
            {
                PerformUninstall(installDir, silent);
                return 0;
            }

            if (silent)
            {
                try
                {
                    PerformInstall(installDir, true, true, relaunch);
                    return 0;
                }
                catch
                {
                    return 1;
                }
            }

            using (SetupForm form = new SetupForm(installDir))
            {
                Application.Run(form);
            }
            return 0;
        }

        public static void PerformInstall(string installDir, bool createDesktopShortcut, bool createStartMenuShortcut, bool launchAfter)
        {
            StopRunningInstances();
            Directory.CreateDirectory(installDir);

            using (Stream resStream = Assembly.GetExecutingAssembly().GetManifestResourceStream(ResourceName))
            {
                if (resStream == null)
                {
                    throw new InvalidOperationException("安装包内置资源缺失 (" + ResourceName + ")");
                }
                using (ZipArchive archive = new ZipArchive(resStream, ZipArchiveMode.Read))
                {
                    foreach (ZipArchiveEntry entry in archive.Entries)
                    {
                        string fullPath = Path.Combine(installDir, entry.FullName.Replace('/', Path.DirectorySeparatorChar));
                        if (string.IsNullOrEmpty(entry.Name))
                        {
                            Directory.CreateDirectory(fullPath);
                            continue;
                        }
                        string parent = Path.GetDirectoryName(fullPath);
                        if (!string.IsNullOrEmpty(parent))
                        {
                            Directory.CreateDirectory(parent);
                        }
                        entry.ExtractToFile(fullPath, true);
                    }
                }
            }

            // Copy setup executable as uninstaller helper
            string uninstallerExe = Path.Combine(installDir, "Uninstall-CodeCanvas.exe");
            try
            {
                File.Copy(Application.ExecutablePath, uninstallerExe, true);
            }
            catch { }

            string mainExe = Path.Combine(installDir, "代码画布.exe");
            string iconPath = Path.Combine(installDir, "assets", "icon.ico");
            if (!File.Exists(iconPath)) iconPath = mainExe;

            if (createDesktopShortcut)
            {
                string desktopDir = Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory);
                CreateShortcut(Path.Combine(desktopDir, "代码画布.lnk"), mainExe, installDir, iconPath, "代码画布 · 粘贴 AI 代码，立即看见作品");
            }

            if (createStartMenuShortcut)
            {
                string programsDir = Environment.GetFolderPath(Environment.SpecialFolder.Programs);
                CreateShortcut(Path.Combine(programsDir, "代码画布.lnk"), mainExe, installDir, iconPath, "代码画布 · 粘贴 AI 代码，立即看见作品");
            }

            RegisterUninstallEntry(installDir, mainExe, uninstallerExe);

            if (launchAfter && File.Exists(mainExe))
            {
                ProcessStartInfo psi = new ProcessStartInfo
                {
                    FileName = mainExe,
                    WorkingDirectory = installDir,
                    UseShellExecute = true
                };
                Process.Start(psi);
            }
        }

        private static void PerformUninstall(string installDir, bool silent)
        {
            StopRunningInstances();
            try
            {
                string desktopLnk = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory), "代码画布.lnk");
                if (File.Exists(desktopLnk)) File.Delete(desktopLnk);

                string startMenuLnk = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Programs), "代码画布.lnk");
                if (File.Exists(startMenuLnk)) File.Delete(startMenuLnk);

                using (RegistryKey runKey = Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\CurrentVersion\Run", true))
                {
                    if (runKey != null) runKey.DeleteValue("CodeCanvas", false);
                }

                Registry.CurrentUser.DeleteSubKeyTree(@"Software\Microsoft\Windows\CurrentVersion\Uninstall\CodeCanvas", false);
            }
            catch { }

            if (!silent)
            {
                MessageBox.Show("代码画布已成功移除快捷方式与注册信息。", "卸载完成", MessageBoxButtons.OK, MessageBoxIcon.Information);
            }
        }

        private static void StopRunningInstances()
        {
            try
            {
                foreach (Process p in Process.GetProcessesByName("代码画布"))
                {
                    try
                    {
                        p.Kill();
                        p.WaitForExit(2000);
                    }
                    catch { }
                }
                Thread.Sleep(150);
            }
            catch { }
        }

        private static void CreateShortcut(string shortcutPath, string targetPath, string workingDir, string iconLocation, string description)
        {
            Type shellType = Type.GetTypeFromProgID("WScript.Shell");
            if (shellType == null) return;
            object shell = Activator.CreateInstance(shellType);
            object shortcut = shellType.InvokeMember("CreateShortcut", BindingFlags.InvokeMethod, null, shell, new object[] { shortcutPath });
            Type scType = shortcut.GetType();
            scType.InvokeMember("TargetPath", BindingFlags.SetProperty, null, shortcut, new object[] { targetPath });
            scType.InvokeMember("WorkingDirectory", BindingFlags.SetProperty, null, shortcut, new object[] { workingDir });
            scType.InvokeMember("IconLocation", BindingFlags.SetProperty, null, shortcut, new object[] { iconLocation });
            scType.InvokeMember("Description", BindingFlags.SetProperty, null, shortcut, new object[] { description });
            scType.InvokeMember("Save", BindingFlags.InvokeMethod, null, shortcut, null);
        }

        private static void RegisterUninstallEntry(string installDir, string mainExe, string uninstallerExe)
        {
            try
            {
                using (RegistryKey key = Registry.CurrentUser.CreateSubKey(@"Software\Microsoft\Windows\CurrentVersion\Uninstall\CodeCanvas"))
                {
                    if (key == null) return;
                    key.SetValue("DisplayName", "代码画布 (Code Canvas)");
                    key.SetValue("DisplayVersion", AppVersion);
                    key.SetValue("Publisher", "Quan Zhu (zhuquan7237)");
                    key.SetValue("DisplayIcon", mainExe);
                    key.SetValue("InstallLocation", installDir);
                    key.SetValue("UninstallString", "\"" + uninstallerExe + "\" --uninstall");
                    key.SetValue("NoModify", 1, RegistryValueKind.DWord);
                    key.SetValue("NoRepair", 1, RegistryValueKind.DWord);
                }
            }
            catch { }
        }
    }

    internal sealed class SetupForm : Form
    {
        private readonly string _installDir;
        private readonly CheckBox _chkDesktop;
        private readonly CheckBox _chkStartMenu;
        private readonly CheckBox _chkLaunchNow;
        private readonly Button _btnInstall;
        private readonly Label _lblStatus;

        public SetupForm(string installDir)
        {
            _installDir = installDir;
            Text = "代码画布 v" + SetupProgram.AppVersion + " 安装向导";
            Width = 500;
            Height = 365;
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false;
            MinimizeBox = false;
            StartPosition = FormStartPosition.CenterScreen;
            BackColor = Color.FromArgb(13, 20, 32);
            ForeColor = Color.FromArgb(241, 245, 249);
            Font = new Font("Microsoft YaHei UI", 9.5f, FontStyle.Regular);

            try { Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath); } catch { }

            PictureBox iconBox = new PictureBox
            {
                Left = 28,
                Top = 26,
                Width = 56,
                Height = 56,
                SizeMode = PictureBoxSizeMode.StretchImage
            };
            if (Icon != null)
            {
                iconBox.Image = Icon.ToBitmap();
            }
            Controls.Add(iconBox);

            Label lblTitle = new Label
            {
                Left = 98,
                Top = 24,
                Width = 365,
                Height = 28,
                Text = "代码画布 (Code Canvas) v" + SetupProgram.AppVersion,
                Font = new Font("Microsoft YaHei UI", 14f, FontStyle.Bold),
                ForeColor = Color.FromArgb(248, 250, 252)
            };
            Controls.Add(lblTitle);

            Label lblSub = new Label
            {
                Left = 98,
                Top = 55,
                Width = 365,
                Height = 24,
                Text = "极轻量原生架构 · 毫秒级秒开 · 灵动浮窗 + Alt+V 唤醒",
                Font = new Font("Microsoft YaHei UI", 9f, FontStyle.Regular),
                ForeColor = Color.FromArgb(142, 180, 255)
            };
            Controls.Add(lblSub);

            Panel card = new Panel
            {
                Left = 28,
                Top = 98,
                Width = 428,
                Height = 148,
                BackColor = Color.FromArgb(21, 30, 44)
            };
            Controls.Add(card);

            _chkDesktop = new CheckBox
            {
                Left = 18,
                Top = 18,
                Width = 390,
                Height = 26,
                Checked = true,
                Text = "在桌面上创建【代码画布】快捷方式 (推荐)",
                ForeColor = Color.FromArgb(241, 245, 249)
            };
            card.Controls.Add(_chkDesktop);

            _chkStartMenu = new CheckBox
            {
                Left = 18,
                Top = 52,
                Width = 390,
                Height = 26,
                Checked = true,
                Text = "添加到 Windows 开始菜单程序列表",
                ForeColor = Color.FromArgb(241, 245, 249)
            };
            card.Controls.Add(_chkStartMenu);

            _chkLaunchNow = new CheckBox
            {
                Left = 18,
                Top = 86,
                Width = 390,
                Height = 26,
                Checked = true,
                Text = "安装完成后立即启动代码画布",
                ForeColor = Color.FromArgb(241, 245, 249)
            };
            card.Controls.Add(_chkLaunchNow);

            Label lblPath = new Label
            {
                Left = 18,
                Top = 118,
                Width = 390,
                Height = 20,
                Text = "安装位置: " + _installDir,
                Font = new Font("Microsoft YaHei UI", 8f),
                ForeColor = Color.FromArgb(148, 163, 184)
            };
            card.Controls.Add(lblPath);

            _lblStatus = new Label
            {
                Left = 28,
                Top = 264,
                Width = 280,
                Height = 36,
                Text = "点击右侧按钮即可在 1 秒内完成安装",
                ForeColor = Color.FromArgb(148, 163, 184)
            };
            Controls.Add(_lblStatus);

            _btnInstall = new Button
            {
                Left = 316,
                Top = 258,
                Width = 140,
                Height = 40,
                Text = "立即极速安装",
                FlatStyle = FlatStyle.Flat,
                BackColor = Color.FromArgb(37, 99, 235),
                ForeColor = Color.White,
                Font = new Font("Microsoft YaHei UI", 10f, FontStyle.Bold),
                Cursor = Cursors.Hand
            };
            _btnInstall.FlatAppearance.BorderSize = 0;
            _btnInstall.Click += OnInstallClick;
            Controls.Add(_btnInstall);
        }

        private void OnInstallClick(object sender, EventArgs e)
        {
            _btnInstall.Enabled = false;
            _lblStatus.Text = "正在写入原生程序并创建桌面快捷方式…";
            Application.DoEvents();

            try
            {
                SetupProgram.PerformInstall(_installDir, _chkDesktop.Checked, _chkStartMenu.Checked, _chkLaunchNow.Checked);
                _lblStatus.Text = "✓ 安装成功！桌面快捷方式已创建。";
                Close();
            }
            catch (Exception ex)
            {
                _btnInstall.Enabled = true;
                MessageBox.Show("安装过程中出现错误: " + ex.Message, "安装失败", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }
    }
}
