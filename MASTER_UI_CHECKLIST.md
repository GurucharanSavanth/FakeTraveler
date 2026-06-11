# FakeTraveler — Master UI Audit Checklist

*Generated 2026-06-10 via 6-dimension multi-agent audit (96 agents). Note: dead **string-resource** verification was incomplete (agent session limit); string strips below were re-verified manually.*

## Summary

**Overall UI health: strong, with 2 confirmed-dead settings fields and 1 confirmed map-zoom bug.**

The FakeTraveler UI was audited across 6 dimensions. The vast majority of interactive elements are correctly wired. The only real problems are: (a) two settings EditTexts (`et_Accuracy`, `et_Altitude`) that exist in the layout but have no backing handler, (b) one orphaned hidden label (`et_DMockLatLon`), and (c) a bookmark-zoom-restore bug in the map pipeline.

| Metric | Count |
|---|---|
| Elements audited (across all 6 dimensions) | ~190 |
| ✅ OK / functional | ~183 |
| ⚠️ Info-level (intentional hide / compat, no action) | 5 (`search_input_layout`, `detection_btn`, `et_DMockLat`, `et_DMockLon`, `cb_MockSpeed`) |
| 💀 Confirmed dead (verdict reallyDead=true) | 3 (`et_Accuracy`, `et_Altitude`, `et_DMockLatLon`) |
| 🐞 Real bug (broken behavior) | 1 (bookmark zoom restore not passed to JS) |

Note: the Map+Zoom findings JSON was truncated at the `zoom persistence restore` element, but the dimension summary fully describes the bookmark-zoom bug, which is captured below.

## Master Checklist

### Main screen (MainActivity)

| Element | Type | Wired to | Status | Note |
|---|---|---|---|---|
| button_applyStop | button | applyLocation() / requestStop() (rewired in changeButtonToStop) | ✅ | activity_main_content.xml:130-141 |
| my_location_fab | fab | setLatLng(lat,lng,LOAD) | ✅ | :112-121 |
| bookmark_list_btn | button | showBookmarksSheet() via wireBookmarkButtons() | ✅ | :47-55 |
| bookmark_save_btn | button | showSaveBookmarkDialog() via wireBookmarkButtons() | ✅ | :302-310 |
| button_settings | button | showSettingsSheet() | ✅ | :57-65 |
| more_btn | button | showOverflowMenu(View) (null-checked) | ✅ | :67-75 |
| quick_frequency_chip | chip | showSettingsSheet() + updateQuickSettingsChips() | ✅ | :274-281 |
| quick_count_chip | chip | showSettingsSheet() + updateQuickSettingsChips() | ✅ | :283-290 |
| quick_accuracy_chip | chip | showSettingsSheet() | ✅ | :292-299 |
| editTextLat | edittext | LocationInputHandler → setLatLng(CHANGE_FROM_EDITTEXT) | ✅ | :222-228 |
| editTextLng | edittext | LocationInputHandler → setLatLng(CHANGE_FROM_EDITTEXT) | ✅ | :246-252 |
| status_chip | chip | updateStatusChip(boolean) | ✅ | :95-110 |
| location_metadata | string/textview | updateLocationMetadata() from setLatLng() | ✅ | :256-265 |
| search_input_layout | container | (none) | ⚠️ | visibility=gone:184, intentionally hidden, not interactive |
| detection_btn | button | showDetectionSheet() via wireDetectionButton() | ⚠️ | visibility=gone, wired but unreachable (intentional placeholder) |
| webView0 | container | WebAppInterface bridge / WebViewSetup.configure() | ✅ | :11-15 |
| top_app_bar | container | hosts bookmark_list_btn, button_settings, more_btn | ✅ | :17-76 |
| top_controls_row | container | hosts status_chip, my_location_fab | ✅ | :90-122 |
| apply_action_row | container | hosts button_applyStop | ✅ | :124-142 |
| main_control_stack | container | hosts coordinate_card inputs/chips | ✅ | :145-315 |

