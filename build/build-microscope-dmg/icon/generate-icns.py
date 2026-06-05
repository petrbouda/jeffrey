#!/usr/bin/env python3
# Jeffrey
# Copyright (C) 2026 Petr Bouda
#
# Regenerates the macOS app icon (Microscope.icns) from the Jeffrey brand mark.
# The mark mirrors static/logo/jeffrey-icon.svg: four stacked rounded bars forming a
# downward funnel, on a transparent background. Re-run after the brand SVG changes:
#
#     python3 build/build-microscope-dmg/icon/generate-icns.py
#
# Requires only Pillow (no external icon tooling). Output is committed so CI and local
# DMG builds need nothing beyond the JDK.

from pathlib import Path

from PIL import Image, ImageDraw

# Bars copied verbatim from static/logo/jeffrey-icon.svg (viewBox 0 0 110 110):
# (x, y, width, height, corner-radius, fill)
VIEWBOX = 110
BARS = [
    (8, 86, 94, 14, 4, "#bf360c"),
    (20, 64, 70, 14, 4, "#e65100"),
    (32, 42, 46, 14, 4, "#ff6d00"),
    (42, 20, 26, 14, 4, "#ffab00"),
]

# Render at high resolution; Pillow downscales to the standard ICNS sizes on save.
RENDER_SIZE = 1024

OUTPUT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "Microscope.icns"


def render(size: int) -> Image.Image:
    scale = size / VIEWBOX
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    for x, y, width, height, radius, fill in BARS:
        box = [x * scale, y * scale, (x + width) * scale, (y + height) * scale]
        draw.rounded_rectangle(box, radius=radius * scale, fill=fill)
    return image


def main() -> None:
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    icon = render(RENDER_SIZE)
    icon.save(OUTPUT, format="ICNS")
    print(f"Wrote {OUTPUT} ({OUTPUT.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
