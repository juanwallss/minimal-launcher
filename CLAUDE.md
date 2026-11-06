# CLAUDE.md

Guidance for Claude Code sessions working on this repo.

## What this is

**Minimal** — a personal, minimalist Android home-screen launcher (Kotlin + Jetpack Compose, one Activity).
Built for and installed on the owner's Samsung Galaxy (One UI, gesture navigation). The owner
speaks **Spanish** — reply in Spanish. Their phone UI is in **English**, so the launcher shows English there.

Look: near-black / warm off-white (follows the system dark/light mode), thin **Jost** type, lowercase labels
(app names capitalized), grayscale icons, no chrome. Keep every addition in that style: small text options
(`favorito · info · ocultar`) instead of buttons/dialogs, hand-drawn thin icons on `Canvas` (no icon library),
subtle animations. Avoid adding dependencies — the app uses only `activity-compose` + `compose-foundation`.

## Build, install, run

No standalone Java on the Mac — use Android Studio's JDK. SDK is at `~/Library/Android/sdk`.

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug      # compile check
./gradlew installDebug       # build + install on the connected phone
```

The phone is reached over **wireless debugging** (`~/Library/Android/sdk/platform-tools/adb`):
- `adb devices` / `adb mdns services` to find it. When it's
  missing, `adb kill-server && adb start-server` often fixes discovery, then `adb connect <ip:port from mdns>`.
- If it refuses the connection, the owner must re-pair (`adb pair IP:PORT` from the phone's "pair with code"
  dialog). **Never type the pairing code yourself** — the owner runs that command.
- With an installed-but-not-front launcher, `installDebug` still works; changes show next time they press Home.

There are no tests. "Done" for a change = `assembleDebug` compiles, `installDebug` succeeds, `adb logcat -d | grep FATAL`
is clean, and a commit. Say clearly when you could not see the change on screen.

## Working on the owner's real phone (important)

The phone is in daily use. Before **any** `adb shell input …` (tap/swipe/keyevent):
1. Check `adb shell dumpsys window | grep -m1 mCurrentFocus` contains `minimallauncher`; otherwise don't touch it
   (earlier, test swipes landed in WhatsApp).
2. Re-check between steps of a sequence; abort if focus changes.

Screenshots (`adb exec-out screencap -p`) of the real phone can show private notifications, apps and the
wallpaper. Prefer `uiautomator dump` text checks; if you need pixels, crop to the area you need, don't describe
personal content, and delete the image afterwards. Don't put the owner's app list or wallpaper in docs/README.

## Code map

| File | What's in it |
|---|---|
| `MainActivity.kt` | Everything on the home screen: state, layout layers, app list, letter bar, clock/status line, gestures, favorites, recents, widget area + system widget hosting, settings entry, animations helpers (`enterAnim`, `longPressFirst`, `alignInk`, `fadeEdges`). Palette/type tokens (`Bg`, `Fg`, `Accent` (theme color; `Fg` when no theme), `Dim`, `RowText`, `SmallText`, `TextShadow`, `Grayscale`) are `internal` top-level vals here. |
| `Settings.kt` | `Look` object (all user settings as Compose state + `load`/`save` to prefs) and the settings screen (long-press the time): opaque, a category list (appearance · home · gestures · driving · permissions) with per-category pages, live background preview, gesture app picker, dim slider, permissions status, `hasUsageAccess()`. |
| `NativeWidgets.kt` | Built-in widget pages: agenda (calendar), music (media sessions + seekable progress + drawn controls), weather (Open-Meteo, coarse location). `NativeWidget` enum. |
| `WidgetHost.kt` | `AppWidgetHost` subclass + host view that detects long-press and swipes on top of other apps' widgets. |
| `LockService.kt` | Accessibility service used only for double-tap-to-lock (`GLOBAL_ACTION_LOCK_SCREEN`). |
| `NotificationDots.kt` | Notification listener: which packages have notifications (dots, `• n` count), plus in-memory chat `Message`s (reply action, app actions, system smart replies from `Ranking`) and `reply`/`send` helpers — also grants media-session access for the music widget. |
| `MessageCard.kt` | Home-view card for the newest chat notification: actions, suggested replies, free reply field. Debug: `--ez msg_test true` shows a fake chat. |
| `Clocks.kt` | `ClockStyle` + `ClockFace`: thin, bold, day, split, analog, stacked, day progress, binary. Debug extras: `--ei clock_style N`, `--es theme sea`, `--ez bar_left true`. |
| `Onboarding.kt` | First-run intro (welcome · make it home via `RoleManager` · how it works · optional permissions). Shown while `Look.onboarded` is false. Debug: `--ez onboarding true`. |
| `res/values/strings.xml` + `values-es/` | **Every visible string**, English base + Spanish. Add new strings to both. |
| `res/values/themes.xml` | Transparent window theme — see gotchas. |

## Data & settings

All in `SharedPreferences("launcher")`: `favOrder` (ordered favorites), `hidden`, `pages` (widget area, see below),
`recent` (launcher-opened history), `recentClearedAt` / `rd:<pkg>` (recents clearing cut-offs), `weather` (cached
JSON), `lastPlayer`, and the `Look` keys (`bg`, `bgDim` 80–98, `bgGray`, `clockSize`, `textSize`, `clock24`,
`barBottom`, `barLeft`, `barHaptics` (0–3), `onboarded`, `font` (index in `FONTS`: jost, fraunces, jetbrains mono, space grotesk — `Thin` returns the chosen one), `infoOff` (comma list of what's hidden under the time — stored as OFF so new items appear by default; old `info` key migrated), `theme` (none|wall|amber|sage|sea|rose|lilac), `showRecents`, `showMessages`, `showWidgets`, `g_<gesture>`). Photo background is copied to `filesDir/background.jpg`.

Widget area pages are strings: `w:<appWidgetId>` (other apps' widgets) or `n:agenda|n:music|n:weather` (built-in).
Gesture actions: `none|lock|notifications|search|camera|torch|app:<pkg>/<activity>`.

## Gotchas (each one was a real bug)

- **Window must be transparent from creation** (`@style/Launcher`). The system-wallpaper background only toggles
  `FLAG_SHOW_WALLPAPER` at runtime. Switching pixel format at runtime made every frame paint over the last (ghosting).
- **Letter bar never selects on touch-down**: only on tap-release or when sliding *along* the strip. Otherwise the
  system back gesture (right edge) flashes a letter. Closures in `pointerInput(letters)` see stale `selected` — track
  locally.
- **Long-press on built-in widget pages** uses `longPressFirst` (Initial pass), because the pages' own clickables
  consume the touch first. Other apps' widgets report gestures through `GestureWidgetView` (they swallow touches, so
  Compose never sees them).
- Font side bearing makes the big thin clock look indented: `alignInk` measures real ink bounds and offsets text.
- `SET_ALARM` permission is needed for Samsung's clock to accept `ACTION_SHOW_ALARMS`.
- `ACCESSIBILITY` / notification access / usage access / calendar / location are user-granted; never toggle them via
  adb — send the user to the right settings screen instead.
- **Keep `onResume` cheap** (it runs on every Home press): the app list is rebuilt only when `LauncherApps`
  reports a package change (`packageWatcher`), and recents read usage events incrementally off the main thread.
- Respect system animation scale (Compose does by default); keep animations short (≤ ~450 ms).

## Conventions

- Commit after each change (`git -c user.name="Juan Paredes" commit`), messages in English, one line + body.
- Prefer small, surgical edits; the owner tests on-device and iterates fast.
- README.md (Spanish) documents gestures/permissions for the owner; update it when user-facing behavior changes
  significantly. Screenshots in `docs/` must not show personal data — take them on the `Medium_Phone` emulator with sample apps.

## Releases

`./gradlew assembleRelease` → minified APK signed with the keystore that `keystore.properties` (git-ignored) points to;
the keystore lives outside the repo (`~/.android-keystores/`). **Never commit either.** Bump `versionCode` +
`versionName` in `app/build.gradle.kts`, then a GitHub release `vX.Y.Z` with `minimal-X.Y.Z.apk` attached
(repo `juanwallss/minimal-launcher`, private; use that account's token: `GH_TOKEN=$(gh auth token --user juanwallss)`).
Release builds can't update a debug install (different signature): uninstall first.