### Bottom sheets

| Element | Type | Wired to | Status | Note |
|---|---|---|---|---|
| BookmarksBottomSheet.onAddCurrentRequested | method | onViewCreated callback | ✅ | :76-78 |
| bookmark_add_current_btn | button | BookmarksBottomSheet:76 | ✅ | |
| bookmark_list | recycler | BookmarkAdapter onTap/onLongPress | ✅ | |
| BookmarkAdapter.onTap | method | listener.onTap (setOnClickListener) | ✅ | :62 |
| BookmarkAdapter.onLongPress | method | listener.onLongPress (setOnLongClickListener) | ✅ | :63 |
| ItemTouchHelper.swipe_to_delete | method | runDb → bookmarkDao.delete | ✅ | :92-112 |
| bookmark_empty | container | isEmpty visibility toggle:89 | ✅ | |
| PrivacyGuardBottomSheet (pg_run) | method | pg_run_btn setOnClickListener | ✅ | :47-49 |
| pg_run_btn | button | runScan:52 | ✅ | |
| pg_risk_badge | textview | bindReport:69-75 | ✅ | |
| pg_recommendation | textview | bindReport:71-74 | ✅ | |
| pg_breakdown_host | container | bindReport dynamic rows:80-97 | ✅ | |
| item_privacy_check | layout | bindReport inflate/bind:81-96 | ✅ | |
| check_action (privacy) | button | openSettings:92 | ✅ | |
| ExifCleanerBottomSheet (auto switch) | method | exif_auto_switch listener | ✅ | :74-81 |
| exif_auto_switch | toggle | onViewCreated:77-81 | ✅ | |
| exif_clean_btn | button | onViewCreated:83-84 | ✅ | |
| exif_pick_btn | button | onViewCreated:86-87 | ✅ | |
| exif_list | recycler | ExifCleanedAdapter LiveData:90-96 | ✅ | |
| exif_empty | container | isEmpty toggle:94 | ✅ | |
| GeoFenceLabBottomSheet (add/events) | method | geofence_add_btn / geofence_events_btn | ✅ | :71-74 |
| geofence_add_btn | button | showFenceDialog:71 | ✅ | |
| geofence_events_btn | button | GeoFenceEventBottomSheet.show:72-73 | ✅ | |
| geofence_list | recycler | GeoFenceAdapter onToggleActive/onEdit/onDelete | ✅ | |
| GeoFenceAdapter.onToggleActive | method | setOnCheckedChangeListener | ✅ | :60-62 |
| GeoFenceAdapter.onEdit | method | itemView.setOnClickListener | ✅ | :63 |
| GeoFenceAdapter.onDelete | method | delete button click | ✅ | :64 |
| showFenceDialog fields | edittext | setPositiveButton callback:130-152 | ✅ | numeric-validated |
| geofence_empty | container | isEmpty toggle:80 | ✅ | |
| wipe_now_btn | button | confirmWipe:70 | ✅ | |
| wipe_emergency_btn | button | emergencyWipe:71 | ✅ | |
| wipe_cb_sessions | checkbox | optionsFrom checked:81 | ✅ | |
| wipe_cb_geofence | checkbox | optionsFrom checked:82 | ✅ | |
| wipe_cb_permissions | checkbox | optionsFrom checked:83 | ✅ | |
| wipe_cb_exif | checkbox | optionsFrom checked:84 | ✅ | |
| wipe_cb_appdata | checkbox | optionsFrom checked:85 | ✅ | |
| wipe_schedule_switch | toggle | onViewCreated:62-67 | ✅ | |
| wipe_log_list | recycler | WipeLogAdapter LiveData:73-75 | ✅ | |
| session_clear_all_btn | button | confirmClearAll:64 | ✅ | |
| session_list | recycler | SessionAdapter onReplay/onLongPress | ✅ | |
| SessionAdapter.onReplay | method | item click → listener.onReplay | ✅ | :79-81 |
| SessionAdapter.onLongPress | method | confirmDelete dialog | ✅ | :85-95 |
| session_empty | container | isEmpty toggle:74 | ✅ | |
| DetectionTest run_btn | button | runTest:47 | ✅ | |
| risk_badge | textview | bindReport:69-79 | ✅ | |
| risk_recommendation | textview | bindReport:70-79 | ✅ | |
| breakdown_host | container | bindReport dynamic rows:84-92 | ✅ | |
| item_detection_check | layout | bindReport inflate/bind:85-92 | ✅ | |
| DebugConsole btn_export | button | exportLog:57-58 | ✅ | |
| DebugConsole btn_clear | button | onViewCreated:59-62 | ✅ | |
| DebugConsole btn_stress | button | onViewCreated:64-75 | ✅ | |
| log_list | recycler | LogAdapter append:55,62,100 | ✅ | |
| LogAdapter.append | method | onEntry real-time listener:96-102 | ✅ | :34-36 |
| route_editor_add_btn | button | showAddDialog:83 | ✅ | |
| route_editor_save_btn | button | save:84 | ✅ | |
| route_editor_name | edittext | save validation:159 | ✅ | |
| route_editor_list | recycler | WaypointAdapter drag/delete | ✅ | |
| WaypointAdapter.onDelete | method | delete button click | ✅ | :62-64 |
| WaypointAdapter.onDragStart | method | drag handle touch | ✅ | :66-69 |
| waypoint_drag_handle | button | onBindViewHolder:66-69 | ✅ | |
| waypoint_delete_btn | button | onBindViewHolder:62-65 | ✅ | |
| route_editor_empty | textview | refreshEmpty toggle:101-102 | ✅ | |
| geofence_event_export_btn | button | exportCsv:53 | ✅ | |
| geofence_event_list | recycler | GeoFenceEventAdapter LiveData:55-62 | ✅ | |
| geofence_event_empty | container | isEmpty toggle:61 | ✅ | |
| permission_detail_ack_btn | button | onViewCreated:69-75 | ✅ | |
| permission_detail_list | recycler | PermissionTimelineAdapter async:61-67 | ✅ | |
| permission_detail_title | textview | setText:51-52 | ✅ | |
| permission_detail_pkg | textview | setText:53 | ✅ | |
| item_bookmark.bookmark_name | textview | BookmarkAdapter:60 | ✅ | |
| item_bookmark.bookmark_coords | textview | BookmarkAdapter:61 | ✅ | |
| item_session.session_label | textview | SessionAdapter bind | ✅ | |
| item_session.session_replay_btn | button | listener.onReplay | ✅ | |
| item_wipe_log.wipe_log_title | textview | WipeLogAdapter bind | ✅ | |
| item_wipe_log.wipe_log_status | textview | WipeLogAdapter bind | ✅ | |
| item_geofence.geofence_name | textview | GeoFenceAdapter:51 | ✅ | |
| item_geofence.geofence_active | toggle | GeoFenceAdapter:60-62 | ✅ | |
| item_geofence.geofence_delete_btn | button | GeoFenceAdapter:64 | ✅ | |
| check_status/label/detail (privacy) | textview | bindReport:88/86/87 | ✅ | |
| check_status/label/detail (detection) | textview | bindReport:91/89/90 | ✅ | |
| item_log_entry.log_ts/level/msg | textview | LogAdapter:48/49/50 | ✅ | |

