# iOS Port Inventory — PT (Public Transportation)

> **Step 1 deliverable.** A structured map of the existing Android-native app for porting to
> iPhone via **Compose Multiplatform (Kotlin Multiplatform / KMP)**, distributed via TestFlight.
> Generated 2026-06-03 from `android/` at commit on `main`.
>
> Goal: an iOS app that is **exactly like the Android-native one**. Strategy chosen for
> **robustness** = one Compose codebase driving both platforms (no second UI codebase to drift).

---

## 0. The one-paragraph summary

The Android app is **~11.5k LOC of Jetpack Compose** + a thin data layer (Retrofit/Hilt) that
talks to an **unchanged Next.js backend** over `/api/*`. Because it's already Compose,
**~75–80% of it moves to `commonMain` and renders identically on iOS** via Compose Multiplatform.
The real iOS work is **four platform seams**: the **OSM map** (osmdroid → MapLibre, the big one),
**location** (FusedLocation → CoreLocation), **persistence** (SharedPreferences/org.json →
multiplatform-settings), and **DI/networking/time** swaps (Hilt→Koin, Retrofit→Ktor, java.time→
kotlinx-datetime). None of this needs a Mac until the iOS *build* step; everything up to that
runs on Linux with the Android target staying green.

---

## 1. What does NOT change

These are shared/server-side and require **zero** porting:

| Thing | Why unchanged |
|---|---|
| **Backend** (`/api/route`, `/api/geocode`, `/api/stops`, `/api/transport`, `/api/line-shape`, `/api/health`, …) | Next.js server (`frontend/public-transportation`) + MOTIS + SIRI. iOS calls the same URLs. |
| **Server topology / failover** | Same peer list (`10.7.0.2/1/4/6` + public `pt.{dev,prod}.ya-niv.com`). Port via build flavor (dev 3003 / prod 3002). |
| **API request/response shapes** | Same DTOs (see §3 models). iOS deserializes the same JSON. |
| **GTFS / OSM tiles / SIRI live data** | Server concern. |

> Implication: the iOS app is a **pure client port**. No backend work in scope.

---

## 2. Strategy: Compose Multiplatform (KMP), not SwiftUI

| | Compose Multiplatform (chosen) | SwiftUI rewrite (rejected) |
|---|---|---|
| New UI code | ~minimal — existing Compose moves to `commonMain` | ~all 264 files rewritten |
| "Exactly like Android" | **identical by construction** (same renderer) | must hand-recreate the look |
| Long-term drift | one codebase → no drift (robust) | two UIs → permanent double-maintenance |
| iOS "native feel" | custom-drawn (Skia), like Flutter | true UIKit/SwiftUI feel |

Given the explicit goals ("exactly like Android" + "robustness"), CMP wins. The only place CMP's
non-native rendering matters is the **map**, which is *already* a custom-drawn `AndroidView` on
Android — so we're swapping one native map view for another behind an `expect/actual` seam, §6.

---

## 3. Current Android architecture → KMP destination

LOC and target source set for every file (`commonMain` = shared/identical; `androidMain`/`iosMain`
= platform `actual`; **bold** = needs real rewrite, not a move).

### Entry / app shell
| File | LOC | Role | KMP destination |
|---|---|---|---|
| `MainActivity.kt` | 144 | Android entry, edge-to-edge, volume-key map zoom, server-check gate, lang/RTL `CompositionLocalProvider` | split: shell composable → `commonMain`; **`androidMain` Activity** + **`iosMain` `ComposeUIViewController`** |
| `PTApp.kt` | 7 | Hilt `@HiltAndroidApp` | `androidMain` only; iOS uses Koin init |

### Data / models  (→ `commonMain`, add `@Serializable`)
| File | LOC | Notes |
|---|---|---|
| `data/model/Route.kt` | 72 | `TransitMode`, `RouteLeg`, `Itinerary` (incl. `estimateFare()`, `walkDuration`), `RouteResult`, `Place`, `RouteSortMode` — pure Kotlin ✅ |
| `data/model/Transport.kt` | 74 | SIRI DTOs + `VehicleMarker` + `extractVehicleMarkers()`. **Uses Gson `@SerializedName`** → switch to kotlinx-serialization `@SerialName` |
| `data/model/Stop.kt` | 9 | `StopResult` |
| `data/model/Geocode.kt` | 8 | `GeocodeSuggestion` |
| `data/api/PtApi.kt` | 63 | **Retrofit interface (9 endpoints)** → rewrite as Ktor client |

