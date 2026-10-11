using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.IO;
using System.Net;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;
using System.Web.Script.Serialization;
using System.Windows.Forms;
using Microsoft.Win32;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;

namespace CodeCanvasNative
{
    internal static class Program
    {
        public const string CurrentVersion = "0.5.0";
        public const int CurrentVersionCode = 50;
        private const string ShowEventName = @"Local\CodeCanvasDesktopShowEvent_v050";

        [DllImport("user32.dll")]
        private static extern bool SetProcessDPIAware();

        [STAThread]
        private static void Main(string[] args)
        {
            try { SetProcessDPIAware(); } catch { }
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            ServicePointManager.SecurityProtocol = SecurityProtocolType.Tls12;

            bool startInTray = false;
            foreach (string a in args)
            {
                if (string.Equals(a, "--tray", StringComparison.OrdinalIgnoreCase))
                {
                    startInTray = true;
                }
            }

            bool createdNew;
            using (EventWaitHandle showEvent = new EventWaitHandle(false, EventResetMode.AutoReset, ShowEventName, out createdNew))
            {
                if (!createdNew)
                {
                    try { showEvent.Set(); } catch { }
                    return;
                }

                MainForm form = new MainForm(startInTray, showEvent);
                Application.Run(form);
            }
        }
    }

    internal sealed class ClipboardToastForm : Form
    {
        private readonly Icon _appIcon;
        private readonly Action _onActivateClick;
        private readonly System.Windows.Forms.Timer _autoHideTimer;
        private string _badgeText = "HTML";
        private string _summaryText = "识别到 AI 代码 · 点击即刻预览 (Alt+V)";
        private Rectangle _closeBtnRect = new Rectangle(332, 12, 22, 22);
        private bool _hoverClose;

        public ClipboardToastForm(Icon appIcon, Action onActivateClick)
        {
            _appIcon = appIcon;
            _onActivateClick = onActivateClick;
            FormBorderStyle = FormBorderStyle.None;
            ShowInTaskbar = false;
            StartPosition = FormStartPosition.Manual;
            TopMost = true;
            Width = 368;
            Height = 72;
            BackColor = Color.FromArgb(19, 28, 43);
            DoubleBuffered = true;
            Cursor = Cursors.Hand;

            Region = CreateRoundedRegion(Width, Height, 16);

            _autoHideTimer = new System.Windows.Forms.Timer { Interval = 4500 };
            _autoHideTimer.Tick += (s, e) =>
            {
                _autoHideTimer.Stop();
                Hide();
            };

            MouseMove += OnToastMouseMove;
            MouseLeave += (s, e) => { _hoverClose = false; Invalidate(); };
            MouseClick += OnToastMouseClick;
        }

        protected override bool ShowWithoutActivation
        {
            get { return true; }
        }

        protected override CreateParams CreateParams
        {
            get
            {
                CreateParams cp = base.CreateParams;
                const int WS_EX_TOPMOST = 0x00000008;
                const int WS_EX_TOOLWINDOW = 0x00000080;
                const int WS_EX_NOACTIVATE = 0x08000000;
                cp.ExStyle |= WS_EX_TOPMOST | WS_EX_TOOLWINDOW | WS_EX_NOACTIVATE;
                return cp;
            }
        }

        public void ShowToast(string kind, string summary)
        {
            _badgeText = string.IsNullOrEmpty(kind) ? "CODE" : kind;
            _summaryText = string.IsNullOrEmpty(summary) ? "识别到 AI 代码 · 点击即刻预览" : summary;

            Rectangle workArea = Screen.PrimaryScreen.WorkingArea;
            Location = new Point(workArea.Right - Width - 18, workArea.Bottom - Height - 18);

            _autoHideTimer.Stop();
            if (!Visible)
            {
                Show();
            }
            Invalidate();
            _autoHideTimer.Start();
        }

        private void OnToastMouseMove(object sender, MouseEventArgs e)
        {
            bool over = _closeBtnRect.Contains(e.Location);
            if (over != _hoverClose)
            {
                _hoverClose = over;
                Invalidate();
            }
        }

        private void OnToastMouseClick(object sender, MouseEventArgs e)
        {
            _autoHideTimer.Stop();
            Hide();
            if (_closeBtnRect.Contains(e.Location))
            {
                return;
            }
            if (_onActivateClick != null)
            {
                _onActivateClick();
            }
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            base.OnPaint(e);
            Graphics g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.TextRenderingHint = System.Drawing.Text.TextRenderingHint.ClearTypeGridFit;

            using (LinearGradientBrush bg = new LinearGradientBrush(ClientRectangle, Color.FromArgb(21, 30, 46), Color.FromArgb(13, 20, 32), 135f))
            {
                g.FillRectangle(bg, ClientRectangle);
            }

            using (Pen borderPen = new Pen(Color.FromArgb(56, 189, 248), 1.5f))
            {
                GraphicsPath path = CreateRoundedPath(new Rectangle(1, 1, Width - 3, Height - 3), 15);
                g.DrawPath(borderPen, path);
            }

            if (_appIcon != null)
            {
                try { g.DrawIcon(_appIcon, new Rectangle(14, 16, 40, 40)); } catch { }
            }

            using (Font titleFont = new Font("Microsoft YaHei UI", 9.5f, FontStyle.Bold))
            using (Font subFont = new Font("Microsoft YaHei UI", 8.25f, FontStyle.Regular))
            using (Brush primaryBrush = new SolidBrush(Color.FromArgb(241, 245, 249)))
            using (Brush accentBrush = new SolidBrush(Color.FromArgb(142, 180, 255)))
            using (Brush subBrush = new SolidBrush(Color.FromArgb(148, 163, 184)))
            {
                g.DrawString("✨ 灵动预览 · " + _badgeText + " 代码已就绪", titleFont, accentBrush, new PointF(64, 14));
                g.DrawString("点击此浮窗或按 Alt+V 立即打开画布预览", subFont, primaryBrush, new PointF(64, 38));
            }

            using (Brush closeBrush = new SolidBrush(_hoverClose ? Color.FromArgb(248, 113, 113) : Color.FromArgb(148, 163, 184)))
            using (Font closeFont = new Font("Segoe UI", 10f, FontStyle.Bold))
            {
                g.DrawString("×", closeFont, closeBrush, new PointF(_closeBtnRect.X + 4, _closeBtnRect.Y + 1));
            }
        }

