"""Generate the matching Chinese and English static product pages."""
from pathlib import Path
from html import escape

ROOT = Path(__file__).resolve().parents[1] / "web/site"
TEXT = {
 "zh": {
  "lang":"zh-CN","skip":"跳到主要内容","brand":"代码画布","video":"演示视频","other":"English","kicker":"把代码变成可预览的作品","hero":"粘贴就能预览。","description":"从 AI 聊天或本地文件带来一段代码，先看效果，再改，再导出。代码和草稿留在当前设备，不用注册。",
  "open":"打开网页版","android":"下载安卓版","small":"先试内置样例，再导入自己的作品。","alt":"代码画布网页版的编辑与预览界面","mobilealt":"代码画布网页版在手机上的预览界面","figure":"网页版实拍 · 源码与效果可以一起检查",
  "start":"三步开始","steps":[("打开或导入","网页版点「载入示例」，也可以粘贴代码或导入本地文件。"),("看效果再修改","在源码与预览之间切换。HTML / SVG 可渲染，普通文本按原文查看。"),("导出或离线回来","导出保留一份文件。首次成功加载后，支持离线的浏览器可继续打开工作区。")],
  "follow":"跟着实拍走一遍","caption":"普通话配音 · 中文字幕为主，英文为辅","download":"下载 MP4","caps":"字幕","menu":"选你常用的设备","options":[("手机浏览器","iPhone / iPad 用 Safari 打开，分享 → 添加到主屏幕；有「作为网页 App 打开」就开启。Android Chrome 可从菜单安装。"),("Windows 或 Mac","直接用浏览器打开。支持安装的 Chrome／Edge 会在地址栏或菜单出现安装入口。"),("原生 Android","需要更多文件格式、系统分享或本地文件工作流，下载 APK。原生 iOS 暂未正式发布。")],
  "privacy":"网页版仅在浏览器本地保存草稿，不跨设备同步；清理站点数据会丢失未导出的内容。网页版不等同安卓完整版。",
  "source":"查看源码","release":"发布记录","readme":"详细说明","cn":"中文","github":"GitHub","apple":"Apple 官方安装说明"},
 "en":{
  "lang":"en","skip":"Skip to content","brand":"Code Canvas","video":"Watch the demo","other":"中文","kicker":"A small workspace for live files","hero":"Paste code. See the result.","description":"Bring in a snippet from an AI chat or a local file. Preview it, make a change, then export. Your code and drafts stay on this device. No account needed.",
  "open":"Open the web app","android":"Download for Android","small":"Start with a sample or bring your own file.","alt":"The Code Canvas web editor and live preview","mobilealt":"Code Canvas web preview on a phone","figure":"Actual web app, with source and preview side by side",
  "start":"Get started in three steps","steps":[("Open or import","Load the built-in sample, paste a snippet, or import a local file."),("Preview and change it","Switch between source and preview. HTML and SVG render; other text remains readable as source."),("Export or come back offline","Export a copy you want to keep. Once the app loads and caches successfully, compatible browsers can open it offline.")],
  "follow":"See the real workflow","caption":"English narration · English captions","download":"Download MP4","caps":"Captions","menu":"Choose your device","options":[("Phone or tablet","On iPhone or iPad, open in Safari, then Share → Add to Home Screen. Turn on Open as Web App if offered. On Android, use Chrome’s install option."),("Windows or Mac","Open it in a browser. Compatible Chrome or Edge browsers may show an install shortcut in the address bar or menu."),("Native Android","Use the APK for additional formats and native file sharing. There is no production native iOS release.")],
  "privacy":"Web drafts stay in this browser and do not sync. Export before clearing site data. This web edition is not a full Android port.",
  "source":"View source","release":"Releases","readme":"Project README","cn":"English","github":"GitHub","apple":"Apple’s install guide"}
}
REPO = "https://github.com/zhuquan7237/code-canvas"
APPLE = "https://support.apple.com/guide/iphone/iphea86e5236/ios"
for key, t in TEXT.items():
    en = key == "en"
    base = "../" if en else "./"
    swap = "../" if en else "./en/"
    readme = f"{REPO}/blob/main/README.en.md" if en else f"{REPO}/blob/main/README.md"
    cards = lambda items, numbered: "".join(
        f'<article class="card">{f"<span class=count>0{i}</span>" if numbered else ""}<h3>{escape(title)}</h3><p>{escape(body)}</p></article>'
        for i, (title, body) in enumerate(items, 1))
    page = f"""<!doctype html>
<html lang="{t["lang"]}">
<head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="description" content="{escape(t["description"], quote=True)}">
<title>{t["brand"]}</title><link rel="stylesheet" href="{base}site.css">
</head>
<body>
<a class="skip" href="#main">{t["skip"]}</a>
<header class="head"><div class="shell"><a class="logo" href="{base if not en else './'}"><span aria-hidden="true">&lt;/&gt;</span>{t["brand"]}</a>
<nav><a href="#demo">{t["video"]}</a><a href="{REPO}">{t["github"]}</a><a href="{swap}" lang="{TEXT['zh' if en else 'en']['lang']}">{t["other"]}</a></nav></div></header>
<main id="main" class="shell">
<section class="hero">
<div><span class="eyebrow">{t["kicker"]}</span><h1>{t["hero"]}</h1><p>{t["description"]}</p>
<div class="actions"><a class="button primary" href="{base}app/?lang={key}">{t["open"]}</a><a class="button" href="{REPO}/releases/latest">{t["android"]}</a></div>
<p class="small">{t["small"]}</p></div>
<figure class="feature-img"><div class="frame"><img src="{base}media/workspace-{key}.webp" width="1280" height="800" alt="{t["alt"]}" fetchpriority="high"></div><figcaption>{t["figure"]}</figcaption></figure>
</section>
<section class="band" aria-labelledby="start"><h2 id="start">{t["start"]}</h2><div class="trio">{cards(t["steps"], True)}</div></section>
<section class="band" id="demo"><h2>{t["follow"]}</h2><p class="kicker">{t["caption"]}</p>
<div class="walk"><div><video controls playsinline preload="none" poster="{base}media/poster-{key}.webp" aria-label="{t["caption"]}">
<source src="{base}media/demo-{key}.mp4" type="video/mp4">
<track kind="captions" src="{base}media/demo-{key}.vtt" srclang="{t["lang"]}" label="{t["caps"]}">
</video><p class="small"><a href="{base}media/demo-{key}.mp4" download>{t["download"]}</a> · <a href="{base}media/demo-{key}.vtt">{t["caps"]}</a></p></div>
<img class="mobile" src="{base}media/mobile-{key}.webp" loading="lazy" alt="{t["mobilealt"]}"></div></section>
<section class="band" aria-labelledby="install"><h2 id="install">{t["menu"]}</h2><div class="trio">{cards(t["options"],False)}</div>
<p class="small"><a href="{APPLE}">{t["apple"]}</a></p><p class="note small">{t["privacy"]}</p></section>
</main>
<footer class="foot"><div class="shell"><span>{t["brand"]}</span><nav><a href="{REPO}">{t["source"]}</a><a href="{readme}">{t["readme"]}</a><a href="{REPO}/releases">{t["release"]}</a><a href="{swap}">{t["other"]}</a></nav></div></footer>
</body></html>
"""
    path = ROOT / ("en/index.html" if en else "index.html")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(page, encoding="utf-8")
