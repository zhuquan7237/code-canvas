# Code Canvas v0.7.2 (Android)

## 中文

- 在作品库打开任一文本作品的菜单，选「对比另一份文本」，再选择对照稿。用文件时间、字符数和末尾编号区分同名作品。
- 对比只读取本地保存的文本，不修改文件、不运行代码、不联网。带行号的新增和删除可交换方向查看；同一行结尾处的空格与制表符用符号标出。
- LF、CRLF、CR 的写法差异不计入对比，但会明确报告文件末尾的换行有无。比较的是解码后的文本，不是文件字节。首版每份最多 65,536 字符、1,000 行，每行最多 1,024 字符。
- 本轮更新只发布 Android APK，未改变其他平台。离线只读 Jupyter Notebook 仍可打开。

## English

- From a saved text file’s menu, choose **Compare with another text file** and select the comparison file. The picker uses a timestamp, character count, and short ID to distinguish copies with the same name.
- Compare locally and read-only, with numbered additions/removals and a swap action. This never changes or runs the files. Trailing spaces and tabs are marked.
- LF, CRLF, and CR styles are treated alike, but a missing final newline is reported separately. This compares decoded text, not original bytes. Each file may have at most 65,536 characters, 1,000 lines, and 1,024 characters per line.
- This release ships the Android APK only. Offline read-only Jupyter Notebook preview is available on Android.