        private static Region CreateRoundedRegion(int w, int h, int r)
        {
            GraphicsPath path = CreateRoundedPath(new Rectangle(0, 0, w, h), r);
            return new Region(path);
        }

        private static GraphicsPath CreateRoundedPath(Rectangle rect, int radius)
        {
            int d = radius * 2;
            GraphicsPath path = new GraphicsPath();
            path.AddArc(rect.X, rect.Y, d, d, 180, 90);
            path.AddArc(rect.Right - d, rect.Y, d, d, 270, 90);
            path.AddArc(rect.Right - d, rect.Bottom - d, d, d, 0, 90);
            path.AddArc(rect.X, rect.Bottom - d, d, d, 90, 90);
            path.CloseFigure();
            return path;
        }
    }

    internal sealed class MainForm : Form
    {
        private const int WM_CLIPBOARDUPDATE = 0x031D;
        private const int WM_HOTKEY = 0x0312;
        private const int HOTKEY_ID_ALT_V = 9001;
        private const int HOTKEY_ID_CTRL_SHIFT_V = 9002;
        private const uint MOD_ALT = 0x0001;
        private const uint MOD_CONTROL = 0x0002;
        private const uint MOD_SHIFT = 0x0004;
        private const uint VK_V = 0x56;

        [DllImport("user32.dll", SetLastError = true)]
        private static extern bool AddClipboardFormatListener(IntPtr hwnd);

        [DllImport("user32.dll", SetLastError = true)]
        private static extern bool RemoveClipboardFormatListener(IntPtr hwnd);

        [DllImport("user32.dll", SetLastError = true)]
        private static extern bool RegisterHotKey(IntPtr hWnd, int id, uint fsModifiers, uint vk);

        [DllImport("user32.dll", SetLastError = true)]
        private static extern bool UnregisterHotKey(IntPtr hWnd, int id);

        [DllImport("user32.dll")]
        private static extern IntPtr GetForegroundWindow();

        [DllImport("user32.dll")]
        private static extern bool SetForegroundWindow(IntPtr hWnd);

        [DllImport("dwmapi.dll")]
        private static extern int DwmSetWindowAttribute(IntPtr hwnd, int attr, ref int attrValue, int attrSize);

        private WebView2 _webView;
        private NotifyIcon _trayIcon;
        private ClipboardToastForm _toastForm;
        private Icon _customIcon;
        private readonly JavaScriptSerializer _json;
        private readonly string _appBaseDir;
        private readonly string _srcDir;
        private readonly string _dataDir;
        private readonly string _docsFile;
        private readonly string _prefsFile;
        private readonly EventWaitHandle _showEvent;

        private Dictionary<string, object> _prefs;
        private bool _allowExit;
        private bool _startInTray;
        private bool _webViewReady;
        private string _pendingClipTextToPreview;
        private string _lastClipboardSignature = "";
        private string _lastToastCandidateText = "";

        public MainForm(bool startInTray, EventWaitHandle showEvent)
        {
            _startInTray = startInTray;
            _showEvent = showEvent;
            _json = new JavaScriptSerializer { MaxJsonLength = 64 * 1024 * 1024 };

            _appBaseDir = AppDomain.CurrentDomain.BaseDirectory;
            _srcDir = ResolveSrcDirectory(_appBaseDir);
            _dataDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "CodeCanvas", "CodeCanvasWorkspace");
            Directory.CreateDirectory(_dataDir);
            _docsFile = Path.Combine(_dataDir, "documents.json");
            _prefsFile = Path.Combine(_dataDir, "preferences.json");

            _prefs = LoadPreferences();
            EnsureStarterDocuments();

            Text = "代码画布 · 桌面工作台";
            Width = 1320;
            Height = 840;
            MinimumSize = new Size(920, 600);
            StartPosition = FormStartPosition.CenterScreen;
            BackColor = Color.FromArgb(13, 20, 32);

            _customIcon = LoadCustomIcon(_appBaseDir);
            if (_customIcon != null)
            {
                Icon = _customIcon;
            }

            _trayIcon = new NotifyIcon
            {
                Icon = _customIcon ?? Icon ?? SystemIcons.Application,
                Text = "代码画布 v" + Program.CurrentVersion + " (Alt+V 极速预览)",
                Visible = true,
                ContextMenuStrip = BuildTrayMenu()
            };
            _trayIcon.DoubleClick += (s, e) => RestoreAndActivateWindow();
            _trayIcon.MouseClick += (s, e) =>
            {
                if (e.Button == MouseButtons.Left)
                {
                    RestoreAndActivateWindow();
                }
            };

            _webView = new WebView2
            {
                Dock = DockStyle.Fill,
                DefaultBackgroundColor = Color.FromArgb(13, 20, 32)
            };
            Controls.Add(_webView);

            ThreadPool.RegisterWaitForSingleObject(_showEvent, (state, timedOut) =>
            {
                if (!IsDisposed && IsHandleCreated)
                {
                    BeginInvoke(new Action(RestoreAndActivateWindow));
                }
            }, null, -1, false);

            Load += OnMainFormLoad;
            FormClosing += OnMainFormClosing;
        }