### Menu / navigation

| Element | Type | Wired to | Status | Note |
|---|---|---|---|---|
| action_bookmarks | menu-item | showBookmarksSheet() MainActivity:913 | ✅ | menu_main.xml:6 |
| action_settings | menu-item | showSettingsSheet():917 | ✅ | :13 |
| action_about | menu-item | startActivity(AboutActivity):921 | ✅ | :20 |
| action_help | menu-item | startActivity(AboutActivity):921 | ✅ | :26 |
| action_session_history | menu-item | showModuleSheet(SessionHistoryBottomSheet):925 | ✅ | :32 |
| action_route_lab | menu-item | openRouteLab() → RouteLabActivity:929 | ✅ | :37 |
| action_geofence | menu-item | showModuleSheet(GeoFenceLabBottomSheet):933 | ✅ | :42 |
| action_permission_drift | menu-item | startActivity(PermissionDriftActivity):937 | ✅ | :47 |
| action_exif | menu-item | showModuleSheet(ExifCleanerBottomSheet):941 | ✅ | :52 |
| action_privacy_wipe | menu-item | showModuleSheet(PrivacyWipeBottomSheet):945 | ✅ | :57 |
| action_evidence | menu-item | startActivity(EvidenceExportActivity):953 | ✅ | :62 |
| action_privacy_guard | menu-item | showPrivacyGuardSheet():949 | ✅ | :67 |
| AboutActivity | activity | action_about / action_help | ✅ | Manifest:100 |
| RouteLabActivity | activity | action_route_lab | ✅ | Manifest:105 |
| PermissionDriftActivity | activity | action_permission_drift | ✅ | Manifest:109 |
| EvidenceExportActivity | activity | action_evidence | ✅ | Manifest:113 |

