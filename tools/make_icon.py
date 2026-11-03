# -*- coding: utf-8 -*-
"""《回血实验室》启动图标生成器（最终版）。

设计：深绿底 + 奶油色「回」+ 一颗麦色指示点。
- 「回」直接对应产品名"回血"，也和 App 内 v0.5 起的汉字分类贴纸（动/静/聊/玩/歇/吃）同一套语汇，
  不是又一个"闪电/爱心"的通用图标。
- 几何按 adaptive icon 安全区收：圆形遮罩半径 36/108，方形标记半宽上限 25.4，这里取 24.5，
  保证 Pixel 之类的圆形/圆角遮罩不会切掉角。
产物：
  res/drawable/ic_launcher_foreground.xml   矢量前景（含麦色点）
  res/drawable/ic_launcher_background.xml   矢量背景（品牌深绿）
  res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher.webp + ic_launcher_round.webp
  assets/app-icon-512.png                   商店/文档用 512 图
"""
import math
import os
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = str(ROOT / "app/src/main/res")
OUT = str(ROOT / "assets")

GREEN = (61, 90, 74)      # Look.Accent
CREAM = (247, 244, 239)   # Look.Paper
WHEAT = (224, 181, 99)    # 指示点

SS = 4
OUTER, INNER, STROKE = 24.5, 11.5, 7.4
RO, RI = 7.5, 3.0
DOT_R, DOT_AT = 3.0, 22.5


def draw_mark(d, size, with_dot=True, plate=None):
    k = size / 108.0
    if plate:
        d.rounded_rectangle([0, 0, size - 1, size - 1], radius=plate * size, fill=GREEN)
    c = 54 * k
    w = max(1, int(round(STROKE * k)))
    for half, radius in ((OUTER, RO), (INNER, RI)):
        box = [c - half * k, c - half * k, c + half * k, c + half * k]
        d.rounded_rectangle(box, radius=radius * k, outline=CREAM, width=w)
    if with_dot:
        dx = DOT_AT / math.sqrt(2) * k
        d.ellipse([c + dx - DOT_R * k, c - dx - DOT_R * k,
                   c + dx + DOT_R * k, c - dx + DOT_R * k], fill=WHEAT)


def render(size, with_dot=True, plate=None, circular=False):
    big = size * SS
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    if circular:
        ImageDraw.Draw(img).ellipse([0, 0, big - 1, big - 1], fill=GREEN + (255,))
        layer = Image.new("RGBA", (big, big), (0, 0, 0, 0))
        draw_mark(ImageDraw.Draw(layer), big, with_dot=with_dot)
        img.alpha_composite(layer)
    else:
        draw_mark(ImageDraw.Draw(img), big, with_dot=with_dot, plate=plate)
    return img.resize((size, size), Image.LANCZOS)


def rrect_path(half, radius, stroke):
    """描边圆角矩形 → 中心线 path。"""
    h = half - stroke / 2.0
    r = max(0.0, radius - stroke / 2.0)
    c = 54.0
    x0, y0, x1, y1 = c - h, c - h, c + h, c + h
    if r <= 0.01:
        return (f"M{x0:.2f},{y0:.2f} L{x1:.2f},{y0:.2f} L{x1:.2f},{y1:.2f} "
                f"L{x0:.2f},{y1:.2f} Z")
    return (f"M{x0 + r:.2f},{y0:.2f} L{x1 - r:.2f},{y0:.2f} "
            f"A{r:.2f},{r:.2f} 0 0,1 {x1:.2f},{y0 + r:.2f} "
            f"L{x1:.2f},{y1 - r:.2f} A{r:.2f},{r:.2f} 0 0,1 {x1 - r:.2f},{y1:.2f} "
            f"L{x0 + r:.2f},{y1:.2f} A{r:.2f},{r:.2f} 0 0,1 {x0:.2f},{y1 - r:.2f} "
            f"L{x0:.2f},{y0 + r:.2f} A{r:.2f},{r:.2f} 0 0,1 {x0 + r:.2f},{y0:.2f} Z")


def vec_foreground():
    dx = DOT_AT / math.sqrt(2)
    x, y = 54 + dx, 54 - dx
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- 回血实验室 图标前景：奶油色「回」+ 麦色指示点（与 App 内汉字分类贴纸同一套语汇） -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:pathData="{rrect_path(OUTER, RO, STROKE)}"
        android:strokeColor="#F7F4EF"
        android:strokeWidth="{STROKE}"
        android:strokeLineJoin="round"
        android:fillColor="#00000000" />
    <path
        android:pathData="{rrect_path(INNER, RI, STROKE)}"
        android:strokeColor="#F7F4EF"
        android:strokeWidth="{STROKE}"
        android:strokeLineJoin="round"
        android:fillColor="#00000000" />
    <path
        android:pathData="M{x - DOT_R:.2f},{y:.2f} a{DOT_R},{DOT_R} 0 1,0 {2 * DOT_R},0 a{DOT_R},{DOT_R} 0 1,0 {-2 * DOT_R},0 z"
        android:fillColor="#E0B563" />
</vector>
"""


def vec_background():
    return """<?xml version="1.0" encoding="utf-8"?>
<!-- 回血实验室 图标背景：品牌深绿 #3D5A4A -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#3D5A4A"
        android:pathData="M0,0h108v108h-108z" />
</vector>
"""


DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def main():
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(RES, "drawable", "ic_launcher_foreground.xml"), "w",
              encoding="utf-8", newline="\n") as f:
        f.write(vec_foreground())
    with open(os.path.join(RES, "drawable", "ic_launcher_background.xml"), "w",
              encoding="utf-8", newline="\n") as f:
        f.write(vec_background())

    for name, px in DENSITIES.items():
        folder = os.path.join(RES, f"mipmap-{name}")
        render(px, plate=0.235).save(os.path.join(folder, "ic_launcher.webp"),
                                     "WEBP", quality=95, method=6)
        render(px, circular=True).save(os.path.join(folder, "ic_launcher_round.webp"),
                                       "WEBP", quality=95, method=6)
        print(f"mipmap-{name}: {px}px")

    render(512, plate=0.235).save(os.path.join(OUT, "app-icon-512.png"))
    print("done")


if __name__ == "__main__":
    main()