        private static string ResolveSrcDirectory(string baseDir)
        {
            string direct = Path.Combine(baseDir, "src");
            if (File.Exists(Path.Combine(direct, "index.html"))) return direct;
            string parentSrc = Path.GetFullPath(Path.Combine(baseDir, "..", "src"));
            if (File.Exists(Path.Combine(parentSrc, "index.html"))) return parentSrc;
            string grandParentSrc = Path.GetFullPath(Path.Combine(baseDir, "..", "..", "src"));
            if (File.Exists(Path.Combine(grandParentSrc, "index.html"))) return grandParentSrc;
            return direct;
        }

        private static Icon LoadCustomIcon(string baseDir)
        {
            string[] candidates = new[]
            {
                Path.Combine(baseDir, "assets", "icon.ico"),
                Path.Combine(baseDir, "icon.ico"),
                Path.GetFullPath(Path.Combine(baseDir, "..", "assets", "icon.ico"))
            };
            foreach (string p in candidates)
            {
                if (File.Exists(p))
                {
                    try { return new Icon(p); } catch { }
                }
            }
            try { return Icon.ExtractAssociatedIcon(Application.ExecutablePath); } catch { return null; }
        }

        private ContextMenuStrip BuildTrayMenu()
        {
            ContextMenuStrip menu = new ContextMenuStrip();
            ToolStripMenuItem itemPaste = new ToolStripMenuItem("⚡ 粘贴并极速预览 (Alt+V)");
            itemPaste.Font = new Font(menu.Font, FontStyle.Bold);
            itemPaste.Click += (s, e) => TriggerGlobalQuickPaste(null);

            ToolStripMenuItem itemOpen = new ToolStripMenuItem("🎨 打开代码画布主窗口");
            itemOpen.Click += (s, e) => RestoreAndActivateWindow();

            ToolStripMenuItem itemUpdate = new ToolStripMenuItem("🔄 同步检查更新");
            itemUpdate.Click += (s, e) =>
            {
                RestoreAndActivateWindow();
                ExecuteRendererScript("if(window.__canvasNativeCheckUpdate) window.__canvasNativeCheckUpdate();");
            };

            ToolStripMenuItem itemSettings = new ToolStripMenuItem("⚙️ 偏好与快速启动设置");
            itemSettings.Click += (s, e) =>
            {
                RestoreAndActivateWindow();
                ExecuteRendererScript("if(window.__canvasNativeOpenSettings) window.__canvasNativeOpenSettings();");
            };

            ToolStripMenuItem itemExit = new ToolStripMenuItem("❌ 彻底退出代码画布");
            itemExit.Click += (s, e) =>
            {
                _allowExit = true;
                _trayIcon.Visible = false;
                Application.Exit();
            };

            menu.Items.Add(itemPaste);
            menu.Items.Add(itemOpen);
            menu.Items.Add(new ToolStripSeparator());
            menu.Items.Add(itemUpdate);
            menu.Items.Add(itemSettings);
            menu.Items.Add(new ToolStripSeparator());
            menu.Items.Add(itemExit);
            return menu;
        }

        protected override void OnHandleCreated(EventArgs e)
        {
            base.OnHandleCreated(e);
            ApplyTitlebarTheme();
            try { AddClipboardFormatListener(Handle); } catch { }
            ApplyHotkeyRegistration();
        }

        protected override void OnHandleDestroyed(EventArgs e)
        {
            try { RemoveClipboardFormatListener(Handle); } catch { }
            try { UnregisterHotKey(Handle, HOTKEY_ID_ALT_V); } catch { }
            try { UnregisterHotKey(Handle, HOTKEY_ID_CTRL_SHIFT_V); } catch { }
            base.OnHandleDestroyed(e);
        }

        private void ApplyTitlebarTheme()
        {
            try
            {
                string theme = GetPrefString("theme", "system");
                bool isDark = theme == "dark" || (theme == "system" && IsSystemDarkTheme());
                int darkVal = isDark ? 1 : 0;
                DwmSetWindowAttribute(Handle, 20, ref darkVal, sizeof(int));
            }
            catch { }
        }

        private void ApplyHotkeyRegistration()
        {
            if (!IsHandleCreated) return;
            try { UnregisterHotKey(Handle, HOTKEY_ID_ALT_V); } catch { }
            try { UnregisterHotKey(Handle, HOTKEY_ID_CTRL_SHIFT_V); } catch { }

            if (GetPrefBool("globalHotkeyEnabled", true))
            {
                try { RegisterHotKey(Handle, HOTKEY_ID_ALT_V, MOD_ALT, VK_V); } catch { }
                try { RegisterHotKey(Handle, HOTKEY_ID_CTRL_SHIFT_V, MOD_CONTROL | MOD_SHIFT, VK_V); } catch { }
            }
        }

