# GlassNotify: Complete Product and Build Spec (for the coding agent)

**Project:** GlassNotify (placeholder name). An Android app that shows important notifications as a floating frosted-glass card instead of the plain system pop-up, and gives the user control, history, smart filtering and quick actions.

**Who the user is:** a founder, not a developer. Keep code simple, commented, and in few files. Build one phase at a time, keep it compiling as an APK after every phase.

**Package:** `com.genservices.glassnotify` (folder `glassnotify/` inside the BareTime repo). It must never touch the BareTime app.

---

## 0. How to use this document

1. Read sections 1 to 4 fully before changing anything.
2. Build phases in order (section 6). Each feature has an ID (F-xx) and acceptance checks.
3. After each phase: build, fix compile errors, update the "Status" column in section 6, push.
4. Do not add a feature that is not listed here without asking the user.
5. If something here conflicts with the existing code, keep the existing behavior working and tell the user.

---

## 1. Current state (already built, do not rebuild)

| Area | Status |
|---|---|
| Notification listener service | Done |
| Setup screen (3 permissions + "Allow restricted settings" help) | Done |
| Reusable overlay glass card: contact photo, app icon, sender, message preview | Done |
| Auto-hide timer, tap to open app, drag, swipe away | Done |
| Top / bottom position | Done |
| Repeated messages from one sender merge into one card ("· 3 new") | Done |
| Blocked-apps list | Done |
| Android 12+ blur-behind with darker translucent fallback | Done |
| "Send a test card" button | Done |
| Never compiled or tested on a real phone | Expect small fixes on first build |

Existing files (all under `glassnotify/app/src/main/java/com/genservices/glassnotify/`):

| File | Role |
|---|---|
| `NotifService.kt` | Receives every notification, turns it into a `Card`, holds `shouldShow` |
| `OverlayManager.kt` | One reused floating card: look, animation, drag, swipe, tap |
| `MainActivity.kt` | Setup screen and settings |
| `Prefs.kt` | Saved settings |
| `Card.kt` | Data for one card |

New files are allowed where listed in section 4.

---

## 2. Product vision

A calm, beautiful notification layer.

- A message arrives and a small frosted card floats in with the person's photo and the app's color.
- Important people and apps get the full card. Noise gets a tiny pill or nothing.
- Five messages from one chat become one card that updates.
- The user can reply, copy an OTP, or snooze from the card.
- Everything swiped away is saved in a searchable inbox.
- Each app or contact can have its own sound.
- The user is always in control: profiles, quiet hours, rules.

**Design principles:** fast (card shows within about 100 ms), light on battery, private (no network), simple settings, never crash.

---

## 3. Hard rules (apply to every phase)

### Privacy and safety
- **No INTERNET permission** until Phase 9 (AI features), and only if the user turns that feature on. Tell the user why when you add it.
- Never log, print or send notification text. No analytics, no crash reporters that upload text.
- Sensitive-app option: hide message text for chosen apps (banking, private chats).
- All data stays on the phone. Provide "Delete all history" in settings.

### Stability
- Keep the try/catch in `onNotificationPosted`. If the listener throws, Android stops sending notifications.
- Never block the main thread. Database and file work run on a background thread.
- Never create a new overlay view per notification. Reuse one view and update its content.
- If the overlay permission is missing or the screen is secure, fall back to leaving the normal notification alone.

### Performance (important to the owner)
- Load rules, per-app settings and themes into memory once; update the in-memory copy when settings change. No database or file read per notification.
- Cache app icons, app names, app accent colors, and contact photos in an in-memory LRU cache keyed by package name or sender.
- Use efficient structures: a hash map for per-app rules, a hash set for blocked apps and keywords, a small ring buffer for the recent-card queue.
- No redundant checks: evaluate filters in one ordered pass and stop at the first decisive rule.
- Batch database writes where possible. Use WAL mode if Room is used.

### Code style
- Simple, well-structured, commented Kotlin. No third-party libraries unless truly needed (Room is acceptable). No server, no accounts.
- Handle both WhatsApp (`com.whatsapp`) and WhatsApp Business (`com.whatsapp.w4b`).
- Do not bump version numbers by hand. The workflow sets the version from the run number.
- Every setting needs: a default, a line in `Prefs.kt`, a control in settings, and a test.

### Build
- Local: `cd glassnotify && gradle assembleRelease` gives `app/build/outputs/apk/release/app-release.apk`.
- GitHub: push to `main`, Actions tab, "Build GlassNotify APK", artifact `GlassNotify-apk`.
- If the build fails, fix and push again.

---

