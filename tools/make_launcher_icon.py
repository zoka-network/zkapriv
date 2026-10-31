"""ZKAPriv 3 launcher icon from the Z-Q mark of ZSilent.

The source is a black rounded square with a white Z and a pink Q. The adaptive
icon wants the mark alone (on transparent) as its foreground and black as its
background, so the square is dropped: a pixel's alpha is its brightness over
black, and its color is un-premultiplied from it.
"""
import os, sys
from PIL import Image, ImageDraw

SRC = r"C:\Users\mderramus\ZOKA\zsilent-core\src\main\resources\zsilent-icon.png"
RES = r"C:\Users\mderramus\ZOKA\zkapriv\app\src\main\res"
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
MARK_HEIGHT_DP = 54  # inside the 66 dp safe circle of the 108 dp canvas

src = Image.open(SRC).convert("RGBA")
w, h = src.size
mark = Image.new("RGBA", src.size, (0, 0, 0, 0))
mono = Image.new("RGBA", src.size, (0, 0, 0, 0))
sp, mp = src.load(), mark.load()
mo = mono.load()
for y in range(h):
    for x in range(w):
        r, g, b, a = sp[x, y]
        if a < 255:
            continue  # outside the rounded square
        level = max(r, g, b)
        if level < 8:
            continue
        alpha = level
        mp[x, y] = (min(255, r * 255 // alpha), min(255, g * 255 // alpha), min(255, b * 255 // alpha), alpha)
        mo[x, y] = (255, 255, 255, alpha)
box = mark.getbbox()
mark, mono = mark.crop(box), mono.crop(box)
print("mark box", box, mark.size)

def canvas(img, size_px, height_px):
    scale = height_px / img.size[1]
    scaled = img.resize((round(img.size[0] * scale), round(height_px)), Image.LANCZOS)
    out = Image.new("RGBA", (size_px, size_px), (0, 0, 0, 0))
    out.paste(scaled, ((size_px - scaled.size[0]) // 2, (size_px - scaled.size[1]) // 2), scaled)
    return out

for name, f in DENSITIES.items():
    d = os.path.join(RES, f"mipmap-{name}")
    os.makedirs(d, exist_ok=True)
    size = round(108 * f)
    canvas(mark, size, MARK_HEIGHT_DP * f).save(os.path.join(d, "ic_launcher_foreground.png"), optimize=True)
    canvas(mono, size, MARK_HEIGHT_DP * f).save(os.path.join(d, "ic_launcher_monochrome.png"), optimize=True)
    # Legacy 48 dp icons: the source square as it is, and a round one.
    legacy = round(48 * f)
    square = src.resize((legacy, legacy), Image.LANCZOS)
    square.save(os.path.join(d, "ic_launcher.png"), optimize=True)
    disc = Image.new("RGBA", (legacy, legacy), (0, 0, 0, 0))
    mask = Image.new("L", (legacy * 4, legacy * 4), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, legacy * 4 - 1, legacy * 4 - 1), fill=255)
    mask = mask.resize((legacy, legacy), Image.LANCZOS)
    black = Image.new("RGBA", (legacy, legacy), (0, 0, 0, 255))
    disc.paste(black, (0, 0), mask)
    inner = canvas(mark, legacy, legacy * 0.56)
    disc.alpha_composite(inner)
    disc.save(os.path.join(d, "ic_launcher_round.png"), optimize=True)

preview = Image.new("RGBA", (432, 432), (0, 0, 0, 255))
preview.alpha_composite(Image.open(os.path.join(RES, "mipmap-xxxhdpi", "ic_launcher_foreground.png")))
ImageDraw.Draw(preview).ellipse((84, 84, 348, 348), outline=(80, 80, 80, 255))
preview.save(sys.argv[1])
print("ok")
