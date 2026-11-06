#!/usr/bin/env python3
"""Generates the launcher icon layers and the in-app logo from NEAT's logo.

Usage: python3 tools/gen_icons.py path/to/neat/browser/branding/official/default2000.png

Writes into app/src/main/res:
  mipmap-*/ic_launcher_foreground.png   logo inside the adaptive-icon safe zone
  mipmap-*/ic_launcher_monochrome.png   white silhouette for themed icons (Android 13+)
  drawable-nodpi/neat_logo.png          512 px logo for the home screen
The icon background is the colour @color/ic_launcher_background (res/values/colors.xml).
"""
import sys
from pathlib import Path
from PIL import Image

RES = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res"
DENSITIES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
LOGO_FRACTION = 0.62  # of the 108 dp layer; the safe zone is 66 dp (61 %)


def layer(logo: Image.Image, px: int, mono: bool) -> Image.Image:
    size = round(px * LOGO_FRACTION)
    scaled = logo.resize((size, size), Image.LANCZOS)
    if mono:
        alpha = scaled.getchannel("A")
        scaled = Image.merge("RGBA", (*[Image.new("L", scaled.size, 255)] * 3, alpha))
    canvas = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    canvas.paste(scaled, ((px - size) // 2, (px - size) // 2), scaled)
    return canvas


def main() -> None:
    logo = Image.open(sys.argv[1]).convert("RGBA")
    for name, px in DENSITIES.items():
        out = RES / f"mipmap-{name}"
        out.mkdir(parents=True, exist_ok=True)
        layer(logo, px, mono=False).save(out / "ic_launcher_foreground.png", optimize=True)
        layer(logo, px, mono=True).save(out / "ic_launcher_monochrome.png", optimize=True)
    out = RES / "drawable-nodpi"
    out.mkdir(parents=True, exist_ok=True)
    logo.resize((512, 512), Image.LANCZOS).save(out / "neat_logo.png", optimize=True)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main()
