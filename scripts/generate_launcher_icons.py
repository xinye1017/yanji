"""Generate launcher icons from the 卷卷 (juanjuan) mascot PNG.

Two outputs are produced for each density:
  - ic_launcher.png / ic_launcher_round.png : legacy badge (API 24–25 fallback)
  - ic_launcher_foreground.png              : adaptive-icon foreground (API 26+)

The adaptive foreground is a 108dp canvas whose central 72dp is the safe zone;
the mascot is drawn inside that zone so no launcher mask clips it.
"""
from PIL import Image, ImageDraw
import os

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(REPO, "app", "src", "main", "res", "drawable-nodpi", "juanjuan.png")
RES = os.path.join(REPO, "app", "src", "main", "res")

BG = (175, 154, 224, 255)          # #AF9AE0 — matches @color/ic_launcher_background
DENSITIES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

# Supersample so curves + the soft mascot edges stay clean at 48–192 px.
SS = 8

# Legacy badge: mascot inset, leaving the same breathing room as the old icon.
INSET = 0.80

# Adaptive icon geometry (fractions of the 108dp canvas).
# Safe zone is 72/108 = 0.667; 0.60 keeps a comfortable margin inside it.
ADAPTIVE_SAFE = 0.60


def load_mascot():
    im = Image.open(SRC).convert("RGBA")
    # Tight-crop to the meaningfully-opaque body so the character is centered
    # rather than offset by the source canvas padding.
    alpha = im.getchannel("A").point(lambda v: 255 if v > 32 else 0)
    bbox = alpha.getbbox()
    im = im.crop(bbox)
    # Square it out around the body center.
    w, h = im.size
    side = max(w, h)
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.paste(im, ((side - w) // 2, (side - h) // 2), im)
    return canvas


def fit(mascot, box_px):
    return mascot.resize((box_px, box_px), Image.LANCZOS)


def make_badge(mascot, size, round_shape):
    S = size * SS
    badge = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    draw = ImageDraw.Draw(badge)

    if round_shape:
        draw.ellipse((0, 0, S - 1, S - 1), fill=BG)
    else:
        # iOS-style squircle-ish rounded square: ~22.5% corner radius.
        radius = int(S * 0.225)
        draw.rounded_rectangle((0, 0, S - 1, S - 1), radius=radius, fill=BG)

    inner = int(S * INSET)
    badge.alpha_composite(fit(mascot, inner), ((S - inner) // 2, (S - inner) // 2))
    return badge.resize((size, size), Image.LANCZOS)


def make_adaptive_foreground(mascot, size):
    # Adaptive foreground canvas is 108dp; @ density it is size * 108/48... but
    # the launcher supplies the 108dp box from the density bucket itself, so the
    # bitmap must be (108/48) x the bucket's nominal launcher size.
    S = int(round(size * 108 / 48)) * SS
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    inner = int(S * ADAPTIVE_SAFE)
    layer.alpha_composite(fit(mascot, inner), ((S - inner) // 2, (S - inner) // 2))
    return layer.resize((S // SS, S // SS), Image.LANCZOS)


def main():
    mascot = load_mascot()
    print("mascot crop size:", mascot.size)
    for folder, size in DENSITIES.items():
        out_dir = os.path.join(RES, folder)
        os.makedirs(out_dir, exist_ok=True)
        for name, is_round in (("ic_launcher.png", False), ("ic_launcher_round.png", True)):
            make_badge(mascot, size, is_round).save(
                os.path.join(out_dir, name), "PNG", optimize=True
            )
            print(f"wrote {folder}/{name} ({size}x{size})")
        fg = make_adaptive_foreground(mascot, size)
        fg.save(os.path.join(out_dir, "ic_launcher_foreground.png"), "PNG", optimize=True)
        print(f"wrote {folder}/ic_launcher_foreground.png ({fg.size[0]}x{fg.size[1]})")


if __name__ == "__main__":
    main()