### Settings / toggles (FeatureFlag + SettingsBottomSheet)

| Element | Type | Wired to | Status | Note |
|---|---|---|---|---|
| SESSION_HISTORY → cb_feat_session_history | flag/toggle | wireCheckBox:303 | ✅ | |
| ROUTE_LAB → cb_feat_route_lab | flag/toggle | wireCheckBox:305 | ✅ | |
| GEOFENCE_LAB → cb_feat_geofence | flag/toggle | wireCheckBox:307 | ✅ | |
| PERMISSION_DRIFT → cb_feat_perm_drift | flag/toggle | wireCheckBox:309 | ✅ | |
| EXIF_CLEANER → cb_feat_exif | flag/toggle | wireCheckBox:311 | ✅ | |
| PRIVACY_WIPE → cb_feat_privacy_wipe | flag/toggle | wireCheckBox:313 | ✅ | |
| EVIDENCE_EXPORT → cb_feat_evidence | flag/toggle | wireCheckBox:315 | ✅ | |
| PRIVACY_GUARD → cb_feat_privacy_guard | flag/toggle | wireCheckBox:317 | ✅ | |
| slider_MockFrequency | slider | wireTimingSliders:87 | ✅ | :69-75 |
| slider_MockCount | slider | wireTimingSliders:87 | ✅ | :120-126 |
| et_MockFrequency | edittext | wireTimingSliders / wireIntField:87 | ✅ | :87-94 |
| et_MockCount | edittext | wireTimingSliders / wireIntField:87 | ✅ | :138-145 |
| mock_frequency_value | textview | slider label sync:217-226 | ✅ | |
| mock_count_value | textview | slider label sync:217-226 | ✅ | |
| et_DMockLat | edittext | wireDoubleField:83 | ⚠️ | hidden compat (gone) but actively wired |
| et_DMockLon | edittext | wireDoubleField:84 | ⚠️ | hidden compat (gone) but actively wired |
| cb_MockSpeed | checkbox | wireCheckBox:88 | ⚠️ | hidden compat (gone) but actively wired |
| et_MapProvider | edittext | wireMapProvider:272 | ✅ | |
| et_SessionLabel | edittext | wireStringField:321 | ✅ | |
| cb_RestoreAfterBoot | toggle | wireRestoreAfterBoot:292 | ✅ | |
| cb_StrictRadioMode | toggle | wireCheckBox:91 | ✅ | |
| cb_SimulateAltitude | toggle | wireCheckBox:319 | ✅ | |
| cb_SimulateAccuracy | toggle | wireCheckBox:320 | ✅ | |
| oem_card_fix_button | button | wireOemCard:123 | ✅ | |
| btn_OemHelper | button | wireOemHelper:363 | ✅ | |
| settings_reset_defaults | button | wireResetDefaults:342 | ✅ | |
| tv_LeafletLicense | textview | wireLeafletLicense:141 | ✅ | |
| tv_AppVersion | textview | wireVersionFooter:107 | ✅ | |
| **et_Accuracy** | edittext | (none) | 💀 | :177 — visible field, no wireDoubleField, no pref |
| **et_Altitude** | edittext | (none) | 💀 | :201 — visible field, no wireDoubleField, no pref |
| **et_DMockLatLon** | textview/label | (none) | 💀 | :489 — orphan label in gone container |

