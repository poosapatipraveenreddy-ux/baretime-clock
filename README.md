# BareTime

One repo, three parts:

| Folder | What it is |
|---|---|
| `web/` | The clock designer website (also the designer inside the Android app) |
| `android/` | Android app: the designer in a WebView + the home-screen widget |
| `tools/`, `.github/workflows/` | Font download, update manifest, and the two GitHub builds |

## One-time setup (about 15 minutes, no installs on your computer)

1. **Create a GitHub repo** and upload everything in this folder (keep the folder structure).
2. **Turn on the website:** Settings → Pages → Source: **GitHub Actions**. Then Actions → *Publish website* → Run workflow.
   Your site will be `https://<you>.github.io/<repo>/`.
3. **Make a signing key (once, keep it safe).** On any computer with Java:
   ```
   keytool -genkeypair -v -keystore baretime.jks -alias baretime -keyalg RSA -keysize 2048 -validity 10000
   base64 -w0 baretime.jks      # macOS: base64 -i baretime.jks
   ```
   In the repo: Settings → Secrets and variables → Actions → add four secrets:
   `KEYSTORE_B64` (the base64 text), `KEYSTORE_PASSWORD`, `KEY_ALIAS` (baretime), `KEY_PASSWORD`.
   Never lose this key: without it, nobody can update the app over an older install.
   (You can skip this for a first test; the APK is then signed with a throwaway key.)
4. **Build the app:** Actions → *Build Android APK* → Run workflow. Download `BareTime-apk` from the run page, unzip, copy `BareTime.apk` to your phone and install it (allow "install unknown apps" for your browser or file manager).
5. **Use it:** open BareTime → design → **Apply to widget** → long-press the home screen → Widgets → BareTime.

## Updating later

| You change | Do this | Users |
|---|---|---|
| The designer (anything in `web/`) | Edit the files, bump `VERSION` in `web/sw.js`, commit | Website updates by itself. The app downloads the new files in the background (verified by hash) and uses them next time it opens. |
| Widget drawing or app code | Commit, then tag a release: `v1.1.0` (Releases → new tag) | The app shows "New version" with a download link |

If a new website version starts needing a new app feature, raise `MIN_BRIDGE` in `web/android-bridge.js` and `BRIDGE_LEVEL` in `android/app/build.gradle.kts`; older apps then keep their current designer instead of breaking.
