# Google Play store screenshots — publicTransportation

8 portrait screenshots, 1080×2400 (Pixel 7 class), Hebrew UI, real live Israeli
transit data. Captured 2026-09-24 between 21:29 and 21:40 IDT, so every countdown,
departure time and vehicle position in them is genuine SIRI / GTFS data, not a mock.

**Shot on Android 16 (API 36), from `main` at `4a846e40`** — which contains the
edge-to-edge fix `3f858fa6`, so no control sits under the navigation bar in any of
these frames. (`4a846e40` was HEAD at capture time; it adds two map/tracking commits
on top of the fix.)

## How they were made (to reproduce)

- **AVD**: `phone_demo_36` — Pixel 7, **Android 16 / API 36**,
  `system-images;android-36;google_apis_playstore;x86_64`, 1080×2400 @ 420 dpi,
  run headless on the *desktop* peer:
  `emulator -avd phone_demo_36 -no-window -gpu swiftshader_indirect -port 5562`.
- **Build**: `./gradlew :app:assembleDevRelease -PdemoRecording=true` — `demoRecording`
  sets `FEEDBACK_ENABLED=false`, so the feedback-lib bug icon and chat button are
  absent from every frame. It also flips the public fallback server to
  `https://pt.prod.ya-niv.com` (see `PTApp.kt`), so the build is not gated by dev-auth.
- **Backend**: the desktop dev server on `:3003`, reached from the emulator via
  `adb reverse tcp:3003 tcp:3003` (the app's first server candidate is the USB tunnel).
- **Locale**: `adb shell cmd locale set-app-locales com.automatelinux.pt.dev --locales he-IL`
  (per-app; no root needed on a Play image). Note this is **lost on uninstall** — re-apply
  it after any reinstall or the app comes up in English.
- **Location**: `adb emu geo fix 34.7818 32.0853` (Tel Aviv). Apply it *before* launching,
  and if the origin field still shows a Californian default, tap the crosshair inside the
  מוצא field to re-resolve it.
- **Status bar**: SystemUI demo mode — notifications hidden, battery 100 and not charging,
  device switched to 24-hour time (`settings put system time_12_24 24`), and the demo
  clock **set to the real wall-clock time before each capture**, so it never contradicts
  the times the app is showing. Wi-Fi and mobile are both hidden: the emulator's Wi-Fi
  icon carries a permanent "no internet" (`!`) badge that demo mode cannot clear, and a
  "no internet" warning on a live-data app reads badly. Every shot therefore has clock +
  battery only. `-e datatype lte` renders as a dated **3G** glyph on this image, so mobile
  is not worth showing either.
- **Panel opacity**: both values set to **1.0**. The sliders (gear → הגדרות שקיפות)
  cannot be driven reliably over `adb`, so the values were written straight into the
  app's preferences using a debuggable build:
  ```
  ./gradlew :app:assembleDevDebug -PdemoRecording=true      # same flags, debuggable
  adb shell run-as com.automatelinux.pt.dev cat shared_prefs/pt_settings.xml
  # add: <float name="sheet_opacity" value="1.0" /> and <float name="card_opacity" value="1.0" />
  ```
  with the app force-stopped, then relaunch. At the shipped defaults (50% sheet /
  60% cards) the panels are translucent and map detail shows through the text.

---

## The files

### `01-route-on-map.png`
The planned journey drawn end to end on the map — Tel Aviv (ארלוזורוב) to
Be'er Sheva (באר שבע מרכז) — with the route summary, the public-transport vs. car
comparison (1:55 vs 1:15) and the sort chips beneath it.
> **מסלול שלם מתל אביב לבאר שבע — על המפה, עם כל תחנה בדרך.**

### `02-route-options.png`
The results list: five ways to make the same trip, each with duration, arrival
window, number of transfers, fare, the line numbers you'd ride, and a live
countdown to the next departure. Sort chips for מגיע ראשון / המהיר ביותר /
פחות החלפות / פחות הליכה.
> **כל האפשרויות במבט אחד — כמה זמן, כמה החלפות, כמה זה עולה.**

### `03-itinerary-detail.png`
One journey, step by step: walk 82 m, board line 289 at 21:45, ride 14 min for
₪8, walk 667 m, wait 10, board line 370 at the Tel Aviv central station, arrive
Be'er Sheva 23:40. Each leg carries a "follow this bus" and "remind me before it
leaves" action, plus accessibility and fare badges.
> **צעד אחר צעד: לאן ללכת, על מה לעלות, מתי לרדת — וכמה זה עולה.**

### `04-live-arrivals.png`
Live arrivals at a stop (אבן גבירול/ארלוזורוב): the next bus in large type with
its destination, then the upcoming departures, filterable by line, each tagged
בזמן אמת (real-time) or מתוכנן (scheduled). Also offers a home-screen widget for
this stop.
> **מה מגיע לתחנה שלך עכשיו — בזמן אמת, לא לפי הלוח.**

### `05-lines-browser.png`
The line browser: search a line number or pick a popular one, and the full route
is drawn on the map — both directions at once, with every stop.
> **כל קו בארץ, כל התחנות, שני הכיוונים — על המפה.**

### `06-which-bus-am-i-on.png`
"באיזה אוטובוס אתה?" — the app lists the lines actually passing your location
right now, in your direction, each with the stop you'd have boarded at. Pick one
and it plans the rest of the trip from the moving bus.
> **כבר באוטובוס? בחר את הקו ונתכנן את ההמשך מאיפה שאתה.**

### `07-departure-board.png`
A full-screen departure board for the stop — airport style, with a live ticking
clock, line colours, destinations and minutes-to-arrival. Built to be left open
on a screen.
> **לוח יציאות מלא לתחנה — כמו בשדה תעופה, מתעדכן כל הזמן.**

### `08-live-bus-tracking.png`
Following a single vehicle: line 289 towards יסוד המעלה/לוינסקי, 7 minutes out,
1.8 km away, its position updated seconds ago, wheelchair accessible, with its
line and remaining stops drawn on the map.
> **עוקבים אחרי האוטובוס עצמו — איפה הוא נמצא ובעוד כמה דקות הוא אצלך.**

---

## Suggested Play ordering

`01` → `02` → `04` → `03` → `07` → `06` → `08` → `05`.
The first three carry the core promise (plan a trip, compare options, know when
the bus really comes); the rest are differentiators.

## Known issue visible in these images

The **status bar clock is very dim** on `02`, `03`, `04`, `07` and `08`. That is the
open status-bar-appearance defect, not a capture problem: the app calls
`enableEdgeToEdge()` without ever setting `isAppearanceLightStatusBars`, so the system
keeps drawing dark icons over the app's dark surfaces (measured contrast as low as
4/255). Fixing it would make the clock and battery legible in these same frames.
