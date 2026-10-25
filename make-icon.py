#!/usr/bin/env python3
"""Generate launcher icons (no design tool needed)."""
from PIL import Image, ImageDraw
import pathlib, math

APP = pathlib.Path(__file__).resolve().parent / "app"
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
SS = 8  # supersample


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def rounded_mask(size, radius):
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=255)
    return m


def make(px):
    S = px * SS
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))

    grad = Image.new("RGB", (S, S))
    gd = ImageDraw.Draw(grad)
    for y in range(S):
        gd.line([(0, y), (S, y)], fill=lerp((124, 92, 255), (34, 211, 238), y / S))
    img.paste(grad, (0, 0), rounded_mask(S, int(S * 0.24)))

    d = ImageDraw.Draw(img)

    # download arrow
    cx = S / 2
    shaft_w = S * 0.105
    top = S * 0.24
    bot = S * 0.63
    d.rounded_rectangle([cx - shaft_w / 2, top, cx + shaft_w / 2, bot],
                        radius=shaft_w / 2, fill=(255, 255, 255, 255))
    head = S * 0.20
    d.polygon([(cx - head, bot - S * 0.03), (cx + head, bot - S * 0.03),
               (cx, bot + head * 0.95)], fill=(255, 255, 255, 255))

    # baseline tray
    d.rounded_rectangle([S * 0.26, S * 0.735, S * 0.74, S * 0.795],
                        radius=S * 0.03, fill=(255, 255, 255, 235))

    return img.resize((px, px), Image.LANCZOS)


def main():
    for name, px in SIZES.items():
        out = APP / "res" / ("mipmap-" + name)
        out.mkdir(parents=True, exist_ok=True)
        make(px).save(out / "ic_launcher.png")
        print("wrote", out / "ic_launcher.png")


if __name__ == "__main__":
    main()