### State / logic  (→ `commonMain`)
| File | LOC | Notes |
|---|---|---|
| `ui/viewmodel/RoutingViewModel.kt` | 232 | `androidx.lifecycle.ViewModel` (KMP-ok), StateFlow, coroutines. **Uses `@HiltViewModel`/`@Inject`** → Koin. **Uses `java.time.ZonedDateTime`/`DateTimeFormatter`** → kotlinx-datetime |
| `ui/viewmodel/ArrivalsViewModel.kt` | 131 | same; `System.currentTimeMillis()` → `Clock.System.now()` |
| `util/PolylineDecoder.kt` | 43 | pure Kotlin ✅ |
| `util/ScreenTracker.kt` | 6 | trivial |
| `util/AppStrings.kt` | 355 | **i18n: hand-rolled `AppStrings` data class + `EnStrings`/`HeStrings` + `compositionLocalOf`.** No Android resources → **moves to `commonMain` as-is** ✅ (huge win, §5) |
| `util/SettingsStore.kt` | 181 | **SharedPreferences + org.json** → multiplatform-settings + kotlinx-serialization (§4) |
| `util/ServerConfig.kt` | 80 | **`java.net.HttpURLConnection` + `BuildConfig`** → Ktor health-check + `expect` build config |

### UI — Compose screens  (→ `commonMain`, move mostly as-is)
| File | LOC | Platform-specific risk |
|---|---|---|
| `ui/MainScreen.kt` | 688 | the hub: bottom sheet, tabs, map host. Audit for `android.*` imports |
| `ui/arrivals/ArrivalsPanel.kt` | 283 | clean Compose |
| `ui/arrivals/StationArrivals.kt` | 307 | clean Compose |
| `ui/arrivals/TransportControls.kt` | 152 | clean Compose |
| `ui/components/AutocompleteField.kt` | 239 | clean Compose |
| `ui/lines/LinesBrowserPanel.kt` | 243 | clean Compose |
| `ui/routing/RoutePlannerPanel.kt` | 244 | clean Compose |
| `ui/routing/ItineraryDetail.kt` | 264 | clean Compose |
| `ui/routing/RouteResults.kt` | 156 | clean Compose |
| `ui/routing/ItineraryCard.kt` | 151 | clean Compose |
| `ui/routing/TimePicker.kt` | 140 | **uses `java.time`** → kotlinx-datetime |
| `ui/routing/LocationInput.kt` | 96 | check location trigger |
| `ui/routing/DebugSettingsDialog.kt` | 131 | clean Compose |
| `ui/SheetHeader.kt` | 182 | clean Compose |
| `ui/OpacityControls.kt` | 84 | clean Compose |
| `ui/SavePlaceDialog.kt` | 27 | clean Compose |
| `ui/theme/Theme.kt` | 36 | Material3 theme — CMP-compatible |
| `ui/LocationHelper.kt` | 63 | **FusedLocation** → `expect/actual` (§6) |

### Map — the platform seam  (→ `expect/actual`, **rewrite for iOS**)
| File | LOC | Notes |
|---|---|---|
| `ui/map/OsmMapView.kt` | 134 | osmdroid `MapView` via `AndroidView` + `MapZoomHandler` |
| `ui/map/MapOverlays.kt` | 286 | **7 overlay types**: route polylines (dashed walk), stop/transfer/vehicle/origin/dest/tracked-bus markers, line-shape. All osmdroid `Polyline`/`Marker` |
| `ui/map/MapDrawables.kt` | 195 | `android.graphics` custom drawables (circle, diamond pin, animated origin) |
| `ui/map/MapOverlays`→`GpsLocationOverlay.kt` | 61 | osmdroid GPS overlay |
| `ui/map/FusedLocationOverlayProvider.kt` | 54 | FusedLocation → osmdroid bridge |

