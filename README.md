# BareTime

A clock you design once and use everywhere: **website**, **installable app**, and a real **Android home-screen widget**.
Works offline, follows the phone's own time. **No servers, no backend, no paid services** — only free static hosting on GitHub.

```
baretime/
├─ web/        The website + designer (also bundled inside the Android app)
├─ android/    The Android app: designer + home-screen widget
├─ tools/      Tiny helper used by the publish step
├─ docs/       THEME-SPEC.md (design file format), WEBSITE.md
└─ .github/workflows/   Free automatic builds (APK + website)
```

## How the pieces connect
1. You design the clock in the **web designer** (website) or in the **same designer inside the Android app**.
2. In the app, tap **Apply to widget**. The design is saved on the phone and drawn by a native widget.
3. The widget redraws every minute from the phone's own clock → **fully offline**.
4. Website → app: the website's *Share & move designs* tab has **Open in BareTime Android app** (opens the app with that design) and a share link / export file you can paste into the app.

## One-time setup (about 15 minutes, all free)
1. Create a new GitHub repository (e.g. `baretime`) and upload everything in this folder to it (branch `main`).
2. Repo **Settings → Pages → Build and deployment → Source: GitHub Actions**. Your website appears at `https://<you>.github.io/baretime/`.
3. Repo **Actions** tab → **Build Android APK** → *Run workflow*. After a few minutes, open the run and download the **BareTime-apk** file. (If the first run shows an error, copy the error text to me and I will fix it — I could not run the Android build myself.)
4. Install the APK on your phone (allow "install unknown apps" for your browser/files app when asked).
5. Open BareTime → pick a design → **Apply to widget** → **Add widget**. Done.

For a downloadable link to share with friends, create a release: repo → **Releases → Draft a new release → tag `v1.0.0` → Publish**. The build attaches the APK automatically.

## Updating whenever you want
| You change | What to do | What users see |
|---|---|---|
| Designer, presets, look (`web/`) | Edit the files, bump `VERSION` in `web/sw.js`, commit to `main` | Website: "Update now" message. **Android app: gets the new designer by itself next time it is opened online — no new APK.** |
| Widget drawing / new native features (`android/`) | Commit, then publish a release with a new tag (`v1.0.1`) | App shows "new version available" when you press *Check for updates* |

Bump **one number** (`VERSION` in `web/sw.js`) for any designer change. Forgetting it means installed copies keep the old files.

## Honest limits
- **Not a live link.** Changing a slider on the website does not instantly move your phone's widget; you apply it with a tap.
- **Glass** can't blur the wallpaper on a widget; it becomes a soft tint. Transparent and Color match the preview closely.
- **Widget size = clock size.** The widget scales the clock to fit; resize the widget on your home screen to make it bigger or smaller. Seconds are not shown on the widget (it updates once a minute to save battery).
- **Position/offset sliders** and wallpaper settings don't apply to the widget (your real wallpaper shows behind it).
- **Exact-minute ticking** uses an Android alarm permission. If a phone brand denies it, the widget may lag by a minute or two. Some brands (Xiaomi, Oppo, Vivo, Samsung "sleeping apps") also kill background alarms — set BareTime to *no battery restrictions* if the widget stalls.
- **Signing key.** `android/keystore/baretime.jks` is committed so every build is signed with the same key (phones only accept updates signed by the same key). Because it is public in your repo, that is fine for personal use and friends, but **before a Play Store release** make a private key and pass it through the `BT_*` secrets in `build-apk.yml`.
- **Play Store:** exact alarms (`USE_EXACT_ALARM`) are restricted by Google policy to alarm/clock-type apps; check the current policy before submitting.
- **Trust:** the app loads designer updates from *your* GitHub Pages site, so whoever controls that repo controls the designer code. Keep the repo's access limited to you.
- **Not tested on a real phone yet.** The web designer was tested in a browser (including the app mode with a simulated bridge); the Android code was written carefully but not compiled here. Expect a round of small fixes after the first build.

## Change the update address
The build fills it in automatically from your GitHub username and repo name. To set it by hand: `android/app/build.gradle` → `updateBaseUrl`.

## Try the website locally
```
cd web
python3 -m http.server 8000     # open http://localhost:8000
```
