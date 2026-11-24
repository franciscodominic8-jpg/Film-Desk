# Film Desk — Fujifilm-style photo editor for Android

Kotlin + Jetpack Compose app that recreates Fujifilm's in-camera settings on any photo.

**Runs on:** Android 7.0 (API 24) and newer — about 97% of Android devices — phones, foldables
(e.g. Galaxy Z Fold), tablets, portrait and landscape. One APK for all CPU types.

## Features

| Screen | What it does |
|---|---|
| **Library** | Your gallery (newest first) + "Pick" via the system photo picker (works without any permission). Also opens photos shared from other apps or "Edit with Film Desk". |
| **Film** | 20 film simulations with live thumbnails of *your* photo: Provia, Velvia, Astia, Classic Chrome, Reala Ace, Pro Neg Hi/Std, Classic Neg, Nostalgic Neg, Eterna, Eterna Bleach Bypass, Acros (+Ye/+R/+G), Monochrome (+Ye/+R/+G), Sepia. Strength slider. Hold the photo to compare with the original. |
| **Q Menu** | Exposure (⅓ stops), Grain (Weak/Strong × Small/Large), Color Chrome, Color Chrome FX Blue, White Balance (Auto, Daylight, Shade, Fluorescent 1–3, Incandescent, Underwater, Kelvin 2500–10000K), WB Shift R/B ±9, Dynamic Range DR100/200/400, D-Range Priority, Highlight/Shadow −2…+4, Color, Sharpness, High ISO NR ±4, Clarity ±5, Mono WC/MG, Smooth Skin. |
| **Pro** | Contrast, Highlights, Shadows, Whites, Blacks, Fade, Temperature, Tint, Vibrance, Saturation, 8-band HSL, Vignette. |
| **Crop** | Original, 1:1, 4:5, 3:2, 2:3, 16:9, 9:16, 65:24; rotate; flip. |
| **Recipes** | Save looks, apply to any photo, share as text, paste to import. 6 starter recipes. |
| **Export** | JPEG (quality) or PNG, full-res or 4096/2048/1080 px, optional film border with settings caption, recipe embedded in EXIF. Saves to *Pictures/FilmDesk* or shares. |

Undo/redo, adaptive layout (side panel when the screen is ≥ 600 dp wide), dark camera-style UI.

## Build the APK

**Option A — GitHub (no install needed):** push this folder to a GitHub repository. The workflow in
`.github/workflows/build-apk.yml` builds automatically. Open the **Actions** tab → latest run →
download **FilmDesk-apk**. Install `app-release.apk` on your phone (allow "Install unknown apps").

**Option B — Android Studio:** File › Open › this folder. Wait for Gradle sync, then
Build › Build APK(s), or press Run with your phone connected.

**Option C — command line** (Android SDK + JDK 17 installed):
```
./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

The release build is signed with the debug key so it installs right away. Create your own keystore
before publishing to Google Play.

## How the engine works

`engine/ImageEngine.kt` is a pure-Kotlin, multi-core pipeline (no GPU dependency, so it behaves the same
on every device): geometry → linear-light white balance & exposure → dynamic-range roll-off → film
simulation → Fuji highlight/shadow tone → pro tone → saturation / Color Chrome / HSL → sharpness, NR,
clarity, smooth skin → grain & vignette. Preview renders at 1440 px; export renders at full resolution,
capped by the device's available memory so low-end phones don't crash.

## Known limits / next steps

- Film simulations are hand-tuned approximations, not Fujifilm's algorithms. For closer matches,
  measure 3D LUTs from real camera JPEGs and swap them into `model/FilmSim.kt`.
- RAW (.RAF) files aren't decoded yet — needs LibRaw via the NDK.
- Reading the recipe stored inside Fujifilm camera JPEGs (maker notes) isn't implemented yet.
- Crops are centred; free crop handles, masks and healing are not built yet.
- "Provia", "Velvia", "Classic Chrome" etc. are Fujifilm trademarks — rename them before publishing.

## License

Open source under the [MIT License](LICENSE): you may use, copy, modify and share this code, including in your own projects, as long as you keep the copyright notice.

"Fujifilm", "Provia", "Velvia", "Classic Chrome" and the other film simulation names are trademarks of FUJIFILM Corporation and are not covered by this license. This project is not affiliated with Fujifilm.

The signing key in `app/` is only for the owner's own builds; anyone publishing their own version should sign it with their own key.