---

## 4. Dependency mapping (Android → KMP/iOS)

| Android dependency | Role | KMP / iOS replacement | Effort |
|---|---|---|---|
| Jetpack Compose Material3 + icons-extended | UI | **Compose Multiplatform** (`org.jetbrains.compose`) Material3 + `compose.materialIconsExtended` | low (config) |
| Hilt / Dagger (`@HiltViewModel`, `@Module`, `@Inject`) | DI | **Koin** (`koin-core`, `koin-compose`, `koin-compose-viewmodel`) | low–med |
| Retrofit + OkHttp + Gson | HTTP + JSON | **Ktor client** (`OkHttp` engine on Android, `Darwin` on iOS) + **kotlinx-serialization** | med (9 endpoints + failover plugin) |
| androidx.lifecycle ViewModel/compose | state host | **lifecycle-viewmodel-compose 2.8+** (already KMP) | low |
| **osmdroid** | OSM map | **MapLibre Native** (iOS SDK; optionally also Android — §6 fork) | **high (iOS map)** |
| play-services-location (FusedLocation) | GPS | **CoreLocation** (`CLLocationManager`) via `expect/actual` | med |
| SharedPreferences + org.json | persistence | **multiplatform-settings** (NSUserDefaults on iOS) + kotlinx-serialization for JSON blobs | low–med |
| java.time (`ZonedDateTime`, `DateTimeFormatter`) | date/time | **kotlinx-datetime** | med (formatting differs) |
| coroutines-android | async | kotlinx-coroutines (common) | none |

---

## 5. i18n / RTL — already KMP-ready (a gift)

The app does **not** use Android string resources. `AppStrings.kt` is a plain Kotlin `data class`
of strings + lambdas, with two singletons `EnStrings`/`HeStrings`, surfaced via
`compositionLocalOf` and `LocalLayoutDirection` (RTL set in `MainActivity`). All of this is
Compose-runtime + pure Kotlin → **moves to `commonMain` verbatim**. Hebrew RTL works on iOS the
same way (CMP honors `LocalLayoutDirection`). No `.strings`/localization files needed. ✅

---

## 6. The map (the real iOS work) + an architecture fork

The map is the **only** large iOS-specific effort. Plan: define a platform-neutral
`expect` composable + overlay API in `commonMain`, e.g.:

```kotlin
// commonMain
@Composable expect fun PtMap(
    state: PtMapState,                 // center, zoom, camera commands
    onLongPress: (LatLng) -> Unit,
    onCameraMoved: (LatLng, Double) -> Unit,
    overlays: PtMapOverlays,           // routePolylines, markers (typed), lineShapes…
)
```
`androidMain` implements it with osmdroid (port the existing code), `iosMain` with **MapLibre iOS**.
All 7 overlay types map to MapLibre annotations / line+symbol layers; custom drawables
(circle, diamond pin, animated origin) become MapLibre symbol images / annotation views.
MapLibre can use the **same MAPNIK-style raster tiles**, so the map looks identical.

### Decision fork — Android map stack
- **Option A (default, low risk):** keep **osmdroid on Android**, MapLibre on iOS, both behind the
  `PtMap` seam. Android keeps working untouched; two map impls to maintain.
- **Option B (max robustness):** migrate **Android to MapLibre too** → one map library, one overlay
  implementation, identical behavior both platforms. Most aligned with the "single source of truth"
  goal, but it **touches the working Android map** (regression risk; needs re-test on device).

> Recommendation: start with **A** to de-risk; revisit **B** once iOS parity is proven. This is a
> user decision — see §10.

---

## 7. Target module structure