## 4. Architecture

```
NotifService (listener)
   -> Normalizer (extract app, sender, text, photo, actions, category, time)
   -> RuleEngine (in-memory: profile, quiet hours, per-app, per-contact, keywords)
   -> Merger/Deduper (same chat -> one card, ignore duplicates and group summaries)
   -> CardQueue (ring buffer, priority order)
   -> OverlayManager (one reused view: glass card / pill / special card)
   -> SoundManager (per app/contact sound + haptics)
   -> HistoryStore (async write, Room or simple file)
```

### New files (add only when the phase needs them)

| File | Purpose | Phase |
|---|---|---|
| `Theme.kt` | Theme definitions (colors, blur, radius, shape) | 2 |
| `Cache.kt` | LRU caches for icons, names, colors, photos | 2 |
| `RuleEngine.kt` | Ordered filters and profiles | 3 |
| `Profiles.kt` | Profile and schedule data | 3 |
| `History.kt` (+ Room entities/DAO) | History store | 4 |
| `InboxActivity.kt` | History inbox UI | 4 |
| `SoundManager.kt` | Sounds, vibration, spoken alerts | 5 |
| `Actions.kt` | Reply, mark read, snooze, copy OTP | 6 |
| `Detectors.kt` | OTP, delivery, payment, calendar, link detection | 7 |
| `Digest.kt` | Batching and summaries | 8 |
| `Lock.kt` | Biometric gate for sensitive cards | 8 |
| `BackupManager.kt` | Export and import settings and themes | 9 |

### Data model (plain description)

**Card**: id, packageName, appName, sender, text, photo (optional), appIcon, accentColor, category, postedTime, mergedCount, actions (reply, mark read), type (normal, pill, otp, delivery, payment, calendar, call), priority (VIP, normal, silent), sensitive (bool).

**AppRule**: packageName, enabled, priority (VIP/normal/silent), accentColor (optional), soundUri (optional), vibrationPattern (optional), hideText (bool), cardType preference (full/pill/off), digestOnly (bool).

**ContactRule**: sender key (package + name), isVIP, soundUri, vibrationPattern, mute, bypassQuietHours (bool).

**Profile**: name, active days, start/end time, allowed apps list or "all except", allowed VIP contacts, sound on/off, card style, auto-activate (schedule).

**KeywordRule**: word or phrase, type (allow = always show, block = never show), scope (all apps or one app).

**HistoryItem**: id, packageName, sender, text, time, starred, dismissedByUser (bool), cardType, conversationKey.

**Settings** (in `Prefs.kt`): position, size, theme, animation, speed, auto-hide seconds, blur on/off, peek-only mode, sensitive-app list, active profile, history retention days, and every toggle in section 5.

---

## 5. Complete feature list (with IDs and acceptance checks)

### A. Look and style

| ID | Feature | Acceptance check |
|---|---|---|
| F-01 | **Corner snap**: card snaps to the nearest corner or edge after drag | Drag and release: card settles at a corner; position is remembered |
| F-02 | **Pill mode**: tiny pill with icon and sender only | Setting switches card to pill; works per app |
| F-03 | **Card sizes**: pill, normal, expanded | All three selectable; expanded shows more text lines |
| F-04 | **Shapes**: bubble, rounded rectangle, circle (photo only) | Selectable in settings; layout does not break |
| F-05 | **Theme packs**: glass, water drop, neon, minimal, paper, dark glass; light/dark/auto | Each theme changes colors, radius, border, highlight; applies live |
| F-06 | **Per-app accent colors**: tint glass by app color (WhatsApp green, etc.) | Auto-pick from app icon; user can override per app |
| F-07 | **Auto-theme from sender photo**: pick a dominant color from the contact photo | Optional toggle; falls back to app color if no photo |
| F-08 | **Animation choices**: drop, slide, pop, fade; speed slider; reduce-motion option | Each animation plays; respects system "remove animations" |
| F-09 | **Edge glow for VIP**: soft colored glow when a VIP message arrives | Only for VIP; can be turned off |
| F-10 | **Landscape and tablet layouts** | Card stays readable and inside the screen in landscape |
| F-11 | **Blur handling**: blur on Android 12+, readable translucent fallback elsewhere; option to turn blur off | No visible blur artifact at rounded corners (use smaller radius or blur off by default if ugly) |

### B. Smarter display

