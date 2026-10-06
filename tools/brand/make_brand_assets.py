#!/usr/bin/env python3
"""
Makes every QVoice brand asset from the owner's logo artwork.

    pip install numpy scipy pillow opencv-python-headless
    python tools/brand/make_brand_assets.py

Input:  tools/brand/logo-master.png  (the owner's 1254 px square design:
        glowing Q + speaker + waves on a navy rounded square, black corners)
Output (committed, so nobody needs to run this unless the logo changes):
  app/src/main/res/mipmap-*/ic_launcher_foreground.png  adaptive-icon foreground
  app/src/main/res/drawable/ic_launcher_background.xml  gradient background
  app/src/main/res/drawable/ic_launcher_monochrome.xml  themed-icon glyph
  app/src/main/res/drawable-nodpi/qvoice_logo.png       in-app logo (72 dp view)
  store/play-icon-512.png                               Play Store icon
  store/feature-graphic-1024x500.png                    Play Store feature graphic

How the artwork is separated from its background: a smooth background field
is fitted to the dark pixels (glow only ever adds light, so the fit keeps a
lower envelope), then each pixel's alpha is the least that explains its
colour over that background ("colour to alpha"). Composited over the same
background the result is the original, pixel for pixel; over the designed
gradient below it looks the same. The alpha is then limited to the artwork's
neighbourhood, which drops the original's rim light and corner haze — a
launcher mask would cut those into a visible edge.
"""
import os
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from scipy import ndimage as ndi

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
RES = os.path.join(ROOT, "app", "src", "main", "res")
STORE = os.path.join(ROOT, "store")

# Launcher artwork size: the owner's square maps to 68 of the 72 dp that every
# mask shows (Q ring inside the 66 dp safe zone; tail tip inside a circle mask).
ART_DP = 68.0
# Themed-icon glyph size (a solid glyph reads heavier than the glowing art).
GLYPH_DP = 64.0

# Background design on the 108 dp canvas; the same numbers go into the vector.
BG = {
    "base": {"cx": 54, "cy": 56, "r": 58, "stops": [(0.0, "#FF04073A"), (0.55, "#FF0E0C55"), (1.0, "#FF2B1673")]},
    "glow_tl": {"cx": 22, "cy": 20, "r": 46, "stops": [(0.0, "#A6612AA8"), (1.0, "#00612AA8")]},
    "glow_br": {"cx": 92, "cy": 94, "r": 40, "stops": [(0.0, "#661B3FC4"), (1.0, "#001B3FC4")]},
}


# ---------------------------------------------------------------- matte
def load_master():
    src = np.asarray(Image.open(os.path.join(HERE, "logo-master.png")).convert("RGB")).astype(np.float64) / 255.0
    assert src.shape[0] == src.shape[1], "the master must be square"
    return src


def rounded_mask(size, inset, radius):
    yy, xx = np.mgrid[0:size, 0:size]
    x0 = y0 = inset
    x1 = y1 = size - 1 - inset
    rr = max(radius - inset, 1)
    cx = np.clip(xx, x0 + rr, x1 - rr)
    cy = np.clip(yy, y0 + rr, y1 - rr)
    return (xx - cx) ** 2 + (yy - cy) ** 2 <= rr ** 2


def extract(src):
    S = src.shape[0]
    corner_r = int(round(S * 0.195))            # the design's corner radius
    yy, xx = np.mgrid[0:S, 0:S]
    X = xx / (S - 1) * 2 - 1
    Y = yy / (S - 1) * 2 - 1
    terms = [np.ones_like(X), X, Y, X * X, X * Y, Y * Y, X ** 3, X * X * Y, X * Y * Y, Y ** 3]
    A = np.stack([t.ravel() for t in terms], 1)
    lum = src @ np.array([0.2126, 0.7152, 0.0722])
    idx = np.flatnonzero((rounded_mask(S, int(S * 0.048), corner_r) & (lum < 0.12)).ravel())
    B = np.zeros_like(src)
    for c in range(3):
        v = src[..., c].ravel()
        keep = idx
        for _ in range(6):
            coef, *_ = np.linalg.lstsq(A[keep], v[keep], rcond=None)
            resid = v - A @ coef
            keep = idx[resid[idx] <= np.percentile(resid[keep], 70)]
        B[..., c] = (A @ coef).reshape(S, S)
    B = np.clip(B, 0, 1)
    diff = src - B
    alpha = np.clip(np.max(np.where(diff > 0, diff / np.maximum(1 - B, 1e-6), 0), axis=2), 0, 1)
    alpha *= ndi.gaussian_filter(rounded_mask(S, int(S * 0.062), corner_r).astype(float), 8)
    core = ndi.binary_opening(ndi.gaussian_filter(alpha, 3) > 0.45, iterations=2)
    near = ndi.binary_dilation(core, iterations=int(S * 0.056))
    alpha *= ndi.gaussian_filter(near.astype(float), 18)
    alpha = np.where(alpha < 0.015, 0, alpha)
    F = np.clip(np.where(alpha[..., None] > 0, B + diff / np.maximum(alpha[..., None], 1e-6), 0), 0, 1)
    return F, alpha


