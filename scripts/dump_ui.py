"""从 uiautomator dump 的 XML 中提取「可见文本 -> 中心坐标」，用于 adb input tap 定位。

用法: python dump_ui.py <ui.xml>
"""
import re
import sys

if sys.stdout.encoding.lower() != 'utf-8':
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass

path = sys.argv[1]
raw = open(path, encoding="utf-8").read()

pattern = re.compile(
    r'(?:text|content-desc)="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
)

seen = set()
for match in pattern.finditer(raw):
    label = match.group(1).strip()
    if not label or label in seen:
        continue
    seen.add(label)
    l, t, r, b = (int(match.group(i)) for i in range(2, 6))
    print(f"{label}\t({(l + r) // 2},{(t + b) // 2})")