| ID | Feature | Acceptance check |
|---|---|---|
| F-12 | **Do not interrupt**: auto-hide cards when phone is face-down, or during a call, video, or game (when detectable) | Card is queued or shown as a tiny pill instead |
| F-13 | **Fullscreen mode**: shrink to a thin line while fullscreen video plays | Optional; tap expands |
| F-14 | **Call card**: separate look for calls, missed calls, alarms | Call/alarm categories use their own card type |
| F-15 | **Lock screen / always-on display** card where the phone allows | If not possible, fall back silently |
| F-16 | **Peek-only mode**: show only sender name, hide the text until tap | Toggle global and per app |
| F-17 | **Card stacking**: several different chats stack neatly with a count, newest on top | Max stack size is a setting; older ones collapse |
| F-18 | **Merge and dedupe**: same sender merges ("· 3 new"); ignore duplicate or group-summary notifications | Verified with repeated test messages |

### C. Control, rules and profiles

| ID | Feature | Acceptance check |
|---|---|---|
| F-19 | **Per-app priority**: VIP / normal / silent per app | VIP full card + sound, normal card, silent no card (still saved to history) |
| F-20 | **Per-contact rules**: VIP, mute, custom sound, bypass quiet hours | Rules keyed by package + sender name |
| F-21 | **Quiet hours**: time window with allowed exceptions | During the window only VIP/bypass contacts show |
| F-22 | **Profiles**: Work, Sleep, Study, Travel, custom | Switching a profile changes which apps/people show, sound, and card style |
| F-23 | **Profile schedules**: auto-switch by time and day (location optional later) | Profile activates and deactivates at the set times |
| F-24 | **Keyword allow/block lists**: allow always shows, block never shows; per app or global | Keyword in text triggers the rule; rule order is documented |
| F-25 | **Mention-only for groups**: a muted group shows only when your name is mentioned | Name list is configurable |
| F-26 | **Delay and digest**: non-urgent notifications held and shown as a batch (every hour, or at set times) | Digest card lists counts per app; tap opens inbox |
| F-27 | **Emergency bypass**: chosen people always break through | Works during quiet hours and sleep profile |
| F-28 | **Repeat alert**: if a VIP message is unread after N minutes, alert again | N and repeat count are settings; stops once read or dismissed |
| F-29 | **Blocked apps list** (already built) | Keep working; also available inside profiles |

**Rule order (document in code comments):**
1. System/ongoing/group-summary ignore
2. Blocked app
3. Contact bypass or mute
4. Active profile restrictions
5. Quiet hours
6. Keyword block, then keyword allow
7. Per-app priority and card type
8. Do-not-interrupt checks
9. Show, queue, pill, or digest

### D. Quick actions

| ID | Feature | Acceptance check |
|---|---|---|
| F-30 | **Reply from card** using the notification's own `Notification.Action` and `RemoteInput` | Reply sends and shows "sent" on the card |
| F-31 | **Smart replies**: 3 one-tap suggestions (local, rule-based: "OK", "On my way", "Call you later") | Editable list in settings; no internet needed |
| F-32 | **Voice reply** from the card (system speech recognizer) | Optional; asks for microphone permission only when used |
| F-33 | **Mark as read** and **dismiss** | Triggers the notification's own action when available |
| F-34 | **Snooze**: 10 min, tonight, tomorrow, custom | Card returns at the chosen time, even after phone restart |
| F-35 | **Notification to task**: save as a reminder inside the app | Appears in a simple "Reminders" list with a time |
| F-36 | **Tap actions**: copy OTP, open link, call number, open map address detected in the text | Shown as chips on the expanded card |
| F-37 | **Swipe gestures**: left and right do different things (archive, reply, mute) | Gesture mapping is configurable |
| F-38 | **Tap to open** the source app (already built), using the notification's own PendingIntent | If an app refuses to start from background, show a short message instead of failing silently |

### E. Inbox and history

| ID | Feature | Acceptance check |
|---|---|---|
| F-39 | **History inbox**: every notification saved (including silent ones), newest first | Opens fast with 10,000 items (paged list) |
| F-40 | **Search and filter** by app, person, keyword, date | Results as you type |
| F-41 | **Recover dismissed**: list of swiped-away items | Tap restores or reopens as a card |
| F-42 | **Pin / star** important items | Starred items are never auto-deleted |
| F-43 | **Conversation view**: group by chat like a mini thread | Tap a chat to see its messages in order |
| F-44 | **Auto-clean**: delete after 7, 30, 90 days or never | Runs in background once a day |
| F-45 | **Export history** to a file the user chooses | Plain text or CSV via the system file picker |
| F-46 | **Daily / weekly summary** card of what you received | Counts per app and top senders; local only |

### F. Sound and feel