### Zoom / map

| Element | Type | Wired to | Status | Note |
|---|---|---|---|---|
| zoomControl | button | map.on('zoomend', onZoomEnd) | ✅ | init.js:12 |
| pinch-zoom | gesture | zoomend (Leaflet default) | ✅ | init.js:11-12 |
| onZoomEnd | method | Android.setZoom(map.getZoom()) | ✅ | init.js:80-82 |
| Android.setZoom | method | init.js:81; bounds 0-22, NaN/Inf rejected | ✅ | WebAppInterface.java:59-74 |
| Android.setPosition | method | init.js:78 (long-press); bounds-checked | ✅ | WebAppInterface.java:32-57 |
| onMapClick | method | Android.setPosition (contextmenu/long-press) | ✅ | init.js:72-79 |
| map marker placement | other | onMapClick + setOnMap | ✅ | init.js:72-76, 87-89 |
| zoom persistence save | other | saveSettings() from setZoom:550 / setLatLng:562 | ✅ | MainActivity.java:362 |
| zoom persistence restore | other | loadSharedPrefs() (findings JSON truncated) | ✅ | MainActivity.java:341 |
| bookmark zoom restore | flow | onBookmarkSelected:658 sets zoom var but setLatLng→setMapMarker:525-526 never passes zoom to JS | 🐞 | restored zoom ignored; map keeps current zoom |
| setOnMap programmatic zoom | flow | map.setView fires no event back to update stored zoom | 🐞 (minor) | no zoom round-trip on programmatic moves |

## Confirmed dead (strip these)

All three flagged items were adversarially verified `reallyDead=true`.

1. **`et_Accuracy`** — `bottom_sheet_settings_content.xml:177`. Visible `TextInputLayout` EditText (hint `@string/Settings_Accuracy_Label`, `inputType=numberDecimal`) with ZERO `R.id.et_Accuracy` references, ZERO `wireDoubleField`/`wireIntField` calls, ZERO backing SharedPreferences double key, ZERO reflection. Added in commit `9e9db84` (UI v2.0) as deferred scaffolding, never wired. **Recommendation: strip the EditText + its `TextInputLayout` wrapper.**

2. **`et_Altitude`** — `bottom_sheet_settings_content.xml:201`. Identical pattern to `et_Accuracy`: only the layout definition references it, no Java reference, no wiring, no pref. (`simulateAltitude` checkbox exists separately and is wired — this *input field* is not.) **Recommendation: strip the EditText + its `TextInputLayout` wrapper.**

3. **`et_DMockLatLon`** — `bottom_sheet_settings_content.xml:489`. A TextView label inside the `settings_compatibility_values` LinearLayout (`visibility=gone`, `hidden_compat_size`=0dp). The "backward compat" comment above it is factually false: no Java references `R.id.et_DMockLatLon`, `R.string.ActivityMore_DMockLatLon` (Java side), `settings_compatibility_values`, or `hidden_compat_size`. The actually-wired compat siblings are `et_DMockLat` (line 490) and `et_DMockLon` (line 491) — not this label. **Recommendation: strip the TextView at line 489.** (Leave the `et_DMockLat`/`et_DMockLon`/`cb_MockSpeed` siblings — they ARE wired.)

### False positives (keep)

