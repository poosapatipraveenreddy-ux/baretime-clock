# GlassNotify (placeholder name)

Important notifications as a floating frosted-glass card, with a few simple controls.
It lives in its own folder, so it never touches BareTime. Same free GitHub build.

## The easy way (3 steps)
1. **Put it on GitHub.** Upload the whole project (this folder is `glassnotify/` inside it) to your repository, branch `main`.
2. **Get the APK.** GitHub → **Actions** → **Build GlassNotify APK** → open the finished run → download **GlassNotify-apk**. Unzip and install the `.apk` on your phone.
3. **Hand it to your agent.** Give your coding agent the file `glassnotify/AGENT-BRIEF.md`. It says where each kind of change goes and what to build next. Whenever the agent pushes a change, GitHub builds a fresh APK by itself.

## First run on the phone
Open GlassNotify and tick the 3 steps: **Notification access**, **Display over other apps**, **Battery not restricted**.
Then press **Send a test card**. If it appears, real notifications will too.
(If the Notification access switch is greyed out: app info → ⋮ → *Allow restricted settings*.)

## What works today
Glass card with photo, app icon, sender and message · auto-hide · tap to open the app · drag · swipe away ·
top or bottom position · repeated messages merge into one card · block apps · Android 12+ blur with a readable fallback.

## Not built yet (the agent does these in order)
History inbox, per-app rules and quiet hours, custom sounds, reply from the card, OTP copy, special cards, AI summaries.
Full list and order are in `AGENT-BRIEF.md`.

## Honest notes
- Not compiled or tested on a real phone yet — the first build may need a small fix.
- Internet permission is present ONLY for optional AI summaries when you tap "Summarize" (no data is sent automatically).
- Play Store needs extra review for this kind of app (overlay + notification access). Installing the APK yourself is fine.