# ---------------------------------------------------------------- rendering helpers
def hex_argb(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (2, 4, 6)]) / 255.0, int(h[0:2], 16) / 255.0


def radial(px, spec):
    yy, xx = (np.mgrid[0:px, 0:px] + 0.5) * (108.0 / px)
    t = np.clip(np.hypot(xx - spec["cx"], yy - spec["cy"]) / spec["r"], 0, 1)
    rgb = np.zeros((px, px, 3))
    a = np.zeros((px, px))
    for (p0, c0), (p1, c1) in zip(spec["stops"], spec["stops"][1:]):
        (r0, a0), (r1, a1) = hex_argb(c0), hex_argb(c1)
        m = (t >= p0) & (t <= p1)
        f = ((t - p0) / max(p1 - p0, 1e-9))[m]
        rgb[m] = r0 * (1 - f[:, None]) + r1 * f[:, None]
        a[m] = a0 * (1 - f) + a1 * f
    return rgb, a


def background(px):
    rgb, _ = radial(px, BG["base"])
    for key in ("glow_tl", "glow_br"):
        c, a = radial(px, BG[key])
        rgb = c * a[..., None] + rgb * (1 - a[..., None])
    return rgb


def place(F, alpha, px, content_dp):
    """The artwork on a px-square 108 dp canvas, premultiplied resize (no dark fringes)."""
    side = int(round(px * content_dp / 108.0))
    out = np.zeros((px, px, 4))
    o = (px - side) // 2
    for i, ch in enumerate([F[..., 0] * alpha, F[..., 1] * alpha, F[..., 2] * alpha, alpha]):
        img = Image.fromarray((ch * 255 + 0.5).astype(np.uint8)).resize((side, side), Image.LANCZOS)
        out[o:o + side, o:o + side, i] = np.asarray(img).astype(np.float64) / 255
    a = np.clip(out[..., 3], 0, 1)
    rgb = np.where(a[..., None] > 1e-4, out[..., :3] / np.maximum(a[..., None], 1e-4), 0)
    return np.clip(rgb, 0, 1), a


def save(arr, path, alpha=None):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    data = arr if alpha is None else np.dstack([arr, alpha])
    Image.fromarray((np.clip(data, 0, 1) * 255 + 0.5).astype(np.uint8), "RGB" if alpha is None else "RGBA").save(path, optimize=True)


def viewport_icon(F, alpha, px):
    """What a launcher shows: the 72 dp middle of the 108 dp icon, px wide."""
    canvas = int(round(px * 108 / 72))
    bg = background(canvas)
    fg, a = place(F, alpha, canvas, ART_DP)
    full = bg * (1 - a[..., None]) + fg * a[..., None]
    o = (canvas - px) // 2
    return full[o:o + px, o:o + px]


# ---------------------------------------------------------------- outputs
def launcher_foregrounds(F, alpha):
    for folder, px in [("mipmap-mdpi", 108), ("mipmap-hdpi", 162), ("mipmap-xhdpi", 216),
                       ("mipmap-xxhdpi", 324), ("mipmap-xxxhdpi", 432)]:
        fg, a = place(F, alpha, px, ART_DP)
        save(fg, os.path.join(RES, folder, "ic_launcher_foreground.png"), a)


def vectors(alpha):
    import cv2
    S = alpha.shape[0]
    m = ndi.binary_fill_holes(ndi.binary_opening(ndi.gaussian_filter(alpha, 1.5) > 0.6, iterations=2))
    lab, n = ndi.label(m)
    sizes = ndi.sum(m, lab, range(1, n + 1))
    ring = int(np.argmax(sizes)) + 1               # ring + tail is the largest part
    yy, xx = np.mgrid[0:S, 0:S]
    cx, cy = S * 0.4705, S * 0.4609               # ring centre, fitted on the master
    ang = np.degrees(np.arctan2(-(yy - cy), xx - cx))
    # The ring fades out behind the waves at the upper right: cut it cleanly there.
    cut = (lab == ring) & (ang > -25) & (ang < 62) & (np.hypot(xx - cx, yy - cy) > S * 0.24)
    glyph = ndi.binary_opening(m & ~cut, iterations=1)
    contours, _ = cv2.findContours((glyph * 255).astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)

    def num(v):
        s = f"{v:.2f}".rstrip("0").rstrip(".")
        return "0" if s in ("-0", "") else s

    paths = []
    for c in contours:
        if cv2.contourArea(c) < 800:
            continue
        q = cv2.approxPolyDP(c, 1.6, True)[:, 0, :].astype(float) * (GLYPH_DP / S) + (108 - GLYPH_DP) / 2
        paths.append("M" + num(q[0][0]) + "," + num(q[0][1]) + "".join(f"L{num(x)},{num(y)}" for x, y in q[1:]) + "Z")
    with open(os.path.join(RES, "drawable", "ic_launcher_monochrome.xml"), "w") as fh:
        fh.write(MONO_HEADER + "".join(
            f'    <path\n        android:fillColor="#FFFFFFFF"\n        android:pathData="{d}" />\n' for d in paths) + "</vector>\n")

    def gpath(spec):
        items = "".join(f'                <item android:offset="{o}" android:color="{c}" />\n' for o, c in spec["stops"])
        return (f'    <path android:pathData="M0,0h108v108h-108z">\n        <aapt:attr name="android:fillColor">\n'
                f'            <gradient\n                android:type="radial"\n                android:centerX="{spec["cx"]}"\n'
                f'                android:centerY="{spec["cy"]}"\n                android:gradientRadius="{spec["r"]}">\n'
                f'{items}            </gradient>\n        </aapt:attr>\n    </path>\n')
    with open(os.path.join(RES, "drawable", "ic_launcher_background.xml"), "w") as fh:
        fh.write(BG_HEADER + gpath(BG["base"]) + gpath(BG["glow_tl"]) + gpath(BG["glow_br"]) + "</vector>\n")


