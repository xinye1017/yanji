"""Package the owner's pencil artwork as Android launcher icons (requires Pillow)."""

from pathlib import Path
from xml.etree import ElementTree

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
BACKGROUND = ElementTree.parse(RES / "values/ic_launcher_background.xml").find("color").text
SOURCE = Image.open(ROOT / "assets/branding/launcher-icon.jpg").convert("RGB")
assert SOURCE.width == SOURCE.height, "Launcher artwork must be square"


def foreground(side):
    # Fit the original composition into the adaptive 66dp safe area. The artwork's
    # corner whitespace keeps the pencil and stroke inside circular masks.
    inner = round(side * 66 / 108)
    art = SOURCE.resize((inner, inner), Image.Resampling.LANCZOS)
    offset = (side - inner) // 2
    canvas = Image.new("RGB", (side, side), BACKGROUND)
    canvas.paste(art, (offset, offset))
    # Extend edge pixels into the overscan area to avoid a flat-color seam.
    for box, target in (
        ((0, 0, inner, 1), (offset, 0, offset + inner, offset)),
        ((0, inner - 1, inner, inner), (offset, offset + inner, offset + inner, side)),
        ((0, 0, 1, inner), (0, offset, offset, offset + inner)),
        ((inner - 1, 0, inner, inner), (offset + inner, offset, side, offset + inner)),
    ):
        canvas.paste(art.crop(box).resize((target[2] - target[0], target[3] - target[1])), target)
    for x0, x1, sx in ((0, offset, 0), (offset + inner, side, inner - 1)):
        for y0, y1, sy in ((0, offset, 0), (offset + inner, side, inner - 1)):
            canvas.paste(art.getpixel((sx, sy)), (x0, y0, x1, y1))
    return canvas.convert("RGBA")


def icon_mask(side, circular):
    # Supersample the legacy mask to keep small icon edges smooth.
    size = side * 4
    mask = Image.new("L", (size, size))
    draw = ImageDraw.Draw(mask)
    if circular:
        draw.ellipse((0, 0, size - 1, size - 1), fill=255)
    else:
        draw.rounded_rectangle((0, 0, size - 1, size - 1), radius=size * 0.23, fill=255)
    return mask.resize((side, side), Image.Resampling.LANCZOS)


for density, scale in DENSITIES.items():
    directory = RES / f"mipmap-{density}"
    directory.mkdir(exist_ok=True)
    foreground(round(108 * scale)).save(directory / "ic_launcher_foreground.png", optimize=True)
    side = round(48 * scale)
    for name, circular in (("ic_launcher", False), ("ic_launcher_round", True)):
        icon = SOURCE.resize((side, side), Image.Resampling.LANCZOS).convert("RGBA")
        icon.putalpha(icon_mask(side, circular))
        icon.save(directory / f"{name}.png", optimize=True)


preview = Image.new("RGB", (1160, 340), "#F2F4F7")
draw = ImageDraw.Draw(preview)
layer = Image.open(RES / "mipmap-xxxhdpi/ic_launcher_foreground.png")
for index, (label, circular, adaptive) in enumerate((
    ("Adaptive circle", True, True),
    ("Adaptive rounded square", False, True),
    ("Legacy circle", True, False),
    ("Legacy rounded square", False, False),
)):
    icon = layer.crop((72, 72, 360, 360)) if adaptive else SOURCE.copy()
    icon = icon.resize((240, 240), Image.Resampling.LANCZOS).convert("RGBA")
    icon.putalpha(icon_mask(240, circular))
    preview.paste(icon, (25 + index * 290, 25), icon)
    draw.text((25 + index * 290, 290), label, fill="#263442")
output = ROOT / "build/orca-reports/launcher-icon-preview.png"
output.parent.mkdir(parents=True, exist_ok=True)
preview.save(output)
print(f"Generated 15 launcher assets. Preview: {output}")
