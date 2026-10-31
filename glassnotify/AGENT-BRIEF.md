# Brief for your coding agent (copy this whole file to it)

**Project:** GlassNotify (placeholder name). An Android app that shows important notifications as a floating
frosted-glass card instead of the plain system pop-up.

**Your job:** keep building this project, phase by phase, and keep it building as an APK.
The user is a founder, not a developer: keep code simple, commented, and in the few files listed below.

## Where things live (all paths inside `glassnotify/app/src/main/java/com/genservices/glassnotify/`)
| File | What it does | Change it when you want to… |
|---|---|---|
| `NotifService.kt` | Receives every notification, turns it into a `Card` | add **rules/filters** (`shouldShow`), merge chats, read more fields |
| `OverlayManager.kt` | One reusable floating card: look, animation, drag, swipe, tap | change the **look** (`build`, `applyLook`), add buttons to the card |
| `MainActivity.kt` | Setup screen + settings | add a **setting** (also add a line in `Prefs.kt`) |
| `Prefs.kt` | Saved settings | add a new setting |
| `Card.kt` | Data for one card | add a field (e.g. OTP, reply action) |

Build files: `glassnotify/app/build.gradle`. Automatic build: `.github/workflows/build-glassnotify.yml`
(push to `main` → Actions → artifact `GlassNotify-apk`).

## How to build / check
- Locally: `cd glassnotify && gradle assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
- On GitHub: push, open the Actions tab, download the APK artifact.
- If the build fails, fix it and push again. First build has never been run — expect small compile fixes.

## What exists now (phase 1 + part of phase 2)
Listener service, setup screen (3 permissions + restricted-settings help), reusable overlay card with contact
photo / app icon / sender / preview, auto-hide timer, tap to open the app, drag, swipe away, top/bottom position,
repeated messages from one sender update one card ("· 3 new"), blocked-apps list, Android 12+ blur-behind with a
darker translucent fallback, "send test card" button.

## Build next, in this order (one at a time; build + test after each)
1. **Phase 2 polish:** corner snap / small "pill" mode, per-app accent colors, light/dark/glass themes.
2. **Phase 3:** history inbox (Room or a simple file), search, recover dismissed, pin/star; per-app priority (VIP/normal/silent); quiet hours; keyword allow/block.
3. **Phase 4:** per-app/contact sound + vibration, reply from card (`Notification.Action` + `RemoteInput`), mark read, snooze, copy OTP (regex on text).
4. **Phase 5:** special cards for delivery/payment/OTP; AI summary and forwarding only if the user asks (they need internet; add the INTERNET permission only then and tell the user why).

## Rules
- No third-party libraries unless truly needed (Room is acceptable for history). No server, no accounts.
- **Privacy:** never log or send notification text. The manifest has no INTERNET permission on purpose.
- Never crash the listener (keep the try/catch in `onNotificationPosted`), or Android stops sending notifications.
- Keep one reused overlay view; do database writes off the main thread.
- Handle both WhatsApp (`com.whatsapp`) and WhatsApp Business (`com.whatsapp.w4b`).
- Bump nothing by hand for builds: the workflow sets the version from the run number.

## Known risks to check on a real phone
- Android 13+ may grey out Notification access for sideloaded APKs → "Allow restricted settings" (the setup screen explains it).
- Blur-behind blurs a **rectangle** the size of the card, so rounded corners may show a slightly blurred corner. If ugly, set the corner radius small or turn blur off by default.
- Some phones hide notification text on a secure lock screen; some brands kill background services.
- Google Play: `SYSTEM_ALERT_WINDOW`, `QUERY_ALL_PACKAGES` and notification access get extra review (needs a privacy policy). Sideloading the APK is fine for testing.
- Tapping a card uses the notification's own PendingIntent; some apps refuse to start from the background.
