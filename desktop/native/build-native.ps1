$ErrorActionPreference = "Stop"

$desktopRoot = Split-Path -Parent $PSScriptRoot
$repoRoot = Split-Path -Parent $desktopRoot
$nativeDir = $PSScriptRoot
$libDir = Join-Path $nativeDir "lib"
$stageDir = Join-Path $nativeDir "stage"
$distDir = Join-Path $desktopRoot "dist"
$iconIco = Join-Path $desktopRoot "assets\icon.ico"
$csc = "C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe"

if (-not (Test-Path $csc)) {
    throw "csc.exe not found at $csc"
}

# Ensure .cs files have UTF-8 BOM so csc.exe parses Chinese string literals accurately
foreach ($csFile in @("CodeCanvasHost.cs", "CodeCanvasSetup.cs")) {
    $csPath = Join-Path $nativeDir $csFile
    $text = [System.IO.File]::ReadAllText($csPath, [System.Text.Encoding]::UTF8)
    [System.IO.File]::WriteAllText($csPath, $text, (New-Object System.Text.UTF8Encoding($true)))
}

if (Test-Path $stageDir) { Remove-Item $stageDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $stageDir | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $stageDir "src") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $stageDir "assets") | Out-Null
New-Item -ItemType Directory -Force -Path $distDir | Out-Null

# Chinese filename "代码画布.exe" constructed via Unicode code points so PS 5.1 ANSI parser never mangles it
$appExeName = [string]([char]0x4EE3) + [char]0x7801 + [char]0x753B + [char]0x5E03 + ".exe"
$hostExe = Join-Path $stageDir $appExeName
$coreDll = Join-Path $libDir "Microsoft.Web.WebView2.Core.dll"
$winformsDll = Join-Path $libDir "Microsoft.Web.WebView2.WinForms.dll"
$loaderDll = Join-Path $libDir "WebView2Loader.dll"

Write-Host "1. Compiling Native Host ($appExeName)..."
& $csc /nologo /utf8output /target:winexe /platform:x64 /optimize+ `
    "/win32icon:$iconIco" `
    "/out:$hostExe" `
    "/r:$coreDll" `
    "/r:$winformsDll" `
    "/r:System.dll" `
    "/r:System.Core.dll" `
    "/r:System.Drawing.dll" `
    "/r:System.Windows.Forms.dll" `
    "/r:System.Web.Extensions.dll" `
    (Join-Path $nativeDir "CodeCanvasHost.cs")

if ($LASTEXITCODE -ne 0) {
    throw "Failed to compile CodeCanvasHost.cs"
}

Copy-Item $coreDll $stageDir -Force
Copy-Item $winformsDll $stageDir -Force
Copy-Item $loaderDll $stageDir -Force
Copy-Item (Join-Path $desktopRoot "src\*") (Join-Path $stageDir "src") -Recurse -Force
Copy-Item $iconIco (Join-Path $stageDir "assets\icon.ico") -Force

$payloadZip = Join-Path $nativeDir "payload.zip"
if (Test-Path $payloadZip) { Remove-Item $payloadZip -Force }

Write-Host "2. Creating compressed payload archive..."
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::CreateFromDirectory($stageDir, $payloadZip, [System.IO.Compression.CompressionLevel]::Optimal, $false)

$setupExe = Join-Path $distDir "CodeCanvas-Setup-0.5.0.exe"
if (Test-Path $setupExe) { Remove-Item $setupExe -Force }

Write-Host "3. Compiling Self-Contained Windows Installer (CodeCanvas-Setup-0.5.0.exe)..."
& $csc /nologo /utf8output /target:winexe /platform:x64 /optimize+ `
    "/win32icon:$iconIco" `
    "/out:$setupExe" `
    "/resource:$payloadZip,CodeCanvasPayload.zip" `
    "/r:System.dll" `
    "/r:System.Core.dll" `
    "/r:System.Drawing.dll" `
    "/r:System.Windows.Forms.dll" `
    "/r:System.IO.Compression.dll" `
    "/r:System.IO.Compression.FileSystem.dll" `
    (Join-Path $nativeDir "CodeCanvasSetup.cs")

if ($LASTEXITCODE -ne 0) {
    throw "Failed to compile CodeCanvasSetup.cs"
}

Remove-Item $payloadZip -Force

$setupItem = Get-Item $setupExe
$sha256 = (Get-FileHash -Path $setupExe -Algorithm SHA256).Hash.ToLowerInvariant()
$sizeKB = [Math]::Round($setupItem.Length / 1024, 1)

$updateDir = Join-Path $repoRoot "docs\update"
New-Item -ItemType Directory -Force -Path $updateDir | Out-Null
$manifestPath = Join-Path $updateDir "desktop-latest.json"

node (Join-Path $nativeDir "write-manifest.js")

Write-Host "[OK] Built Setup Installer: $setupExe ($sizeKB KB)"
Write-Host "[OK] SHA-256: $sha256"
Write-Host "[OK] Updated manifest: $manifestPath"