None. No flagged item returned a "keep" verdict; every adversarial verdict in the findings was `reallyDead=true`. The `info`-severity hidden-but-wired items (`et_DMockLat`, `et_DMockLon`, `cb_MockSpeed`, `search_input_layout`, `detection_btn`) were never flagged as dead and must NOT be stripped — they are either actively wired through the compat layer or intentional hidden placeholders.

## Real bugs / missing wiring (fix these)

1. **Bookmark zoom restore is broken** (Map+Zoom dimension, severity: critical per summary).
   - Path: `onBookmarkSelected` (`MainActivity.java:658`) updates the local `zoom` variable, but `setLatLng()` → `setMapMarker()` (`MainActivity.java:525-526`) only emits lat/lng to the WebView; it never passes the zoom to JS `setOnMap`. Result: selecting a bookmark moves the marker but the map stays at the current zoom instead of the bookmark's saved zoom.
   - Fix: thread `zoom` through `setLatLng`/`setMapMarker` into the `setOnMap` JS call (e.g. `setOnMap(lat, lng, zoom)` and `map.setView([lat,lng], zoom)`), so a restored bookmark applies its saved zoom.

2. **`et_Accuracy` / `et_Altitude` have no handler** (FeatureFlag-Settings dimension, severity: major).
   - These are visible, user-typeable fields that silently do nothing — a UX bug as much as dead code. The chosen resolution per the adversarial verdict is **strip** (no pref backing exists to wire to). If the intent is to *keep* the feature, the alternative fix would be to add `wireDoubleField` calls + new SharedPreferences double keys for accuracy/altitude and feed them into the mocked `Location`. Given zero backing infrastructure, stripping is the recommended path.

3. **Programmatic `setView` does not round-trip zoom** (Map+Zoom dimension, minor).
   - When Java calls `setOnMap`, Leaflet's `map.setView` does not push the resulting zoom back through `Android.setZoom`. Low impact (only affects programmatic moves), but worth normalizing once the bookmark-zoom fix threads zoom end-to-end.

## Fix list

### SAFE-MECHANICAL (strip dead UI; no behavior change)
1. `app/src/main/res/layout/bottom_sheet_settings_content.xml:177` — remove the `et_Accuracy` EditText and its enclosing `TextInputLayout`.
2. `app/src/main/res/layout/bottom_sheet_settings_content.xml:201` — remove the `et_Altitude` EditText and its enclosing `TextInputLayout`.
3. `app/src/main/res/layout/bottom_sheet_settings_content.xml:489` — remove the orphan `et_DMockLatLon` TextView (keep siblings `et_DMockLat`:490, `et_DMockLon`:491, `cb_MockSpeed`:492 — they are wired).
4. After stripping (1)–(3), remove now-unused string resources if they become orphaned: `@string/Settings_Accuracy_Label`, the Altitude label string, and (only if no other reference) `ActivityMore_DMockLatLon` across `values/strings.xml` + localized variants. Verify with a grep before deleting each string.
5. Run `./gradlew lintDebug` (SPEC §V21 requires zero warnings) to confirm no `unused`/`missing id` regressions after removal.

### BEHAVIORAL (wire a handler)
6. `app/src/main/java/cl/coders/faketraveler/MainActivity.java` (~525-526, 658) + `app/src/main/assets/.../init.js` (setOnMap ~87-89) — thread the bookmark zoom through `setLatLng`/`setMapMarker` into the JS `setOnMap(lat, lng, zoom)` call so `onBookmarkSelected` actually restores the saved zoom. This is the one true functional bug.
7. (Optional, only if accuracy/altitude inputs are wanted rather than stripped) Add `wireDoubleField` wiring + new double SharedPreferences keys for `et_Accuracy` / `et_Altitude` in `SettingsBottomSheet.java` and feed them into the mocked `Location` accuracy/altitude. Mutually exclusive with fix items (1)–(2); default recommendation is to strip, not wire.
8. (Optional, minor) Normalize programmatic zoom round-trip: have `setOnMap` in `init.js` re-emit `Android.setZoom(map.getZoom())` after `map.setView`, so persisted zoom stays consistent on programmatic moves.