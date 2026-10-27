# BareTime — custom clock website + installable app

One static website. No backend, no database, no paid APIs.
Open it in a browser, or install it as an app ("Add to Home screen" / "Install app") and it works offline.

## What it does
- Three display modes: **Transparent** (numbers only), **Color** (solid or gradient card), **Glass**
- 26 fonts, weight, size (20–300%), spacing, line height, alignment, position
- One-line or stacked layout, 12/24-hour, seconds, AM/PM, blinking colon, time zones
- Date with 5 formats, weekday toggle, own size/opacity
- Text color, gradient text, shadow, glow, outline
- Wallpapers: 9 built-in + your own photo (stays on your device), dim/blur, **Auto contrast**
- 12 presets, saved themes, **share link**, **embed code**, export/import file
- Full screen, keep-screen-on, installable, offline, self-updating

## Put it online (free, pick one)
The folder is plain files, so any static host works. PWAs need **https**, which all of these give you.

**GitHub Pages:** create a repo, upload this folder's contents, Settings → Pages → deploy from `main` / root.
**Netlify / Vercel / Cloudflare Pages:** drag the folder in (or connect the repo). No build command; output directory is the folder itself.

## How updates reach users (no server code)
1. Change any file (`app.js`, `styles.css`, `index.html`, …).
2. Open `sw.js` and bump `const VERSION = '1.0.0'` → `'1.0.1'`.
3. Re-upload / push. Done.

Everyone who has it open or installed gets an **"Update now"** message within minutes
(or when they next open the app) and the new version loads in one tap.
Forgetting step 2 means installed copies keep the old files, so always bump it.

## Files
| File | Purpose |
|---|---|
| `index.html`, `styles.css`, `app.js` | The app |
| `sw.js` | Offline caching + update mechanism |
| `manifest.webmanifest`, `icons/` | Makes it installable |
| `THEME-SPEC.md` | Portable theme format (for a future Android widget) |

## Good to know
- **Fonts** come from Google Fonts the first time you're online, then are cached for offline use. If the very first visit is offline, system fonts are used instead. To make fonts 100% self-contained, download the font files and host them in a `fonts/` folder (add them to `ASSETS` in `sw.js`).
- **Settings are per browser.** Phone and laptop don't sync automatically; move a design with the share link or export file.
- **Seconds** tick every second only while the page is open; the minutes-only mode uses almost no battery.
- iPhone/iPad don't support the full-screen button or keep-screen-on in every version; installing to the Home Screen gives the best result.
- A website can't draw a clock on the Android *home screen* itself. That needs the native widget (see `THEME-SPEC.md`), which can read the same themes.
- "BareTime" is a working name — check it for conflicts before launching publicly.

## Try it on your computer
```
cd baretime
python3 -m http.server 8000
# open http://localhost:8000
```
