# Code Canvas 0.6.8 · Android + Web

## Android

- 刷新、全屏、作品库和编辑器的主要按钮改为有边框的圆角控件。适配切换与缩放分组，选中项与外边框连贯。
- SVG「适应窗口」还原作品自带的取景；「适应内容」按实际绘制范围重新取景。切换适配或旋转屏幕不会重载 SVG、清空正在运行的作品状态。
- 改进窄屏、大字体和浅色／深色布局。正式 APK 沿用原签名，可覆盖升级。

## Web/PWA

- 按安卓版作品库和编辑预览设计重做。初次打开是空作品库；没有不能删除的示例。支持文件导入、编辑、导出、列表／网格、本地草稿和离线打开。
- 预览的内容区与外框分开，边角不遮挡导入内容。作品库和代码／预览切换有短过渡，也尊重系统的减少动态效果设置。
- 可测量的 SVG 可以按实际内容适配。复杂 SVG 保留原视口；HTML 可以独立滚动。网页预览继续禁用上传文件中的脚本和联网访问，与安卓原生版的能力不完全相同。
- [打开网页版](https://canvas.zhuquan.xyz/app/) · [代码画布官网](https://canvas.zhuquan.xyz/) · [普通话实拍与双语字幕](https://canvas.zhuquan.xyz/#demo) · [English walkthrough](https://canvas.zhuquan.xyz/en/#demo)

旧 dsh 域名的本地草稿不会自动迁移到 canvas。此版本不包含原生 iOS 安装包。

The new PWA follows the Android library and preview workflow. It opens to an empty personal library, keeps file contents local, and supports offline reopening. Install the Android APK for expanded format support and native sharing.
