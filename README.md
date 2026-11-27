# Lumora Film

A Fujifilm-style photo editor for Android. Give any photo the look of Fujifilm's film simulations and in-camera settings, then fine-tune it with pro tools, curves and crop.

Made for the Galaxy Z Fold, and works on any Android 7.0+ phone or tablet, folded or unfolded, portrait or landscape.

---

## Contents

1. [Install and update](#1-install-and-update)
2. [Studio — your photos](#2-studio--your-photos)
3. [The editor](#3-the-editor)
4. [Film — film simulations](#4-film--film-simulations)
5. [Q Menu — camera settings](#5-q-menu--camera-settings)
6. [Pro — fine adjustments](#6-pro--fine-adjustments)
7. [Curve](#7-curve)
8. [Crop](#8-crop)
9. [Copy, paste and recipes](#9-copy-paste-and-recipes)
10. [Export](#10-export)
11. [Tips and gestures](#11-tips-and-gestures)
12. [FAQ](#12-faq)
13. [For developers](#13-for-developers)

---

## 1. Install and update

1. On your phone, open **[LumoraFilm.apk](https://github.com/julian-ymo/Film-Desk/releases/latest/download/LumoraFilm.apk)** (always the newest build).
2. If Android asks, allow your browser or Files app to **install unknown apps**.
3. Tap **Install**.

**Updating:** download the new APK and install it the same way. It installs over the old app and **keeps your photos, edits and recipes**. No need to uninstall.

---

## 2. Studio — your photos

The Studio is the start screen: a grid of the photos you've imported, each with its own edits.

| | |
|---|---|
| **＋ (amber button)** | Import photos. Pick from your gallery, or from other apps (Google Photos, Files…). You can choose many at once. |
| **All / Edited / Not edited** | Filter the grid. |
| **Code on a photo** (CC, CN, ACR…) | The photo is edited; the code is its film simulation. |
| **Tap a photo** | Open it in the editor. |
| **Long-press a photo** | Start selecting (or **⋯ → Select photos**). |
| **⋯ → Recipes** | Your saved looks. |

**When photos are selected**, the bottom bar shows:

- **Copy** — copy the edits of one photo.
- **Paste** — paste copied edits onto all selected photos (you choose which parts).
- **Recipe** — apply a saved recipe to all selected photos.
- **Export** — save all selected photos at once, each with its own edits.
- **Reset** — remove the edits.
- **Remove** — take them out of the Studio. Your original photos are never deleted.

> Lumora Film keeps a private copy of each imported photo, so edits still work even if the original is moved.
> You can also open a photo straight from another app with **Share → Lumora Film** or **Edit with → Lumora Film**.

---

## 3. The editor

```
 ‹                 ↶  ↷  ⋯   Export
┌──────────────────────────────────┐
│             photo                │   hold the photo = compare with the original
└──────────────────────────────────┘
        tools of the current tab
   Film    Q Menu    Pro    Crop          ← tabs
```

- **‹** — back to the Studio (your edits are saved automatically).
- **↶ ↷** — undo / redo.
- **⋯** — Copy edits, Paste edits, Apply a recipe, Save as recipe.
- **Export** — save or share the photo.
- **Hold the photo** to see the original; let go to see your edit.

On an unfolded Fold or a tablet, the tools appear in a panel on the right instead of under the photo.

**Option panels.** Many tools open a small panel with:

- **✕** — cancel what you changed in this panel
- **Reset** — set this tool back to its default
- **✓ / Done** — keep the change (one undo step)

The phone's **back button** closes an open panel and keeps your change.

---

## 4. Film — film simulations

The **Film** tab shows a strip of your photo in every simulation. The selected one has a white border, and the header shows its full name and a short description.

| Code | Simulation | Code | Simulation |
|---|---|---|---|
| PRO | Provia / Standard | ET | Eterna / Cinema |
| VEL | Velvia / Vivid | BB | Eterna Bleach Bypass |
| AST | Astia / Soft | ACR | Acros |
| CC | Classic Chrome | A+Y / A+R / A+G | Acros + Yellow / Red / Green filter |
| RA | Reala Ace | MON | Monochrome |
| NH | Pro Neg. Hi | M+Y / M+R / M+G | Monochrome + Yellow / Red / Green filter |
| NS | Pro Neg. Std | SEP | Sepia |
| CN | Classic Neg. | | |
| NN | Nostalgic Neg. | | |

- **Tap** a simulation to use it.
- **Tap the selected one again** to set its **strength** (0–100%).

---

## 5. Q Menu — camera settings

Like the Q button on a Fujifilm camera: every setting is a round dial showing its current value. **Orange = changed** from default. **Grey = not active right now** (for example, Mono WC/MG on a colour film).

Tap a dial and its box opens right under that row. Change the value, then tap **Done** (or tap the dial again). **Reset** sets that setting back to default.

| Dial | What it does |
|---|---|
| **Film Sim** | Choose the simulation and its strength. |
| **Exposure** | −3 to +3 EV in ⅓ steps. |
| **Grain** | Off, Weak/Strong × Small/Large. |
| **Color Chrome** | Deeper, richer tones in strongly coloured areas (Off / Weak / Strong). |
| **CC FX Blue** | Deeper blues, e.g. skies (Off / Weak / Strong). |
| **White Bal.** | As Shot, Auto, Daylight, Shade, Fluorescent 1–3, Incandescent, Underwater, or Kelvin (2500–10000K). |
| **WB Shift** | Drag the dot on the grid: Red ↔ Cyan and Blue ↔ Yellow, ±9. |
| **Dyn. Range** | DR100 / DR200 / DR400 — softer highlights, keeps bright areas from blowing out. |
| **D-Range Pri.** | Off / Weak / Strong / Auto. When on, it sets Dynamic Range, Highlight and Shadow for you (they turn grey). |
| **Highlight** / **Shadow** | Tone curve, −2 (soft) to +4 (hard), ½ steps. |
| **Color** | Saturation, −4 to +4. |
| **Sharpness** | −4 to +4. |
| **High ISO NR** | Noise reduction, −4 to +4. |
| **Clarity** | Mid-tone contrast, −5 to +5. |
| **Mono WC/MG** | Black & white tone: warm ↔ cool and magenta ↔ green. B&W films only. |
| **Smooth Skin** | Softens skin texture (Off / Weak / Strong). |

**Sliders:** drag sideways to change the value. Use **‹ ›** beside the number for one step at a time, and **double-tap** the slider to go back to the default.

**Reset all** (top right of the Q Menu) resets every camera setting.

---

## 6. Pro — fine adjustments

Round tools in three groups. An **orange dot** means the tool is changed.

| Group | Tools |
|---|---|
| **Light** | Contrast, Highlights, Shadows, Whites, Blacks, Fade |
| **Colour** | Temperature, Tint, Vibrance, Saturation, **HSL** |
| **Advanced & effects** | **Curve**, Vignette, **Glow** |

- Most tools open one big slider. Drag sideways, or use **‹ ›** for single steps; double-tap the slider to reset.
- **HSL** — pick one of 8 colours (red, orange, yellow, green, aqua, blue, purple, magenta), then change its Hue, Saturation and Luminance.
- **Glow** — choose a style:
  - **Glow:** soft bloom around bright areas.
  - **Halation:** red-orange glow around highlights, like CineStill film.
  - **Mist:** a soft haze over the whole photo.

  Then set Amount, Threshold (how bright an area must be), Size and Warmth.
- **Reset all** (top right) resets every Pro adjustment.

---

## 7. Curve

Tap **Curve** in the Pro tab for the full-screen curve editor, styled after DaVinci Resolve.

**The graph**
- Shows your photo's red, green, blue and brightness (Y) histograms behind the curves.
- **Tap** to add a point, **drag** to move it, **double-tap** a point to delete it.

**Edit row**
- **Y R G B** — choose which curve to edit (Y = brightness).
- **Link** 🔗 — on: the Y curve moves R, G and B together. Off: Y changes brightness only. Picking R, G or B turns the link off.
- **Eyedropper** — tap the photo to see where that colour sits on each curve.
- **⋮** — copy a curve to other channels, reset one curve, or reset everything.
- **Four coloured sliders** — the strength of each curve (100 = full). You can also type a number in the box.

**Soft Clip** (scroll down on a phone)
- **Low / High** — the darkest black and brightest white.
- **Low Soft / High Soft** — gently roll off crushed shadows and blown highlights.
- **Link** 🔗 on = the same values for R, G and B. Tap **R**, **G** or **B** to set each colour on its own.

Tap **Done** (or back) to return to the editor.

---

## 8. Crop

- **Straighten** — drag the ruler (−45° to +45°). Double-tap it to level. The photo zooms slightly so no corners are empty.
- **Ratio** — Free, Original, 1:1, 4:5, 3:2, 2:3, 16:9, 9:16, 65:24. Use **W/H** to turn it the other way (3:2 ↔ 2:3).
- **Drag the corners and edges** of the frame to crop freely; drag inside the frame to move it. Pinch to zoom.
- **Composition guides** — Off, Thirds, Golden, Spiral, Diagonal, Triangle, Grid, Center. Tap Spiral or Triangle again to turn the guide.
- **Left / Right** turn 90°, **Flip** mirrors, **W/H** swaps width and height, **Reset** undoes all cropping.

---

## 9. Copy, paste and recipes

**Copy edits from one photo to others**
1. In the editor: **⋯ → Copy edits**. In the Studio: select one photo → **Copy**.
2. Select other photos in the Studio → **Paste**, or open a photo → **⋯ → Paste edits**.
3. Choose what to paste:
   - On by default: Film Simulation, Q Menu settings, Pro Light & Effects, Pro Colour & HSL, Curve.
   - Off by default: Detail and Crop & Rotate.

**Recipes** are saved looks you can reuse at any time.
- **Save:** in the editor, **⋯ → Save as recipe** and give it a name.
- **Apply to one photo:** **⋯ → Apply a recipe**. The photo's own crop is kept.
- **Apply to many photos:** select them in the Studio → **Recipe**.
- **Share:** tap the share icon on a recipe to send it as text, for example to a friend.
- **Import:** copy a shared recipe text, then tap **Paste** on the Recipes screen.
- **Delete:** tap the bin icon.

---

## 10. Export

Tap **Export** in the editor, or select photos in the Studio → **Export**.

**Quick presets:**
- **JPG Small:** 2048 px, quality 85.
- **JPG Full:** full size, quality 92.
- **Instagram:** 1080 px short edge, quality 90, screen sharpening.
- **PNG Full:** lossless.
- **+ Save:** saves your current settings as your own preset. Long-press your own preset to delete it.

**Settings** (tap a row to change it):

| Row | Options |
|---|---|
| **File type** | JPEG, PNG, WebP (lossy or lossless), HEIF. Quality 10–100. |
| **Size** | Full, Long edge, Short edge, Megapixels, or %. Photos are never enlarged. |
| **Limit file size** | Keep the file under a size you choose (e.g. 2 MB); the best quality that fits is used. JPEG and lossy WebP. |
| **Output sharpening** | Off, Screen, Matte or Glossy paper × Low / Standard / High. |
| **Metadata** | All, Copyright only, or None. With All, choose camera info, location (GPS) and embedding the recipe. JPEG only. |
| **Save to** | `Pictures/LumoraFilm/` plus an optional folder name, e.g. `Pictures/LumoraFilm/Trip`. |
| **Watermark** | Your text (e.g. © Your Name), position, size, opacity and colour, with a live preview. |

The top of the screen shows the output size and an estimated file size.

- **Save to …** puts the photo in your gallery.
- **Share** sends it straight to another app.

Your original photo is never changed.

---

## 11. Tips and gestures

| Gesture | Where | Does |
|---|---|---|
| Hold | Photo | Show the original |
| Drag sideways | Any slider | Change the value |
| ‹ › | Beside a value | One step down / up |
| Double-tap | Slider | Back to default |
| Tap again | Selected film | Strength |
| Tap again | Open Q dial | Close its box |
| Long-press | Studio photo | Select |
| Long-press | Your export preset | Delete it |
| Pinch | Crop | Zoom |

- Edits save automatically, so it's safe to leave or close the app.
- Undo/redo remembers up to 100 steps per photo while it's open.
- Start with a film simulation, then Q Menu, then Pro. That's the same order the camera works in.

---

## 12. FAQ

**Are the film simulations exactly the same as my Fujifilm camera?**
They're close, but not identical. Fujifilm's processing is secret and runs on RAW sensor data inside the camera. Lumora Film recreates each look on a finished photo. Results are closest when you start from a Fuji JPEG shot in Provia/Standard.

**Can I edit RAW files?**
Yes. Import RAF, DNG, CR2/CR3, NEF, ARW, ORF, RW2 and other RAW files (use **Files** on the Import screen if the photo picker doesn't show them). Lumora Film develops the RAW with the white balance it was shot with, then every tool works on it as usual. Opening a RAW takes a few seconds, and full-size export takes longer than for a JPEG.

**Where are my exported photos?**
In your gallery, in the folder **Pictures/LumoraFilm** (or the subfolder you chose).

**Does Lumora Film change or delete my original photos?**
No. It works on a private copy, and exports are saved as new files.

**I removed a photo from the Studio. Is it gone from my phone?**
No, only from the Studio.

**Do I need to uninstall before updating?**
No. New versions install over the old one and keep everything.

---

## 13. For developers

- **Stack:** Kotlin, Jetpack Compose, Android 7.0+ (API 24), one APK for all devices.
- **Build:** every push to GitHub builds the APK with GitHub Actions and publishes it as a release (`LumoraFilm.apk`). Locally: `./gradlew assembleRelease`.
- **Engine:** `engine/ImageEngine.kt` is a multi-core CPU pipeline. The steps run in this order:
  1. geometry
  2. white balance and exposure in linear light
  3. dynamic-range roll-off
  4. film simulation (OKLab)
  5. tone
  6. curves and soft clip
  7. saturation, Color Chrome, HSL and fade
  8. sharpness, noise reduction, clarity and smooth skin
  9. glow
  10. grain and vignette

  The preview renders at reduced size; exports render at full resolution.
- **Signing:** builds are signed with a fixed key (`app/filmdesk-release.jks`) so updates install over the old app. Anyone building their own version should use their own key.

---

RAW decoding uses [LibRaw](https://www.libraw.org/) under its CDDL 1.0 licence (source in `app/src/main/cpp/LibRaw`).

"Fujifilm", "Provia", "Velvia", "Astia", "Classic Chrome", "Reala Ace", "Eterna", "Acros" and the other film simulation names are trademarks of FUJIFILM Corporation. Lumora Film is not affiliated with or endorsed by Fujifilm.
