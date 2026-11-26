# Code Canvas Android v0.6.11 / 0.7 roadmap, round 1 of 10

## 中文

- 从文件或分享导入 Jupyter Notebook（.ipynb，v4）。离线只读，按单元显示基础 Markdown、源码、已保存的文本输出和 PNG/JPEG 图表。长输出可以展开。
- 不执行 Python、网页脚本或交互组件，不自动读取远程资源；原文件以原始字节保留，可导出或分享。已在旧版导成普通文本的 Notebook 可以重新导入原文件。
- 预览限制为 8 MiB，导入限制为 16 MiB。超量单元、文本和输出会明确提示，不能安全显示的富输出会提示或显示可用的纯文本。

## English

- Import Jupyter Notebook v4 (.ipynb) from Files or Share. The offline, read-only viewer shows basic Markdown, source, saved text, and static PNG/JPEG plots. Expand long saved output on demand.
- There is no code execution, active notebook HTML, or remote loading. The original bytes stay available for export and sharing. Re-import older notebooks previously imported as plain text.
- Preview is bounded to 8 MiB and import to 16 MiB; unsupported or truncated output is identified rather than silently disappearing.
