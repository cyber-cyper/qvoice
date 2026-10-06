# QVoice brand assets

`logo-master.png` is the owner's logo (1254 px): a glowing Q whose ring
holds a speaker and three sound waves, on a deep navy rounded square.
Everything else is generated from it:

```
pip install numpy scipy pillow opencv-python-headless
python tools/brand/make_brand_assets.py
```

| Output | Used for |
|---|---|
| `app/src/main/res/mipmap-*/ic_launcher_foreground.png` | adaptive launcher icon, foreground layer (108/162/216/324/432 px) |
| `app/src/main/res/drawable/ic_launcher_background.xml` | adaptive launcher icon, background layer (vector gradients) |
| `app/src/main/res/drawable/ic_launcher_monochrome.xml` | Android 13+ themed icon (vector glyph) |
| `app/src/main/res/drawable-nodpi/qvoice_logo.png` | the logo inside the app (Home bar, About) |
| `store/play-icon-512.png` | Play Store icon |
| `store/feature-graphic-1024x500.png` | Play Store feature graphic (text in Poppins, SIL OFL) |

Run it only when the logo changes; the outputs are committed. The feature
graphic's text is set in Poppins (SIL OFL): install it, or point
`POPPINS_DIR` at a folder with Poppins-Bold/Medium/Regular.ttf.

## Decisions

- **A real adaptive icon, not the flat picture.** Launchers cut icons into
  circles, squircles or rounded squares, and themed icons need a separate
  glyph. So the artwork is lifted off its background (colour-to-alpha
  against a fitted background field: over the original background it
  reproduces the picture exactly) and placed on a designed gradient in the
  same navy/violet/blue tones.
- **Size: the owner's square fills 68 of the 72 dp a mask shows.** The Q
  ring stays inside Android's 66 dp safe zone and the tail's tip stays inside
  even a circle mask.
- **The original's rim light and corner haze are dropped**: a launcher mask
  would cut them into a visible edge.
- **Themed glyph**: the solid shapes only, with the ring cut cleanly where
  the original fades behind the waves (a traced fade looked ragged).
- **Brand colours in the app** (ui/Theme.kt) come from the same logo:
  indigo, violet, cyan, navy.