| ID | Feature | Acceptance check |
|---|---|---|
| F-47 | **Per-app and per-contact sound** | Picker for system sounds or a chosen file; preview button |
| F-48 | **Vibration patterns** per app/contact | At least 5 patterns plus off; preview button |
| F-49 | **Day/night volume**: soft sounds at night, normal by day | Set times; follows active profile |
| F-50 | **Spoken announcements** ("Message from Rahul") for driving or hands-free | Off by default; toggle by profile; never read text of sensitive apps |
| F-51 | **Haptics** on drag, snap and dismiss | Light tick; can be turned off |

Note: sounds for your own app's notification channels cannot be changed after creation on Android 8+. Play sounds through `SoundManager` (ringtone/media player on the notification stream), and respect system silent and Do Not Disturb modes.

### G. Special cards (detectors)

| ID | Feature | Acceptance check |
|---|---|---|
| F-52 | **OTP card**: big code, copy button, countdown | Detects 4 to 8 digit codes near words like "OTP", "code", "verification"; handles Indian bank and app formats |
| F-53 | **Delivery card**: order out for delivery, arriving soon, delivered | Pattern-based, for major delivery and shopping apps |
| F-54 | **Payment/bank card**: amount large, debited/credited, merchant | Pattern-based; honors "hide text" privacy setting; amounts never leave the phone |
| F-55 | **Calendar/meeting card**: title, time, countdown | From calendar app notifications |
| F-56 | **Status pills**: battery low, charging, download done | Tiny pills; never full cards |
| F-57 | **Link / phone / address chips** (shares logic with F-36) | Detected with simple patterns |

### H. Privacy and safety

| ID | Feature | Acceptance check |
|---|---|---|
| F-58 | **Hide text for chosen apps** (banking, private chats) | Card shows app and "New message" only |
| F-59 | **Biometric unlock to expand** a sensitive card | Fingerprint/face via the system prompt; fails closed |
| F-60 | **Hide during screen sharing or recording** | Cards queue silently while it is active (where detectable) |
| F-61 | **Wipe data**: delete history, rules and cache | One button with confirmation |
| F-62 | **Lock-screen redaction respect**: if Android redacts text, do not try to work around it | Card shows what the system gives |

### I. Extras

| ID | Feature | Acceptance check |
|---|---|---|
| F-63 | **Backup and restore** settings, rules, and themes to a file | Round trip restores everything |
| F-64 | **Share a theme** as a small file; import from file | Import validates the file and ignores unknown fields |
| F-65 | **Home-screen widget**: today's notification count and last sender | Updates when a new notification arrives |
| F-66 | **BareTime match**: option to match the card style to the user's BareTime clock design | Reads an exported theme file; no code coupling between the two apps |
| F-67 | **Forward chosen alerts** to Telegram or another device | Phase 9 only; needs INTERNET; opt-in per app; clearly shown in settings |

### J. Smart / AI (optional, last, needs internet)

| ID | Feature | Acceptance check |
|---|---|---|
| F-68 | **Summaries** of long group chats or floods of messages | Opt-in; user sees exactly what will be sent; never sent for sensitive apps |
| F-69 | **Spam / promo detection** | Prefer on-device rules first; remote only if opt-in |
| F-70 | **Importance scoring**: learn which messages the user usually opens and promote similar ones | On-device only; resettable |
| F-71 | **Translation** of foreign-language messages | Opt-in; per app |

---

## 6. Build phases (do in order; update Status after each)

| Phase | Goal | Features | Status |
|---|---|---|---|
| 1 | Core card | Listener, setup screen, overlay card, test card | Done |
| 2 | Look and polish | F-01 to F-11, F-17, F-18, F-38, F-51 | Todo |
| 3 | Control | F-19 to F-29, F-12, F-13, F-14 | Todo |
| 4 | History | F-39 to F-46 | Todo |
| 5 | Sound | F-47 to F-50 | Todo |
| 6 | Actions | F-30 to F-37 | Todo |
| 7 | Special cards | F-52 to F-57, F-15 | Todo |
| 8 | Privacy and digest | F-16, F-26, F-58 to F-62 | Todo |
| 9 | Extras and optional online | F-63 to F-67, then F-68 to F-71 | Todo |

---

## 7. Settings screen layout (target)