```
publicTransportation/
  android/                 # existing — becomes a KMP project
    settings.gradle.kts    # include(":shared", ":androidApp")
    shared/                # NEW kotlin-multiplatform + compose module
      src/commonMain/      # models, VMs, AppStrings, UI screens, Ktor client, PtMap (expect), DI
      src/androidMain/     # osmdroid PtMap actual, FusedLocation actual, settings actual
      src/iosMain/         # MapLibre PtMap actual, CoreLocation actual, NSUserDefaults actual,
                           #   MainViewController() = ComposeUIViewController { App() }
    androidApp/            # thin launcher: MainActivity wraps shared App()  (was :app)
    iosApp/                # NEW Xcode project — consumes shared.framework
                           #   (Gradle embedAndSignAppleFrameworkForXcode, or SPM/CocoaPods)
    feedback-lib/          # Android-only dev tool — stays androidApp-only, NOT ported (§8)
```

Gradle: `kotlin("multiplatform")`, `org.jetbrains.compose`, `kotlinx-serialization`,
optional `kotlin.cocoapods` for MapLibre. iOS targets: `iosArm64` (device) + `iosSimulatorArm64`.

---

## 8. feedback-lib

Android `:feedback-lib` is a **dev-flavor-only** Compose+Hilt+Retrofit module, bind-mounted from
`addnewfeature`. It is a *developer* tool, not a user feature. **Out of scope for the iOS port** —
the iOS app ships without it initially (matches the *prod* Android flavor, which also excludes it).
Revisit only if/when a native iOS feedback-lib exists.

---

## 9. Build, signing & TestFlight (payment-gated)

| Stage | Needs Mac? | Needs $99 Apple? |
|---|---|---|
| KMP restructure, Android stays green, write `iosMain` | ❌ (Linux) | ❌ |
| Build iOS framework + run in **iOS Simulator** to parity | ✅ (Mac on VPN) | ❌ (simulator needs no account) |
| Sideload to a physical iPhone | ✅ | ❌ (free 7-day personal team) |
| **TestFlight distribution** | ✅ | ✅ ($99/yr) |

iOS build driven remotely on the VPN Mac: Gradle `:shared` framework + `xcodebuild` archive →
`fastlane pilot` upload. App Store Connect API key for headless signing. **Deferred until the user
opts to pay.**

---

## 10. Open decisions for the user (gate the next step)

1. **Map stack** — Option A (osmdroid Android + MapLibre iOS) *[recommended start]* vs Option B
   (MapLibre on both, max robustness, touches working Android). → §6
2. **App identity** — same bundle id family? Proposed iOS bundle id `com.automatelinux.pt`
   (matches Android `applicationId`). App name / icon reuse the Android `Bus` icon?
3. **DI** — confirm **Koin** (the standard KMP choice) to replace Hilt across shared code.
4. **Repo layout** — KMP-ify the existing `android/` dir (recommended) vs a new `ios/` sibling.

---

## 11. Effort & risk (rough)

| Workstream | Size | Risk |
|---|---|---|
| KMP project setup + Compose MP config | M | low |
| Move models/VMs/strings/screens to commonMain | M | low (mechanical; audit `android.*` imports) |
| Retrofit→Ktor + Gson→kotlinx-serialization | S | low |
| Hilt→Koin | S–M | low |
| java.time→kotlinx-datetime (formatting parity) | S–M | med (locale/RTL number formatting) |
| SharedPreferences→multiplatform-settings | S | low |
| Location: CoreLocation actual | S–M | med (permission UX) |
| **Map: MapLibre iOS — 7 overlays + drawables** | **L** | **high** (the bulk of iOS work) |
| iOS build/CI/TestFlight wiring | M | med (Mac-dependent) |

**Critical path = the map.** Everything else is mechanical KMP plumbing.

---

## 12. Proposed next steps (step 2+)

1. Resolve §10 decisions.
2. On Linux: KMP-ify `android/` — create `:shared`, move `commonMain` candidates, swap
   Hilt→Koin / Retrofit→Ktor / java.time→kotlinx-datetime / SharedPrefs→multiplatform-settings,
   stub `expect` map+location. **Keep the Android app building & runnable on the phone the whole time.**
3. When the VPN Mac is ready: add `iosApp`, implement `iosMain` actuals (MapLibre, CoreLocation,
   NSUserDefaults), run in the iOS Simulator, iterate to pixel parity against Android.
4. (Paid, on request) fastlane + App Store Connect → TestFlight.
