"""Package the existing Bingbing artwork for Android launcher masks (requires Pillow)."""

from math import hypot
from pathlib import Path
from xml.etree import ElementTree

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
BACKGROUND = ElementTree.parse(RES / "values/ic_launcher_background.xml").find("color").text
SOURCE = Image.open(RES / "drawable-nodpi/bingbing.png").convert("RGBA")
ART = SOURCE.crop(SOURCE.getchannel("A").getbbox())


def alpha_radius(image):
    cx, cy = image.width / 2, image.height / 2
    return max(
        hypot(x + 0.5 - cx, y + 0.5 - cy)
        for y in range(image.height)
        for x in range(image.width)
        if image.getpixel((x, y))[3] > 4
    )


ART_RADIUS = alpha_radius(ART)


def foreground(side, radius):
    scale = radius / ART_RADIUS
    art = ART.resize(
        (round(ART.width * scale), round(ART.height * scale)), Image.Resampling.LANCZOS
    )
    canvas = Image.new("RGBA", (side, side))
    canvas.alpha_composite(art, ((side - art.width) // 2, (side - art.height) // 2))
    return canvas


def icon_mask(side, circular):
    mask = Image.new("L", (side, side))
    draw = ImageDraw.Draw(mask)
    if circular:
        draw.ellipse((0, 0, side - 1, side - 1), fill=255)
    else:
        draw.rounded_rectangle((0, 0, side - 1, side - 1), radius=side * 0.23, fill=255)
    return mask


for density, scale in DENSITIES.items():
    directory = RES / f"mipmap-{density}"
    directory.mkdir(exist_ok=True)
    side = round(108 * scale)
    layer = foreground(side, 31 * scale)
    # Android adaptive icons have a 66dp diameter safe circle on a 108dp canvas.
    assert alpha_radius(layer) <= 33 * scale, f"{density}: artwork exceeds safe zone"
    layer.save(directory / "ic_launcher_foreground.png", optimize=True)
    side = round(48 * scale)
    for name, circular in (("ic_launcher", False), ("ic_launcher_round", True)):
        icon = Image.new("RGBA", (side, side), BACKGROUND)
        icon.alpha_composite(foreground(side, side * 0.43))
        icon.putalpha(icon_mask(side, circular))
        icon.save(directory / f"{name}.png", optimize=True)

# Inspect packaged assets locally, without interacting with the user's phone.
preview = Image.new("RGB", (900, 340), "#F2F4F7")
draw = ImageDraw.Draw(preview)
layer = Image.open(RES / "mipmap-xxxhdpi/ic_launcher_foreground.png")
for index, circular in enumerate((True, False)):
    icon = Image.new("RGBA", layer.size, BACKGROUND)
    icon.alpha_composite(layer)
    icon = icon.crop((72, 72, 360, 360))
    icon.putalpha(icon_mask(288, circular))
    icon = icon.resize((240, 240), Image.Resampling.LANCZOS)
    preview.paste(icon, (30 + index * 290, 30), icon)
    draw.text((30 + index * 290, 290), "Adaptive circle" if circular else "Adaptive squircle", fill="#263442")
avatar = Image.new("RGBA", (240, 240), BACKGROUND)
avatar.putalpha(icon_mask(240, False))
art = SOURCE.copy()
art.thumbnail((168, 168), Image.Resampling.LANCZOS)
avatar.alpha_composite(art, ((240 - art.width) // 2, (240 - art.height) // 2))
preview.paste(avatar, (610, 30), avatar)
draw.text((610, 290), "Profile avatar: complete artwork", fill="#263442")
output = ROOT / "build/orca-reports/launcher-icon-preview.png"
output.parent.mkdir(parents=True, exist_ok=True)
preview.save(output)
print(f"Generated 15 launcher assets; all foregrounds fit the adaptive safe zone. Preview: {output}")
