# SPEC — FakeTraveler v2.4.0

Distilled from `FakeTraveler_Caveman_Orchestration_Engine.md`, current code, `output/L*.md`. Caveman format.

## §G — Goal

Android app. Spoof device GPS system-wide. Map UI + manual lat/lng + GPX route playback. Persist across recents-swipe + reboot. Java only. GPL-3. F-Droid.

## §C — Constraints

| id | rule |
|---|---|
| C1 | Java only. ZERO Kotlin. |
| C2 | ZERO new external deps. AndroidX + Material + Flexbox only (frozen in `app/build.gradle:34-38`). |
| C3 | GPL-3 license. Preserve per-file headers when present. |
| C4 | minSdk=21 (Lollipop) → targetSdk=36 (Android 16 Baklava). compileSdk=36. |
| C5 | F-Droid build clean. No proprietary libs. No Play Services. |
| C6 | Java 21 source/target. R8 desugars records + Java 9+ collection factories. |
| C7 | Per-app spoofing OUT-OF-SCOPE (Android security model — see #102, V8). |
| C8 | Class ≤300 LOC. Method ≤40 LOC. `@NonNull`/`@Nullable` on params. |

## §I — External surfaces

### I.manifest — `app/src/main/AndroidManifest.xml`

| surface | shape |
|---|---|
| I.perm | INTERNET, ACCESS_MOCK_LOCATION, ACCESS_COARSE_LOCATION, FOREGROUND_SERVICE, FOREGROUND_SERVICE_LOCATION, POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS |
| I.act.main | `MainActivity` exported. intent-filter: MAIN+LAUNCHER, VIEW(geo), VIEW(geo,\*/\*), VIEW(content/file gpx) |
| I.act.more | `MoreActivity` not exported. parentActivityName=MainActivity |
| I.svc | `MockedLocationService` exported=false. foregroundServiceType=location |
| I.recv.boot | `BootCompletedReceiver` exported=true. actions: BOOT_COMPLETED + LOCKED_BOOT_COMPLETED |
| I.backup | `data_extraction_rules.xml` + `backup_rules.xml` exclude sharedprefs. installLocation=internalOnly |

### I.svc.actions — `MockedLocationService` constants

| action | extras |
|---|---|
| ACTION_START | EXTRA_LATITUDE, EXTRA_LONGITUDE, EXTRA_D_LAT, EXTRA_D_LNG, EXTRA_FREQUENCY, EXTRA_COUNT, EXTRA_SPEED |
| ACTION_STOP | (none) |
| ACTION_RESUME | (none — reads prefs) |
| ACTION_PLAY_ROUTE | EXTRA_ROUTE_JSON, EXTRA_FREQUENCY |

### I.prefs — `SharedPreferences("FakeTravelerPrefs")` keys

| key | type | source |
|---|---|---|
| prefsSchemaVersion | int | `SharedPrefsUtil.KEY_SCHEMA_VERSION` |
| restoreAfterBoot | bool | `KEY_RESTORE_AFTER_BOOT` |
| lastMockedLocation | json string | `KEY_LAST_MOCKED_LOCATION` |
| routeData | json string | `KEY_ROUTE_DATA` |
| lat, lng | double (longBits) | `putDouble`/`getDouble` |
| dLat, dLng | double (longBits) | drift |
| endTime | long | epoch ms when mock expires |
| mockCount, mockFrequency | int | UI |
| mockSpeed | bool | UI |

### I.js — `WebAppInterface` (`addJavascriptInterface(this, "Android")`)

| method | param |
|---|---|
| setPosition(String) | "(lat, lng)" — long-press map |
| setZoom(String) | numeric zoom |

### I.intent.geo — `geo:lat,lng?z=Z`

Parsed by `GeoUri` record. VIEW intent → MainActivity.

### I.intent.gpx — VIEW `application/gpx+xml` or `*.gpx`

Parsed by `GpxImporter` (`XmlPullParser`). Saved to prefs `routeData`.

## §V — Invariants

| id | invariant | scope |
|---|---|---|
| V1 | ∀ `addTestProvider` call → SDK_INT branch. ≥31 → `ProviderProperties.Builder`. ≤30 → 10-arg legacy w/ `@SuppressWarnings("deprecation")`. ! the deprecated form on 31+. | `MockedLocationProvider` |
| V2 | ∀ mock cycle → mock GPS_PROVIDER + NETWORK_PROVIDER. On ≥31 also FUSED_PROVIDER. ! mock subset (target apps using Fused see jumps). | `MockedLocationService.attachAllProviders` |
| V3 | `Service.onCreate` → `startForeground` BEFORE any heavy work. ! exceed Android 12+ ~5s window (ForegroundServiceDidNotStartInTimeException). | `MockedLocationService.promoteToForeground` |
| V4 | `startForeground` 3-arg form on ≥29 with `FOREGROUND_SERVICE_TYPE_LOCATION`. 2-arg form on ≤28. | `MockedLocationService.promoteToForeground` |
| V5 | `BroadcastReceiver` for BOOT_COMPLETED declared in manifest AND `RECEIVE_BOOT_COMPLETED` permission declared. ! receiver-without-permission (silently skipped). | `I.manifest`, `I.recv.boot` |
| V6 | ∀ `PendingIntent` ctor → `FLAG_IMMUTABLE` set on ≥M (API 23). ! mutable PendingIntent broadcasts. | `NotificationFactory:57,94` |
| V7 | WebView tile request → intercepted via `WebViewClient.shouldInterceptRequest` for hosts in TILE_HOSTS whitelist → Referer header injected. ! 403 from picky OSM tile servers. | `WebViewSetup.FakeTravelerWebViewClient` |
| V8 | ! per-app spoofing logic. Android mock-location is system-wide. #102 OUT-OF-SCOPE. | repo-wide |
| V9 | ∀ `Settings.Secure`/`AppOpsManager` mock check → AlertDialog on fail with deep-link to dev settings. ! bare Toast/Snackbar. | `PermissionChecker.showDevSettingsDialog`, `MainActivity.applyLocation` |
| V10 | `onStartCommand` returns `START_STICKY`. null intent → `resumeFromPrefsIfActive`. ! return START_NOT_STICKY (loses mock on OOM kill). | `MockedLocationService.onStartCommand` |
| V11 | `SharedPrefsUtil.migrateToV2` idempotent. Guarded by `KEY_SCHEMA_VERSION ≥ SCHEMA_V2`. Try-catch swallows + logs. ! crash on corrupt prefs. | `SharedPrefsUtil.migrateToV2` |
| V12 | ∀ `WebView.addJavascriptInterface` → `@JavascriptInterface` annotation on every exposed method. ! interface methods reachable from arbitrary JS. | `WebAppInterface` |
| V13 | WebView loads ONLY `file:///android_asset/`. `shouldOverrideUrlLoading` blocks non-(file/about/javascript) schemes. | `WebViewSetup.FakeTravelerWebViewClient.shouldOverrideUrlLoading` |
| V14 | OEM dispatch — `Build.MANUFACTURER` → OEM-specific Intent; fallback `ACTION_APPLICATION_DETAILS_SETTINGS`. Every Intent wrapped try-catch. ! ActivityNotFoundException crash. | `OemBatteryOptHelper.promptIfNeeded` |
| V15 | GPX parse uses `XmlPullParser` w/o external entity resolution; route point cap enforced (DoS guard). ! XXE / unbounded memory. | `GpxImporter.parse` |
| V16 | All file paths/URLs under TILE_HOSTS whitelist are HTTPS. `HttpURLConnection` uses default SSLSocketFactory (cert validation). ! trust-all SSL. | `WebViewSetup.FakeTravelerWebViewClient.shouldInterceptRequest` |
| V17 | minSdk < API-of-Java-9-feature → R8 must auto-desugar. Evidence req: `dexdump classes*.dex \| grep -E "RecordTag\|SyntheticBackport"` shows synthetic class. ! ship without dex evidence. | build verify, AGP ≥7 |
| V18 | ∀ `R.string.<id>` defined in `strings.xml` → ≥1 code reference OR strip. ! defined-not-wired (5 found in v2.4.0: MainActivity_MockNotApplied, MainActivity_MockLocRunning, MainActivity_PermissionDeniedTitle, MainActivity_NotificationDeniedMsg, More_RoutePlayCount). | `strings.xml` × Java |
| V19 | `registerReceiver` ACTION_STOP pre-API-33 → 4-arg form gated on signature-level permission `cl.coders.faketraveler.permission.STOP_MOCK`. On ≥33 use `Context.RECEIVER_NOT_EXPORTED`. ! plain registerReceiver(receiver, filter) pre-33 (implicit export = cross-app DoS surface). | `MockedLocationService.registerStopReceiver`, `AndroidManifest.xml:<permission>` |
| V20 | ∀ poison-pill ALL 7 checks PASS before signoff: no `setMockLocation`, no per-app spoof, `ProviderProperties` on ≥31, `startForeground` ≤5s, `RECEIVE_BOOT_COMPLETED` declared, WebView referer interceptor present, all 3 providers mocked. | `output/L1_poison_pill_report.md`, `output/L8_validation_signoff.json` |
| V21 | ZERO lint warnings on `./gradlew lintDebug`. ! ship with warnings>0 (drill-sergeant Sin #1). Justified `@SuppressLint`/`lint.disable` allowed only with architectural rationale comment + compensating control. | `app/build.gradle:lint{}`, all `@SuppressLint` sites |
| V22 | ∀ `WebAppInterface` JS-bridge method → validate input shape BEFORE substring/parse. Bounds-check `indexOf`/`charAt` returns. Wrap body in try-catch returning silently on malformed input. ! uncaught exception on UI thread from JS bridge. | `WebAppInterface.setPosition`, `WebAppInterface.setZoom` |
| V23 | `Location.setSpeed` value = `distance_per_tick(m) / mockFrequency(s)` = m/s. ! divide by ms (×1000 too small). | `MainActivity.applyLocation` |
| V24 | `mockCount==0` (infinite mock) → `endTime = Long.MAX_VALUE`. ! compute past-timestamp via `(count-1)*freq` arithmetic. UI state derives from endTime; sentinel ensures button=Stop while service runs. | `MainActivity.applyLocation`, `MockedLocationService.resumeFromPrefsIfActive`, `BootCompletedReceiver.onReceive` |
| V25 | `TimerTask` extending classes override `cancel()` to set `volatile boolean cancelled`; `run()` returns immediately if `cancelled`. ! rely on `Timer.cancel()` to interrupt mid-`run()`. | `MockedLocationTask`, `RoutePlaybackTask` |
| V26 | Shared mutable collections accessed by Timer thread + main thread → `CopyOnWriteArrayList` or synchronized wrapper. ! plain `ArrayList`. | `MockedLocationService.providers` |
| V27 | Process death recovery on `endTime > now` → `startForegroundService(ACTION_RESUME)` + bind, NOT bind-only. ! bind alone (no `onStartCommand` → no resumeFromPrefs). | `MainActivity.onCreate`, `ServiceConnector.resumeAndBind` |
| V28 | WebView state persisted via `onSaveInstanceState`/`onRestoreInstanceState`. ! reload on every config change (rotate loses marker). | `MainActivity.onSaveInstanceState`, `onRestoreInstanceState` |
| V29 | User-visible error paths use `Snackbar.LENGTH_LONG` (or AlertDialog). ! `LENGTH_SHORT` for errors. | `MainActivity.showError` |
| V30 | Geo intent parse failure → user-visible Snackbar, NOT silent log only. | `MainActivity.applyIntentOrDefault` |

`?` = uncertain or new; user confirm.

## §T — Tasks

`.` = pending, `~` = active, `x` = done, `-` = blocked.

| id | st | task | cites |
|---|---|---|---|
| T1 | x | impl 16 in-scope fixes (FIX-001..019) | V1..V14 |
| T2 | x | build assembleDebug + lintDebug | V20 |
| T3 | x | dex verify desugaring (RecordTag + SyntheticBackport0) | V17 |
| T4 | . | T01–T23 manual on-device matrix (emulator/device) | V1..V14 |
| T5 | x | stripped 5 unused R.string (incl. locale files) + 3 unused colors | V18 |
| T11 | x | impl FIX-020 (DEFECT-NEW-001): WebAppInterface bounds check | V22 |
| T12 | x | lint drive to ZERO (21 warnings → 0); plurals + locale cleanup | V21 |
| T13 | x | impl FIX-021 (V19): signature permission on ACTION_STOP receiver pre-33 | V19 |
| T14 | x | impl FIX-022: PermissionChecker uses OP_MOCK_LOCATION literal (kills InlinedApi) | V21 |
| T15 | x | impl FIX-023 (P0): speed unit corrected to m/s | V23 |
| T16 | x | impl FIX-024 (P0): mockCount=0 → Long.MAX_VALUE sentinel | V24 |
| T17 | x | impl FIX-025 (P2): showError uses LENGTH_LONG | V29 |
| T18 | x | impl FIX-026 (P1): MockedLocationTask + RoutePlaybackTask cancel-flag check | V25 |
| T19 | x | impl FIX-027 (P1): providers → CopyOnWriteArrayList | V26 |
| T20 | x | impl FIX-028 (P1): ServiceConnector.resumeAndBind for process-death recovery | V27 |
| T21 | x | impl FIX-029 (P1): geo intent parse error → showError | V30 |
| T22 | x | impl FIX-030 (P2): WebView saveState/restoreState | V28 |
| T6 | x | impl V19: signature-permission gate pre-33 + RECEIVER_NOT_EXPORTED on 33+ | V19 |
| T7 | . | commit staged + untracked work as v2.4.0 baseline | C3 |
| T8 | . | assembleRelease (signing config required) | C5 |
| T9 | . | translate 5 new English strings to da/de/es/pt-rBR/ru/zh | I.manifest |
| T10 | . | resolve `app_name` translatable=false ↔ da/de/zh translated mismatch | (lint warn) |

## §B — Bugs

| id | date | cause | fix |
|---|---|---|---|
| B1 | 2026-05-20 | WebAppInterface.setPosition substring outside try-catch; malformed JS payload → StringIndexOutOfBoundsException on UI thread | FIX-020 + V22 |
| B2 | 2026-05-20 | ACTION_STOP receiver registered without export flag pre-33 → cross-app DoS injection surface | FIX-021 + V19 (revised) |
| B3 | 2026-05-20 | 21 lint warnings accepted as "cosmetic" by prior pipeline → drift surface (5 strings defined-not-wired, hardcoded i18n leaks) | FIX-022 + V21 |
| B4 | 2026-05-20 | Speed sent to Location.setSpeed was 1000× too small (divided by ms not s) — broke speed-aware consumers (FusedLocationProvider rate-of-change check) | FIX-023 + V23 |
| B5 | 2026-05-20 | mockCount=0 (infinite mock) computed endTime in past → UI desync: button "Apply" while service mocking | FIX-024 + V24 |
| B6 | 2026-05-20 | MockedLocationTask.run pushed location after Stop because Timer.cancel() does not interrupt running task | FIX-026 + V25 |
| B7 | 2026-05-20 | providers ArrayList iterated by Timer thread + mutated by main thread → ConcurrentModificationException risk | FIX-027 + V26 |
| B8 | 2026-05-20 | Process death recovery used bind-only — onStartCommand never fired → resumeFromPrefsIfActive never ran → service bound but not mocking | FIX-028 + V27 |
| B9 | 2026-05-20 | WebView state lost on rotate (no onSaveInstanceState) → marker reset, user lost position | FIX-030 + V28 |
| B10 | 2026-05-20 | Geo intent parse failure logged but no user feedback → user thought VIEW intent ignored | FIX-029 + V30 |

---

**Spec OK?** Suggest edits or `/build` to tackle T4/T5/T6/T7. `/spec amend §V.19` to revise the pre-33 receiver invariant.
