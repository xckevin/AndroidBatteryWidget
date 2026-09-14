# Samsung physical-device validation — 2026-09-09

Status: the phone was subsequently unlocked during the UI refresh. Home-screen add, resize and tap checks are now complete; see the UI follow-up below. No PIN was requested or entered.

## Device and scope

- Galaxy S23 Ultra (SM-S918B), Android 15 / API 35, One UI 7.
- The target app was absent before testing. Installed debug version 3.3 (33), targetSdk 36.
- Actual phone battery: 100%, FULL, connected to AC. No simulated battery values, system clock changes, forced Doze or global power-setting changes.
- User has no Bluetooth accessory available. The app sees zero paired/visible accessories after permission is granted. Samsung's internal S Pen entry is not exposed as an app-readable battery accessory.
- Production 3.2 review and Play products were not changed. No purchase was made.

## Results so far

| Area | Result | Evidence / limit |
|---|---|---|
| Baseline instrumentation on Samsung | PASS, 14 tests, no skips, 49.402 s | `play/artifacts/3.3-33/samsung/instrumentation-current.log` |
| Actual phone battery / charging display | PASS | Overview shows 100% and 已充满 |
| Manual refresh | PASS | Refresh updates the displayed check time |
| Continuous monitoring / process restart | PASS | Enabled in settings; actual service dump reports running; notification permission grant + process restart restores it |
| Notification runtime-permission loss / recovery | PASS via ADB permission changes | Deny → no service; regrant + force-stop/reopen → enabled/running, no start error. In-app system-settings touch flow remains pending |
| Periodic refresh | PASS for screen-interactive state | After process restart, refresh count 1 → 2; elapsed timestamps differ by 59.958 s. This is not a screen-off periodic or Doze result |
| Bluetooth permission denial and recovery | PASS | Deny → return to recovery action → allow → explicit empty state |
| No-accessory UI / settings entry | PASS | App shows paired 0 / visible 0 and working system-settings entry |
| Screen-off / wake recovery | PASS for a short observation only | Service remained running; refresh count 10 → 11 on wake, elapsed timestamps differ by 51.392 s. No battery change or long Doze claim |
| Configuration scrolling | FIXED; physical scroll verified | All sections and both actions are reachable in the refreshed UI |
| Ring percentage legibility | FIXED, device-rendered image verified | Text inherited STROKE from the ring; now FILL and font-metric centered. `samsung-pro-combined.png` clearly shows 100% |
| Fixed-build component tests | PASS, 9 tests, no skips, 1.722 s | Notifications, receipt storage, per-widget preferences and host binding; `instrumentation-fixed-components.log` |
| Fixed-build JVM / Lint | PASS | 33 JVM tests, no failures/skips; Lint 0 errors / 194 warnings |
| Refreshed-build portrait / landscape touch reachability | PASS, 2 tests, no skips, 93.234 s | Actual Espresso scroll/display/click plus RESULT_OK/widget ID; `ui-refresh/instrumentation-samsung-window-sync.log` |
| One UI home-screen add / tap / resize | PASS | Saved defaults, confirmed One UI add dialog, dragged widget wider, tapped phone area to open monitoring settings |
| Pro real-battery reminder / deduplication / quiet hours | PASS in QA, 3 tests total including rendering, 2.925 s | True sticky FULL/plugged/100% checked against BatteryRepo. Production alert entry sends once; repeated refresh does not resend; all-day quiet suppresses and disabling it sends |
| Pro compact / wide combined rendering | PASS in QA device AppWidgetHost | 100×100 dp falls back to phone; 240×120 dp shows phone plus selected-device empty state. Real view and text assertions; rendered PNG visually checked. This is not a One UI launcher gesture test |
| Pro settings UI / threshold input / save / rotation | PARTIAL | Refreshed form display checked on Samsung; custom input, keyboard avoidance and theme-change draft retention checked on emulator. Full enabled-reminder settings/save flow on Samsung still needs an entitled test setup. The phone is now unlocked; QA entitlement does not establish a Play purchase |
| Bluetooth battery / aliases / ordering / reconnect / low-battery notification | NOT TESTED on hardware | No accessory available; existing synthetic/component tests are not hardware evidence |
| Real Play purchase / restore / refund | NOT TESTED | Product is not activated; QA entitlement is not a purchase |

## Fixes and artifact

- `app/src/main/res/layout/fragment_battery_widget_config.xml`: all settings and actions are inside the scroll container.
- `app/src/main/java/com/github/xckevin927/android/battery/widget/utils/Utils.java`: draw filled, vertically centered percentage text.
- `app/src/androidTest/java/com/github/xckevin927/android/battery/widget/WidgetReachabilityTest.java`: portrait and landscape tests use scrolling and actual Espresso clicks, avoiding the old direct-performClick blind spot.

Installed fixed debug APK: `play/artifacts/3.3-33/samsung/battery-widget-3.3-33-samsung-tested-debug.apk`.
SHA-256: `396c2a7cd00880ab57ee79f5e6dd8a0545d8489214a5608f65fc3b587bc3c0e7`.
The filename identifies the Samsung test candidate; pending checks above are not implied to have passed.

QA copy: `/tmp/battery-widget-samsung-qa`, application ID `com.github.xckevin927.android.battery.widget.qa`, launcher label `Battery Widget QA`, version `3.3-QA`. Entitlement bypass exists only in this temporary copy; analytics/crash reporting collection is disabled there. The QA application and its test APK have been uninstalled after validation. Explicitly labelled QA-only APKs, test source and checksums are preserved in the ignored `play/artifacts/3.3-33/samsung/qa-only/` folder for resumed testing.

Screenshots and layout snapshots are local ignored artifacts under `play/artifacts/3.3-33/samsung/`; they are not store assets. The earlier PIN-lock blocker was resolved when the phone was unlocked during the UI task.

## Final device state and remaining work

- Normal fixed debug application remains installed; continuous monitoring is enabled and recovered after the permission test. Notification and Bluetooth connection permissions are granted, matching the authorized test setup.
- Temporary QA application and QA instrumentation package removed. Normal application's temporary widget-host bind grant revoked. One phone-battery widget was subsequently added to One UI page 2 and widened for the UI validation; it remains available for the user to review.
- No fatal exception, ANR marker or native-library load error in the captured final normal-app process log (177 lines); this only covers the observed process/session. FreeReflection remains in the build.
- The subsequent UI task completed both physical Espresso reachability cases, Pro purchase-unavailable/draft-return flows, manual configuration scroll/default-save and One UI add/tap/resize. Full Pro reminder input/save still needs an entitled test setup; no Play purchase is implied.
- Longer overnight / unplugged Doze tests, battery transitions and Bluetooth accessory scenarios still need appropriate physical conditions. Short charging observations do not establish those results.

## UI follow-up

The six-page UI refresh and its latest installed APK are documented in [ui-refresh-2026-09-09.md](ui-refresh-2026-09-09.md). Actual touch checks cover overview, configuration scrolling, saved defaults, One UI add confirmation, widget resize, widget click through to monitoring settings, and the reminder page. The normal Pro gate remains active. The UI does not establish a paid entitlement or a real transaction.

The first landscape Espresso attempts failed because the new Activity was ready while the system rotation window was still settling. Merely waiting for Activity recreation was insufficient. The test now waits for the new laid-out instance and then bounded window-event idle before the original Espresso scroll/display/click; it still asserts RESULT_OK and the widget ID. Logs retain both unsuccessful attempts and the synchronized run for traceability.