        private void ApplyLaunchAtLogin(bool enabled)
        {
            try
            {
                using (RegistryKey key = Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\CurrentVersion\Run", true))
                {
                    if (key == null) return;
                    if (enabled)
                    {
                        key.SetValue("CodeCanvas", "\"" + Application.ExecutablePath + "\" --tray");
                    }
                    else
                    {
                        key.DeleteValue("CodeCanvas", false);
                    }
                }
            }
            catch { }
        }

        protected override void WndProc(ref Message m)
        {
            if (m.Msg == WM_HOTKEY)
            {
                int id = m.WParam.ToInt32();
                if (id == HOTKEY_ID_ALT_V || id == HOTKEY_ID_CTRL_SHIFT_V)
                {
                    TriggerGlobalQuickPaste(null);
                    return;
                }
            }
            else if (m.Msg == WM_CLIPBOARDUPDATE)
            {
                OnSystemClipboardUpdated();
            }
            base.WndProc(ref m);
        }

        private void OnSystemClipboardUpdated()
        {
            if (!GetPrefBool("clipboardFloatEnabled", true)) return;

            // Only pop the bottom-right floating pill when the user is working in ANOTHER window
            bool isSelfForeground = Visible && WindowState != FormWindowState.Minimized && GetForegroundWindow() == Handle;
            if (isSelfForeground) return;

            string raw = SafeGetClipboardText();
            if (string.IsNullOrEmpty(raw) || raw.Length < 14) return;

            string sig = raw.Length + ":" + (raw.Length > 64 ? raw.Substring(0, 64) : raw);
            if (sig == _lastClipboardSignature) return;

            string kind;
            if (!LooksLikePreviewableCode(raw, out kind)) return;

            _lastClipboardSignature = sig;
            _lastToastCandidateText = raw;
            if (_toastForm == null || _toastForm.IsDisposed)
            {
                _toastForm = new ClipboardToastForm(_customIcon ?? Icon, OnToastClicked);
            }
            _toastForm.ShowToast(kind, "识别到 " + kind + " 代码 · 点击即刻预览");
        }

        private void OnToastClicked()
        {
            TriggerGlobalQuickPaste(_lastToastCandidateText);
        }

        private void TriggerGlobalQuickPaste(string overrideText)
        {
            string clipText = !string.IsNullOrEmpty(overrideText) ? overrideText : SafeGetClipboardText();
            if (!string.IsNullOrEmpty(clipText))
            {
                _lastClipboardSignature = clipText.Length + ":" + (clipText.Length > 64 ? clipText.Substring(0, 64) : clipText);
            }
            RestoreAndActivateWindow();
            if (_webViewReady && !string.IsNullOrEmpty(clipText))
            {
                string escaped = _json.Serialize(clipText);
                ExecuteRendererScript("if(window.__canvasNativeTriggerQuickPaste) window.__canvasNativeTriggerQuickPaste(" + escaped + ");");
            }
            else if (!string.IsNullOrEmpty(clipText))
            {
                _pendingClipTextToPreview = clipText;
            }
        }

        private void RestoreAndActivateWindow()
        {
            if (!Visible)
            {
                Show();
            }
            if (WindowState == FormWindowState.Minimized)
            {
                WindowState = FormWindowState.Normal;
            }
            Activate();
            BringToFront();
            try { SetForegroundWindow(Handle); } catch { }
        }

        private static void TraceLog(string msg)
        {
            try
            {
                File.AppendAllText(Path.Combine(Path.GetTempPath(), "codecanvas-trace.log"), DateTime.Now.ToString("HH:mm:ss.fff") + " " + msg + "\r\n");
            }
            catch { }
        }

        private async void OnMainFormLoad(object sender, EventArgs e)
        {
            TraceLog("OnMainFormLoad entered, _startInTray=" + _startInTray + ", Handle=" + Handle);
            if (_startInTray)
            {
                BeginInvoke(new Action(() => Hide()));
            }

            try
            {
                string webViewDataDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "CodeCanvas", "WebView2Data");
                TraceLog("Creating CoreWebView2Environment at " + webViewDataDir);
                CoreWebView2Environment env = await CoreWebView2Environment.CreateAsync(null, webViewDataDir);
                TraceLog("Calling EnsureCoreWebView2Async...");
                await _webView.EnsureCoreWebView2Async(env);
                TraceLog("EnsureCoreWebView2Async succeeded!");

                _webView.CoreWebView2.Settings.AreDefaultContextMenusEnabled = true;
                _webView.CoreWebView2.Settings.AreDevToolsEnabled = true;
                _webView.CoreWebView2.Settings.IsStatusBarEnabled = false;

                await _webView.CoreWebView2.AddScriptToExecuteOnDocumentCreatedAsync(GetBridgeInjectionScript());
                _webView.CoreWebView2.WebMessageReceived += OnWebMessageReceived;
                _webView.CoreWebView2.NavigationCompleted += (s, args) =>
                {
                    TraceLog("NavigationCompleted IsSuccess=" + args.IsSuccess + " WebErrorStatus=" + args.WebErrorStatus);
                    _webViewReady = true;
                    if (!string.IsNullOrEmpty(_pendingClipTextToPreview))
                    {
                        string text = _pendingClipTextToPreview;
                        _pendingClipTextToPreview = null;
                        string escaped = _json.Serialize(text);
                        ExecuteRendererScript("if(window.__canvasNativeTriggerQuickPaste) window.__canvasNativeTriggerQuickPaste(" + escaped + ");");
                    }
                };

                _webView.CoreWebView2.SetVirtualHostNameToFolderMapping(
                    "app.codecanvas.local",
                    _srcDir,
                    CoreWebView2HostResourceAccessKind.Allow);

                TraceLog("Navigating to https://app.codecanvas.local/index.html from _srcDir=" + _srcDir);
                _webView.CoreWebView2.Navigate("https://app.codecanvas.local/index.html");
            }
            catch (Exception ex)
            {
                TraceLog("OnMainFormLoad Exception: " + ex.ToString());
                MessageBox.Show("初始化 WebView2 渲染引擎失败: " + ex.Message, "代码画布", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }

        private void OnMainFormClosing(object sender, FormClosingEventArgs e)
        {
            TraceLog("OnMainFormClosing Reason=" + e.CloseReason + " _allowExit=" + _allowExit);
            if (!_allowExit && e.CloseReason == CloseReason.UserClosing && GetPrefBool("closeToTray", true))
            {
                e.Cancel = true;
                Hide();
                return;
            }
            _trayIcon.Visible = false;
        }

        private static string GetBridgeInjectionScript()
        {
            return @"
(function() {
  const pending = new Map();
  let seq = 1;
  window.__canvasResolveRpc = function(id, payload, err) {
    const entry = pending.get(id);
    if (!entry) return;
    pending.delete(id);
    if (err) entry.reject(new Error(err));
    else entry.resolve(payload);
  };
  function invoke(method, arg) {
    return new Promise((resolve, reject) => {
      const id = seq++;
      pending.set(id, { resolve, reject });
      window.chrome.webview.postMessage(JSON.stringify({ id, method, arg: arg === undefined ? null : arg }));
    });
  }
  window.canvasDesktop = {
    listDocuments: () => invoke('docs:list'),
    saveDocument: (doc) => invoke('docs:save', doc),
    deleteDocuments: (ids) => invoke('docs:delete', ids),
    importFiles: () => invoke('docs:importDialog'),
    exportFile: (doc) => invoke('docs:exportDialog', doc),
    readClipboard: () => invoke('clipboard:read'),
    writeClipboard: (text) => invoke('clipboard:write', text),
    getPrefs: () => invoke('prefs:get'),
    setPrefs: (patch) => invoke('prefs:set', patch),
    openStorageDir: () => invoke('shell:openStorageDir'),
    checkForUpdate: () => invoke('update:check'),
    installUpdate: (info) => invoke('update:install', info)
  };
})();";
        }

        private async void OnWebMessageReceived(object sender, CoreWebView2WebMessageReceivedEventArgs e)
        {
            int reqId = 0;
            try
            {
                string raw = e.TryGetWebMessageAsString();
                Dictionary<string, object> msg = _json.Deserialize<Dictionary<string, object>>(raw);
                reqId = Convert.ToInt32(msg["id"]);
                string method = Convert.ToString(msg["method"]);
                object arg = msg.ContainsKey("arg") ? msg["arg"] : null;

                object result = await HandleRpcAsync(method, arg);
                SendRpcResponse(reqId, result, null);
            }
            catch (Exception ex)
            {
                if (reqId != 0)
                {
                    SendRpcResponse(reqId, null, ex.Message);
                }
            }
        }

        private void SendRpcResponse(int reqId, object payload, string error)
        {
            if (IsDisposed || !_webViewReady && _webView.CoreWebView2 == null) return;
            string payloadJson = _json.Serialize(payload);
            string errJson = error == null ? "null" : _json.Serialize(error);
            ExecuteRendererScript("window.__canvasResolveRpc(" + reqId + ", " + payloadJson + ", " + errJson + ");");
        }

        private void ExecuteRendererScript(string script)
        {
            if (IsDisposed || _webView == null || _webView.CoreWebView2 == null) return;
            try { _webView.CoreWebView2.ExecuteScriptAsync(script); } catch { }
        }

        private async Task<object> HandleRpcAsync(string method, object arg)
        {
            switch (method)
            {
                case "docs:list":
                    return ReadDocuments();

                case "docs:save":
                {
                    Dictionary<string, object> doc = arg as Dictionary<string, object> ?? new Dictionary<string, object>();
                    List<Dictionary<string, object>> docs = ReadDocuments();
                    string id = doc.ContainsKey("id") && doc["id"] != null ? Convert.ToString(doc["id"]) : ("doc-" + DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() + "-" + Guid.NewGuid().ToString("N").Substring(0, 5));
                    string title = doc.ContainsKey("title") && doc["title"] != null ? Convert.ToString(doc["title"]).Trim() : "未命名画布.html";
                    if (string.IsNullOrEmpty(title)) title = "未命名画布.html";
                    string content = doc.ContainsKey("content") && doc["content"] != null ? Convert.ToString(doc["content"]) : "";
                    long now = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();

                    int existingIdx = docs.FindIndex(d => Convert.ToString(d["id"]) == id);
                    int rev = existingIdx >= 0 && docs[existingIdx].ContainsKey("revision") ? Convert.ToInt32(docs[existingIdx]["revision"]) + 1 : 1;

                    Dictionary<string, object> record = new Dictionary<string, object>
                    {
                        { "id", id },
                        { "title", title },
                        { "content", content },
                        { "updatedAt", now },
                        { "revision", rev }
                    };

                    if (existingIdx >= 0) docs[existingIdx] = record;
                    else docs.Insert(0, record);
                    WriteDocuments(docs);
                    return record;
                }

                case "docs:delete":
                {
                    HashSet<string> toDelete = new HashSet<string>();
                    System.Collections.IEnumerable list = arg as System.Collections.IEnumerable;
                    if (list != null && !(arg is string))
                    {
                        foreach (object item in list)
                        {
                            if (item != null) toDelete.Add(Convert.ToString(item));
                        }
                    }
                    else if (arg != null)
                    {
                        toDelete.Add(Convert.ToString(arg));
                    }
                    List<Dictionary<string, object>> next = ReadDocuments().FindAll(d => !toDelete.Contains(Convert.ToString(d["id"])));
                    WriteDocuments(next);
                    return next;
                }

                case "docs:importDialog":
                {
                    using (OpenFileDialog ofd = new OpenFileDialog())
                    {
                        ofd.Title = "导入代码或网页文件到代码画布";
                        ofd.Multiselect = true;
                        ofd.Filter = "代码画布支持文件 (*.html;*.htm;*.svg;*.xml;*.txt;*.md)|*.html;*.htm;*.svg;*.xml;*.txt;*.md|所有文件 (*.*)|*.*";
                        if (ofd.ShowDialog(this) != DialogResult.OK)
                        {
                            return new object[0];
                        }
                        List<Dictionary<string, object>> docs = ReadDocuments();
                        List<Dictionary<string, object>> imported = new List<Dictionary<string, object>>();
                        foreach (string filePath in ofd.FileNames)
                        {
                            string content = File.ReadAllText(filePath, Encoding.UTF8);
                            Dictionary<string, object> rec = new Dictionary<string, object>
                            {
                                { "id", "imp-" + DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() + "-" + Guid.NewGuid().ToString("N").Substring(0, 4) },
                                { "title", Path.GetFileName(filePath) },
                                { "content", content },
                                { "updatedAt", DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() },
                                { "revision", 1 }
                            };
                            docs.Insert(0, rec);
                            imported.Add(rec);
                        }
                        WriteDocuments(docs);
                        return imported;
                    }
                }

                case "docs:exportDialog":
                {
                    Dictionary<string, object> doc = arg as Dictionary<string, object> ?? new Dictionary<string, object>();
                    string suggested = doc.ContainsKey("title") && doc["title"] != null ? Convert.ToString(doc["title"]) : "canvas.html";
                    string content = doc.ContainsKey("content") && doc["content"] != null ? Convert.ToString(doc["content"]) : "";
                    using (SaveFileDialog sfd = new SaveFileDialog())
                    {
                        sfd.Title = "导出画布文件";
                        sfd.FileName = suggested;
                        sfd.Filter = "HTML 网页 (*.html)|*.html|SVG 矢量图 (*.svg)|*.svg|XML 配置 (*.xml)|*.xml|文本文件 (*.txt)|*.txt";
                        if (sfd.ShowDialog(this) != DialogResult.OK)
                        {
                            return new Dictionary<string, object> { { "saved", false } };
                        }
                        File.WriteAllText(sfd.FileName, content, new UTF8Encoding(false));
                        return new Dictionary<string, object> { { "saved", true }, { "filePath", sfd.FileName } };
                    }
                }

                case "clipboard:read":
                    return SafeGetClipboardText();

                case "clipboard:write":
                    try
                    {
                        string text = Convert.ToString(arg ?? "");
                        _lastClipboardSignature = text.Length + ":" + (text.Length > 64 ? text.Substring(0, 64) : text);
                        Clipboard.SetText(text);
                        return true;
                    }
                    catch { return false; }

                case "prefs:get":
                    _prefs["systemDark"] = IsSystemDarkTheme();
                    return _prefs;

                case "prefs:set":
                {
                    Dictionary<string, object> patch = arg as Dictionary<string, object>;
                    if (patch != null)
                    {
                        foreach (KeyValuePair<string, object> kv in patch)
                        {
                            _prefs[kv.Key] = kv.Value;
                        }
                        SavePreferences(_prefs);
                        TopMost = GetPrefBool("alwaysOnTop", false);
                        ApplyTitlebarTheme();
                        ApplyHotkeyRegistration();
                        if (patch.ContainsKey("launchAtLogin"))
                        {
                            ApplyLaunchAtLogin(GetPrefBool("launchAtLogin", false));
                        }
                    }
                    _prefs["systemDark"] = IsSystemDarkTheme();
                    return _prefs;
                }

                case "shell:openStorageDir":
                    try { Process.Start("explorer.exe", _dataDir); } catch { }
                    return true;

                case "update:check":
                    return await Task.Run(() => CheckForUpdateManifest());

                case "update:install":
                {
                    Dictionary<string, object> info = arg as Dictionary<string, object>;
                    return await Task.Run(() => DownloadAndInstallUpdate(info));
                }

                default:
                    return null;
            }
        }

        private Dictionary<string, object> CheckForUpdateManifest()
        {
            string[] urls = new[]
            {
                "https://relay.zhuquan.xyz/dl/codecanvas-desktop-latest.json?t=" + DateTimeOffset.UtcNow.ToUnixTimeSeconds(),
                "https://raw.githubusercontent.com/zhuquan7237/code-canvas/main/docs/update/desktop-latest.json?t=" + DateTimeOffset.UtcNow.ToUnixTimeSeconds()
            };
            foreach (string url in urls)
            {
                try
                {
                    HttpWebRequest req = (HttpWebRequest)WebRequest.Create(url);
                    req.UserAgent = "CodeCanvas-Desktop/" + Program.CurrentVersion;
                    req.Timeout = 8000;
                    using (HttpWebResponse resp = (HttpWebResponse)req.GetResponse())
                    using (StreamReader reader = new StreamReader(resp.GetResponseStream(), Encoding.UTF8))
                    {
                        string jsonText = reader.ReadToEnd();
                        Dictionary<string, object> data = _json.Deserialize<Dictionary<string, object>>(jsonText);
                        int remoteCode = data.ContainsKey("versionCode") ? Convert.ToInt32(data["versionCode"]) : 0;
                        string remoteName = data.ContainsKey("versionName") ? Convert.ToString(data["versionName"]) : "";
                        string setupUrl = data.ContainsKey("setupUrl") ? Convert.ToString(data["setupUrl"]) : (data.ContainsKey("downloadUrl") ? Convert.ToString(data["downloadUrl"]) : "");
                        string sha256 = data.ContainsKey("sha256") ? Convert.ToString(data["sha256"]).ToLowerInvariant() : "";
                        long fileSize = data.ContainsKey("fileSizeBytes") ? Convert.ToInt64(data["fileSizeBytes"]) : 0;
                        string changelog = data.ContainsKey("changelog") ? Convert.ToString(data["changelog"]) : "";

                        if (remoteCode > 0 && !string.IsNullOrEmpty(remoteName) && IsTrustedSetupUrl(setupUrl))
                        {
                            return new Dictionary<string, object>
                            {
                                { "status", "ok" },
                                { "hasUpdate", remoteCode > Program.CurrentVersionCode },
                                { "versionCode", remoteCode },
                                { "versionName", remoteName },
                                { "setupUrl", setupUrl },
                                { "sha256", sha256 },
                                { "fileSizeBytes", fileSize },
                                { "changelog", changelog }
                            };
                        }
                    }
                }
                catch { }
            }
            return new Dictionary<string, object>
            {
                { "status", "error" },
                { "message", "无法连接更新服务器，请稍后重试" }
            };
        }

        private Dictionary<string, object> DownloadAndInstallUpdate(Dictionary<string, object> info)
        {
            if (info == null || !info.ContainsKey("setupUrl"))
            {
                return new Dictionary<string, object> { { "ok", false }, { "message", "缺少更新包地址" } };
            }
            string setupUrl = Convert.ToString(info["setupUrl"]);
            string expectedSha = info.ContainsKey("sha256") && info["sha256"] != null ? Convert.ToString(info["sha256"]).Trim().ToLowerInvariant() : "";
            string ver = info.ContainsKey("versionName") && info["versionName"] != null ? Convert.ToString(info["versionName"]) : "latest";

            if (!IsTrustedSetupUrl(setupUrl))
            {
                return new Dictionary<string, object> { { "ok", false }, { "message", "更新包源域名未通过安全校验" } };
            }

            try
            {
                string tempSetup = Path.Combine(Path.GetTempPath(), "CodeCanvas-Setup-" + ver + ".exe");
                using (WebClient wc = new WebClient())
                {
                    wc.Headers.Add("User-Agent", "CodeCanvas-Desktop/" + Program.CurrentVersion);
                    wc.DownloadFile(setupUrl, tempSetup);
                }

                if (!string.IsNullOrEmpty(expectedSha) && expectedSha.Length == 64)
                {
                    using (FileStream fs = File.OpenRead(tempSetup))
                    using (SHA256 sha = SHA256.Create())
                    {
                        byte[] hash = sha.ComputeHash(fs);
                        StringBuilder sb = new StringBuilder(64);
                        foreach (byte b in hash) sb.Append(b.ToString("x2"));
                        if (!string.Equals(sb.ToString(), expectedSha, StringComparison.OrdinalIgnoreCase))
                        {
                            File.Delete(tempSetup);
                            return new Dictionary<string, object> { { "ok", false }, { "message", "安装包 SHA-256 校验未通过" } };
                        }
                    }
                }

                ProcessStartInfo psi = new ProcessStartInfo
                {
                    FileName = tempSetup,
                    Arguments = "--silent --relaunch",
                    UseShellExecute = true
                };
                Process.Start(psi);

                BeginInvoke(new Action(() =>
                {
                    _allowExit = true;
                    _trayIcon.Visible = false;
                    Application.Exit();
                }));

                return new Dictionary<string, object> { { "ok", true } };
            }
            catch (Exception ex)
            {
                return new Dictionary<string, object> { { "ok", false }, { "message", ex.Message } };
            }
        }

        private static bool IsTrustedSetupUrl(string url)
        {
            try
            {
                Uri u = new Uri(url);
                if (!string.Equals(u.Scheme, "https", StringComparison.OrdinalIgnoreCase)) return false;
                string h = u.Host.ToLowerInvariant();
                if (h != "relay.zhuquan.xyz" &&
                    h != "github.com" &&
                    h != "objects.githubusercontent.com" &&
                    h != "release-assets.githubusercontent.com")
                {
                    return false;
                }
                return u.AbsolutePath.EndsWith(".exe", StringComparison.OrdinalIgnoreCase);
            }
            catch { return false; }
        }

        private static bool LooksLikePreviewableCode(string rawText, out string kind)
        {
            kind = "HTML";
            if (string.IsNullOrWhiteSpace(rawText)) return false;
            string s = rawText.Trim();
            if (s.Length < 14) return false;

            if (Regex.IsMatch(s, @"```(html|htm|svg|xml)\b", RegexOptions.IgnoreCase))
            {
                if (Regex.IsMatch(s, @"```svg\b", RegexOptions.IgnoreCase)) kind = "SVG";
                else if (Regex.IsMatch(s, @"```xml\b", RegexOptions.IgnoreCase)) kind = "XML";
                return true;
            }
            if (Regex.IsMatch(s, @"^<svg[\s>]", RegexOptions.IgnoreCase) && Regex.IsMatch(s, @"</svg>", RegexOptions.IgnoreCase))
            {
                kind = "SVG";
                return true;
            }
            if (Regex.IsMatch(s, @"^<\?xml[\s\?]", RegexOptions.IgnoreCase))
            {
                kind = "XML";
                return true;
            }
            if (Regex.IsMatch(s, @"<!doctype\s+html|<html[\s>]|<head[\s>]|<body[\s>]|<style[\s>]|<script[\s>]", RegexOptions.IgnoreCase))
            {
                kind = "HTML";
                return true;
            }
            if (Regex.IsMatch(s, @"^<([a-zA-Z][\w:-]*)(\s[^>]*)?>[\s\S]*</\1>\s*$") && s.Contains("\n"))
            {
                kind = "XML";
                return true;
            }
            return false;
        }

        private static string SafeGetClipboardText()
        {
            try
            {
                if (Clipboard.ContainsText())
                {
                    return Clipboard.GetText();
                }
            }
            catch { }
            return "";
        }

        private static bool IsSystemDarkTheme()
        {
            try
            {
                using (RegistryKey k = Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\CurrentVersion\Themes\Personalize"))
                {
                    if (k != null)
                    {
                        object v = k.GetValue("AppsUseLightTheme");
                        if (v != null) return Convert.ToInt32(v) == 0;
                    }
                }
            }
            catch { }
            return true;
        }

        private Dictionary<string, object> LoadPreferences()
        {
            Dictionary<string, object> defaults = new Dictionary<string, object>
            {
                { "theme", "system" },
                { "viewMode", "split" },
                { "clipboardAutoDetect", true },
                { "clipboardFloatEnabled", true },
                { "globalHotkeyEnabled", true },
                { "closeToTray", true },
                { "launchAtLogin", false },
                { "autoCheckUpdate", true },
                { "alwaysOnTop", false },
                { "showGrid", false },
                { "wordWrap", true },
                { "fontSize", 13 },
                { "splitRatio", 0.46 }
            };
            try
            {
                if (File.Exists(_prefsFile))
                {
                    string raw = File.ReadAllText(_prefsFile, Encoding.UTF8);
                    Dictionary<string, object> saved = _json.Deserialize<Dictionary<string, object>>(raw);
                    if (saved != null)
                    {
                        foreach (KeyValuePair<string, object> kv in saved)
                        {
                            defaults[kv.Key] = kv.Value;
                        }
                    }
                }
            }
            catch { }
            return defaults;
        }

        private void SavePreferences(Dictionary<string, object> prefs)
        {
            try
            {
                File.WriteAllText(_prefsFile, _json.Serialize(prefs), new UTF8Encoding(false));
            }
            catch { }
        }

        private bool GetPrefBool(string key, bool fallback)
        {
            if (_prefs != null && _prefs.ContainsKey(key) && _prefs[key] != null)
            {
                try { return Convert.ToBoolean(_prefs[key]); } catch { }
            }
            return fallback;
        }

        private string GetPrefString(string key, string fallback)
        {
            if (_prefs != null && _prefs.ContainsKey(key) && _prefs[key] != null)
            {
                return Convert.ToString(_prefs[key]);
            }
            return fallback;
        }

        private List<Dictionary<string, object>> ReadDocuments()
        {
            try
            {
                if (File.Exists(_docsFile))
                {
                    string raw = File.ReadAllText(_docsFile, Encoding.UTF8);
                    List<Dictionary<string, object>> docs = _json.Deserialize<List<Dictionary<string, object>>>(raw);
                    if (docs != null) return docs;
                }
            }
            catch { }
            return new List<Dictionary<string, object>>();
        }

        private void WriteDocuments(List<Dictionary<string, object>> docs)
        {
            try
            {
                File.WriteAllText(_docsFile, _json.Serialize(docs), new UTF8Encoding(false));
            }
            catch { }
        }

        private void EnsureStarterDocuments()
        {
            List<Dictionary<string, object>> docs = ReadDocuments();
            if (docs.Count > 0) return;

            long now = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
            docs.Add(new Dictionary<string, object>
            {
                { "id", "starter-html-atelier" },
                { "title", "Atelier 交互粒子画布.html" },
                { "updatedAt", now },
                { "revision", 1 },
                { "content", "<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n  <meta charset=\"UTF-8\">\n  <style>\n    body {\n      margin: 0;\n      min-height: 100vh;\n      display: flex;\n      align-items: center;\n      justify-content: center;\n      background: radial-gradient(circle at 50% 30%, #1e293b, #0f172a 75%);\n      color: #f8fafc;\n      font-family: 'Segoe UI', 'Microsoft YaHei UI', sans-serif;\n    }\n    .card {\n      padding: 32px 36px;\n      border-radius: 22px;\n      background: rgba(30, 41, 59, 0.78);\n      border: 1px solid rgba(148, 163, 184, 0.25);\n      box-shadow: 0 24px 48px rgba(0, 0, 0, 0.35);\n      text-align: center;\n      max-width: 440px;\n    }\n    .badge {\n      display: inline-block;\n      padding: 4px 12px;\n      border-radius: 999px;\n      background: rgba(56, 189, 248, 0.16);\n      color: #38bdf8;\n      font-size: 12px;\n      font-weight: 600;\n      margin-bottom: 12px;\n    }\n    h1 { font-size: 22px; margin: 0 0 10px; }\n    p { color: #94a3b8; font-size: 13.5px; line-height: 1.6; margin: 0 0 20px; }\n    button {\n      padding: 10px 22px;\n      border: none;\n      border-radius: 999px;\n      background: linear-gradient(135deg, #2563eb, #38bdf8);\n      color: white;\n      font-weight: 600;\n      cursor: pointer;\n    }\n  </style>\n</head>\n<body>\n  <div class=\"card\">\n    <span class=\"badge\">Atelier Native Edition v0.5.0</span>\n    <h1>代码画布 · 极速桌面工作台</h1>\n    <p>复制任意 AI 生成的 HTML / SVG / XML 代码，点击右下角灵动浮窗或按 <b>Alt + V</b> 即可毫秒级唤醒渲染。</p>\n    <button onclick=\"this.textContent='✨ 实时沙箱交互正常 · ' + new Date().toLocaleTimeString()\">测试实时交互</button>\n  </div>\n</body>\n</html>" }
            });
            docs.Add(new Dictionary<string, object>
            {
                { "id", "starter-svg-geometry" },
                { "title", "几何黄金螺旋徽章.svg" },
                { "updatedAt", now - 60000 },
                { "revision", 1 },
                { "content", "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 320 240\">\n  <defs>\n    <linearGradient id=\"g1\" x1=\"0\" y1=\"0\" x2=\"1\" y2=\"1\">\n      <stop offset=\"0%\" stop-color=\"#38bdf8\"/>\n      <stop offset=\"100%\" stop-color=\"#818cf8\"/>\n    </linearGradient>\n  </defs>\n  <rect width=\"320\" height=\"240\" rx=\"24\" fill=\"#0D1420\"/>\n  <circle cx=\"160\" cy=\"120\" r=\"72\" fill=\"none\" stroke=\"url(#g1)\" stroke-width=\"3\"/>\n  <polygon points=\"160,56 216,154 104,154\" fill=\"none\" stroke=\"#F8FAFC\" stroke-width=\"2.5\"/>\n  <circle cx=\"160\" cy=\"124\" r=\"22\" fill=\"#38bdf8\" fill-opacity=\"0.22\" stroke=\"#38bdf8\" stroke-width=\"2\"/>\n</svg>" }
            });
            docs.Add(new Dictionary<string, object>
            {
                { "id", "starter-xml-config" },
                { "title", "Atelier 主题规范配置.xml" },
                { "updatedAt", now - 120000 },
                { "revision", 1 },
                { "content", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<atelierDesignSystem version=\"0.5.0\">\n  <palette mode=\"DualTheme\">\n    <token name=\"canvas_bg\" day=\"#F5F7FA\" night=\"#0D1420\" />\n    <token name=\"canvas_primary\" day=\"#2456C6\" night=\"#8EB4FF\" />\n    <token name=\"canvas_surface\" day=\"#FFFFFF\" night=\"#151E2C\" />\n  </palette>\n  <radii unit=\"dp\">\n    <thumbnail>12</thumbnail>\n    <card>16</card>\n    <dialog>20</dialog>\n  </radii>\n</atelierDesignSystem>" }
            });
            WriteDocuments(docs);
        }
    }
}