1. **Setup status**: Notification access, Display over other apps, Battery not restricted, with a "Send a test card" button.
2. **Look**: theme, shape, size, position, animation and speed, blur, auto-hide time, peek-only.
3. **Apps**: list of installed apps with priority (VIP/normal/silent), accent color, sound, hide text, card type, digest-only, block.
4. **People**: VIP list, mute list, bypass list, per-contact sound.
5. **Profiles and quiet hours**: profiles, schedules, emergency bypass.
6. **Rules**: keywords (allow/block), mention-only groups, repeat alerts.
7. **Sound and haptics**: global sound, day/night volume, vibration patterns, spoken announcements.
8. **Actions**: smart replies list, swipe gesture mapping, snooze options.
9. **Inbox**: opens the history screen; retention days; export.
10. **Privacy**: sensitive apps, biometric unlock, screen-sharing hide, wipe data.
11. **Backup and themes**: backup, restore, import/export theme.
12. **About**: version, what the app can and cannot access, link to the privacy statement.

Keep the main screen simple: setup status plus 5 big entries. Advanced options live one level deeper.

---

## 8. Test checklist (real phone)

**Setup**
- Fresh install: three permissions can be granted; "Allow restricted settings" help appears when Notification access is greyed out.
- "Send a test card" shows a card.

**Core**
- WhatsApp and WhatsApp Business messages show with the right photo and badge.
- Five quick messages from one person show one card with a count.
- Dragging, snapping, swiping away, and tap-to-open work.
- Rotation and different screen sizes do not break the card.

**Rules**
- Silent app: no card, but item is in history.
- Quiet hours: only VIP or bypass contacts show.
- Keyword block hides a card; keyword allow overrides quiet hours only if documented.
- Profile schedule switches at the right time.

**Actions**
- Reply from card sends a message in the source app.
- OTP copy puts the code on the clipboard.
- Snooze returns the card at the set time, even after restart.

**Sound and privacy**
- Per-contact sound plays and respects silent/Do Not Disturb.
- Hidden-text app shows no message text anywhere (card, history preview if setting says so).
- Biometric gate blocks expansion when failed.

**Reliability**
- Service stays alive after screen off for 1 hour (check on Xiaomi, Oppo, Samsung if possible).
- No crash when the notification has no text, no photo, or a very long text.
- 500 notifications in a row do not slow the phone or leak memory.
- Cold start to first card under about 300 ms after a notification arrives while the service is running.

---

## 9. Known risks and how to handle them

| Risk | Handling |
|---|---|
| Android 13+ greys out Notification access for sideloaded APKs | Setup screen explains "Allow restricted settings" |
| Blur-behind blurs a rectangle, so rounded corners may show a blurred corner | Use a small corner radius or turn blur off by default if it looks bad |
| Some phones hide notification text on a secure lock screen | Respect it; show a generic card |
| Some brands kill background services | Guide the user to auto-start and "no battery restrictions"; use a foreground service |
| Some apps refuse to start from the background when a card is tapped | Show a short message and fall back to the normal notification |
| Google Play review for overlay, notification access, and package visibility | Sideload for testing; for Play, prepare a privacy policy and limit `QUERY_ALL_PACKAGES` if possible |
| Detectors (OTP, payment, delivery) can misfire | Keep patterns simple, test with real examples, and let the user turn each detector off |
| Signing key committed in the repo | Fine for personal use; before any Play release, generate a private key and pass it through secrets |

---

## 10. Permissions summary

| Permission | Why | When |
|---|---|---|
| Notification access (listener) | Read notifications | Phase 1 |
| Display over other apps (`SYSTEM_ALERT_WINDOW`) | Draw the glass card | Phase 1 |
| Ignore battery optimization | Keep the service alive | Phase 1 |
| Post notifications (Android 13+) | Foreground-service notice and reminders | Phase 1/6 |
| Vibrate | Haptics and patterns | Phase 5 |
| Biometric | Unlock sensitive cards | Phase 8 |
| Record audio | Voice reply only | Phase 6 (only when used) |
| Exact alarms or WorkManager | Snooze, repeat alerts, digest times | Phase 6/8 |
| INTERNET | Forwarding and AI features only | Phase 9, opt-in, explained to the user |

---

## 11. Definition of done (whole project)

- Every feature above is either working or clearly marked "not supported on this phone".
- The APK builds from GitHub with no manual steps.
- No notification text is logged or sent anywhere unless the user opted into a Phase 9 feature.
- The settings screen is simple at the top level and clear at the second level.
- A short user guide (`USER-GUIDE.md`) explains setup, permissions, and each feature in plain words.

---

## 12. Instructions for the agent when you get stuck

- If a feature depends on something this phone or Android version cannot do, implement the closest safe fallback and note it in the README under "What works today / Honest notes".
- If a build breaks, fix the smallest thing and push; do not refactor unrelated code.
- Ask the user only when a decision changes how the product looks or what data leaves the phone.