def feature_graphic(F, alpha):
    W, H = 1024, 500
    yy, xx = np.mgrid[0:H, 0:W].astype(float)

    def glow(img, cx, cy, r, color, strength):
        d = np.clip(1 - np.hypot(xx - cx, yy - cy) / r, 0, 1) ** 2 * strength
        return img * (1 - d[..., None]) + (np.array(color) / 255.0) * d[..., None]
    t = np.clip(xx / W, 0, 1) ** 1.2
    img = np.array([6, 8, 52]) / 255.0 * (1 - t[..., None]) + np.array([24, 16, 92]) / 255.0 * t[..., None]
    img = glow(img, 120, 40, 420, (110, 44, 180), 0.55)
    img = glow(img, 980, 470, 380, (27, 90, 200), 0.45)
    img = glow(img, 250, 260, 230, (40, 20, 120), 0.35)
    canvas = Image.fromarray((np.clip(img, 0, 1) * 255 + 0.5).astype(np.uint8)).convert("RGBA")
    art = Image.fromarray((np.dstack([F, alpha]) * 255 + 0.5).astype(np.uint8), "RGBA").resize((400, 400), Image.LANCZOS)
    canvas.alpha_composite(art, (40, (H - 400) // 2 + 6))
    # Poppins (SIL OFL 1.1, fonts.google.com/specimen/Poppins); set POPPINS_DIR
    # to the folder holding Poppins-Bold/Medium/Regular.ttf if it isn't here.
    fonts = os.environ.get("POPPINS_DIR", "/usr/share/fonts/truetype/google-fonts").rstrip("/\\") + "/"
    d = ImageDraw.Draw(canvas)
    d.text((470, 110), "QVoice", font=ImageFont.truetype(fonts + "Poppins-Bold.ttf", 112), fill=(255, 255, 255))
    d.text((474, 250), "Offline text to speech", font=ImageFont.truetype(fonts + "Poppins-Medium.ttf", 40), fill=(205, 198, 255))
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    small = ImageFont.truetype(fonts + "Poppins-Regular.ttf", 25)
    x = 474
    for label in ["Natural voices", "Private", "No ads"]:
        w = ld.textlength(label, font=small)
        ld.rounded_rectangle((x, 332, x + w + 32, 378), radius=23, fill=(255, 255, 255, 22), outline=(159, 232, 247, 170), width=2)
        ld.text((x + 16, 339), label, font=small, fill=(200, 243, 252, 255))
        x += w + 46
    canvas.alpha_composite(layer)
    os.makedirs(STORE, exist_ok=True)
    canvas.convert("RGB").save(os.path.join(STORE, "feature-graphic-1024x500.png"), optimize=True)


MONO_HEADER = '''<?xml version="1.0" encoding="utf-8"?>
<!--
  Themed-icon layer (Android 13+): the QVoice mark's solid shapes only - the
  Q ring (cut cleanly where the original fades behind the waves), its tail,
  the speaker and three waves. Generated by tools/brand/make_brand_assets.py
  from the owner's artwork; the launcher tints it, so the colour is ignored.
  Also the basis for a future notification icon.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
'''

BG_HEADER = '''<?xml version="1.0" encoding="utf-8"?>
<!--
  Launcher icon background: the deep navy of the QVoice logo, darkest behind
  the mark, with the violet light at the top left and the blue light at the
  bottom right of the original artwork. Three radial gradients on the 108 dp
  canvas. Generated by tools/brand/make_brand_assets.py (same formula as the
  Play Store icon it renders).
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
'''

if __name__ == "__main__":
    F, alpha = extract(load_master())
    launcher_foregrounds(F, alpha)
    vectors(alpha)
    save(viewport_icon(F, alpha, 288), os.path.join(RES, "drawable-nodpi", "qvoice_logo.png"))
    save(viewport_icon(F, alpha, 512), os.path.join(STORE, "play-icon-512.png"))
    feature_graphic(F, alpha)
    print("brand assets written")
