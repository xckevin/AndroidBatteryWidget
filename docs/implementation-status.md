# High / medium feature implementation

User instruction 2026-09-09: implement all high/medium items from the functional review, then explore Google Play paid unlocking; medium features are candidates for a one-time upgrade. Do not submit the old release while implementing. The earlier requirement to retain FreeReflection was explicitly superseded on 2026-09-11: use LSPosed AndroidHiddenApiBypass instead. History charts and privacy controls were later-stage recommendations, outside this iteration.

## Work ownership

- Root: BatteryRepo, PhoneBatteryState/BtDeviceState and freshness, live events, monitoring status/home entry, integration, manifest/build, validation; then billing feasibility and implementation if practical.
- widget_customization: widget preferences/config flow, renderers/providers, per-widget choices, combined responsive widget.
- data_safety_review (reused agent): Bluetooth page + DevicePreferences.
- release_artifact_review (reused agent): reminders engine/settings/notifications/tests.

## Shared contracts

- `BatteryRepo.refresh(reason)`, `getBtSnapshot()` pure cache, `addListener(Runnable)` / `removeListener(Runnable)`.
- `PhoneBatteryState`: previous methods plus `getStatus()`, `isCharging()`, `isPlugged()`, `getCheckedAtMillis()`. Unknown percentage is -1.
- `BtDeviceState`: previous first three parameters plus defaulted `lastCheckedAt`, `lastSuccessfulReadAt`, `source`, `status`, `failureReason`. Times are wall-clock milliseconds. Sources: framework/gatt/unknown; status: available/cached/stale/unknown/unsupported/disconnected/permission_denied.
- `BatteryStatusText.phone(context,state)`, `bluetooth(context,state)`, `freshness(context,state)`.
- `DevicePreferences.displayName(context,state)`, `visibleDevices(context,list)`, `allDevices(context,list)`, all Java callable.
- `BatteryAlerts.evaluate(context,phone,devices)` Java callable; invoked from refreshed repository state.
- Widget tap: SettingsActivity for monitor status; TabActivity extras `open_tab=bluetooth`, optional `device_address` for device page.

## Completion checklist

- [x] Fix widget configuration result / draft isolation / charging states (API36 save/cancel/recreate tests passed).
- [x] Actual monitoring status, recovery and manual refresh (actual FGS start/background battery event verified).
- [x] Device selection, alias and ordering; live UI (pure ordering tests passed; real devices still hardware validation).
- [x] Freshness / failure / unavailable state (framework cached value has independent observation/check clocks).
- [x] Per-widget settings, tap actions and migration (old shared appearance frozen per instance; device focus consumes intent once).
- [x] Combined responsive widget (horizontal/vertical/compact modes, explicit all-visible/selected device modes).
- [x] Threshold reminders, quiet hours and persistent deduplication (8 engine tests + 2 actual NotificationManager tests passed).
- [x] Meaningful unit tests, build/lint, isolated emulator flow checks (23 JVM + 9 instrumentation tests; no skips; Lint 0 errors).
- [x] Only after feature verification: official Play Billing research, account capability check, paid unlock decision and local implementation (store activation / actual Play transaction tests remain a release step).

Historical Console read (2026-09-09): production32 (3.2) was under review. Latest read (2026-09-11): production32 is published. The separate Billing test setup is recorded below.

## Earlier verification steps (superseded by the final results below)

- Debug APK and AndroidTest APK build, 19 JVM tests, Lint: 0 errors / 188 warnings.
- API36 isolated emulator `emulator-5582`: 8 instrumentation tests passed (93.256s), no skips. Covers per-ID preferences, migration, draft isolation, real AppWidget binding, narrow/wide updates, launcher configuration RESULT_OK/ID and RESULT_CANCELED, draft recreation, actual notification delivery and persistent cycle deduplication. AndroidX test dependencies updated to ext JUnit 1.2.1 / Espresso 3.6.1 to handle modern receiver flags.
- Manual: overview renders 42% plugged/not charging correctly; settings switches to actual running after starting FGS; with app backgrounded battery event 42→43 refreshes service snapshot.
- Final review followups in progress: framework cached-reading timestamps; notification-off + screen-off service exit; explicit all-devices selection; responsive combined layout; reminder monitoring explanation. Rebuild targeted checks after these fixes.
- No Play Billing research/implementation or remote product changes yet; wait until above feature work is verified.
- Physical Samsung device has not been used or modified. Test emulator is a new temporary AVD under /tmp/battery-widget-33-test.

## Feature phase completed

All high/medium capabilities are implemented before starting Play Billing research. Final artifact/log snapshot: `play/artifacts/3.3-33/features-before-billing/`. Regression confirmed notification-channel blocking + immediate screen-off now removes WidgetUpdateService (previous APK kept it alive). Manual Bluetooth permission request → grant → empty paired-device recovery view passed; monitoring recovery/start and background battery events passed. FreeReflection is retained. Bluetooth hardware/OEM long-duration behavior cannot be proven by the emulator.

## Pro integration completed

See `docs/pro-unlock.md` for the free/paid boundary, account checks, product proposal and remaining Play launch steps. Combined widgets and reminders use a single non-consumable `battery_widget_pro` entitlement. Core monitoring, manual refresh/recovery, device management and individual widgets remain free. Pending purchases do not unlock; verified receipts are kept outside Android backup; UI and execution/rendering both enforce entitlement.

Final build passed: `assembleDebug`, `assembleDebugAndroidTest`, `testDebugUnitTest`, `lintRelease`, `bundleRelease`. **33 JVM tests + 14 API 36 instrumentation tests passed, no skips; Lint 0 errors / 194 warnings.** AAB structure validation passed; its decoded manifest confirms version33/3.3, min21, target36, Billing8.0.0 and BILLING permission. Independent review confirmed the fixes for stale queries, acknowledgement retry, pending-cache invalidation and immediate widget/reminder restoration.

Evidence and artifacts: `play/artifacts/3.3-33/with-pro/`. The APK is a debug build and the AAB is unsigned. Neither was uploaded or released. No Play product/price was activated and no real/test payment was made. Device tests used only the isolated emulator, never the user's Samsung phone.

## Samsung physical-device phase (2026-09-09)

The earlier statement that Samsung was untouched applies only to the emulator/Pro implementation phase above. The user subsequently requested Samsung testing. Galaxy S23 Ultra / Android 15 / One UI 7 is now under test. The first 14 instrumentation tests passed; actual touch testing then found a portrait configuration scrolling bug that direct view clicks had missed. The scroll container and ring-text rendering have been fixed; fixed-build 33 JVM tests, Lint (0 errors / 194 warnings), and 9 Samsung component tests pass. Three further QA-only Samsung tests using true full-battery readings and a real widget host also passed; the isolated QA package was then removed. Remaining unlocked-screen and launcher checks are tracked separately in `docs/samsung-validation-2026-09-09.md`. No Bluetooth accessory is available, and no real Play transaction is claimed.

## UI refresh completed (2026-09-09)

Overview, widget editor, Bluetooth devices, alerts, Pro and settings now share light/dark colors, typography, cards and action styles. Narrow screens, 150% font size, English text, landscape navigation and keyboard avoidance were visually checked. Final build: 33 JVM tests passed, Lint 0 errors / 228 warnings. The API 36 UI-stage suite passed 16 tests; final Samsung checks passed 2 Pro flows and 2 physical scroll/save flows (the latter required synchronizing system rotation before touch injection). One UI add, resize and widget tap also passed after the phone was unlocked. The latest debug APK and screenshots are listed in [ui-refresh-2026-09-09.md](ui-refresh-2026-09-09.md). No Google Play upload or paid-product activation was performed.

## Non-Bluetooth follow-up (2026-09-10)

Completed the executable non-Bluetooth follow-up on the isolated API 36 emulator; Samsung was not connected. Fixed restored quiet-hours pickers losing their confirmation callback, with start/end/cancel UI regression coverage. Final results: 33 JVM tests, 11 normal-app cases and 4 QA-only cases pass across the recorded final targeted runs; Lint 0 errors / 228 warnings. Runtime checks cover screen-off events, forced Doze recovery, notification permissions/channels, Stop action, actual reboot with monitoring enabled and disabled, Worker success without starting monitoring, reminder process-persistent deduplication, deferred channel recovery and notification navigation. QA packages and battery simulation were cleaned up. Physical long-duration behavior and actual Play transactions remain unverified. See [non-bluetooth-validation-2026-09-10.md](non-bluetooth-validation-2026-09-10.md) for evidence, failed attempts and the latest normal APK. No upload or paid-product activation occurred.

## Samsung reconnection and standby evidence (2026-09-10)

The Samsung phone subsequently reconnected. The latest UI passed 7 ordinary-package cases plus 1 isolated QA enabled-reminder form case; 2 earlier same-day QA cases also passed using the phone's real FULL/100% reading. Original preferences and One UI widget ID 8 were preserved, QA packages removed, and normal monitoring restored after instrumentation. The standby recorder recovered and finalized after about 6 h 27 min, with the same app PID and process-start identity at its endpoints. However, only two samples occurred inside the requested two-hour window, leaving a 7197-second gap; the final endpoint was powered and awake, and battery-statistics segments had reset. This establishes endpoint process survival and post-reconnection UI behavior, **not** a passing two-hour standby, natural Doze, refresh-timing, or energy result. See [Samsung validation and evidence limits](samsung-validation-2026-09-10.md). No production code or Play state changed during this reconnection phase.

## Play Billing setup (2026-09-11)

The owner confirmed US$1.99 for one-time Pro unlock. Created and activated `battery_widget_pro` / `buy` in 173 regions with English and Simplified Chinese metadata. Both Samsung Google accounts are now in the selected license-test list. The signed current AAB was validated and version 33 published to the internal track to unlock product creation; the track was subsequently resumed and the work account joined with owner approval. Production is still published version 32. The build command succeeded; existing 33-test results were reused by Gradle, Lint has 0 errors / 228 warnings, and an independent review verified the final AAB identity, certificate, normal receipt verification and retained FreeReflection.

Initially, live diagnostics returned `PRODUCT_NOT_FOUND`; this resolved on the 13:25 HKT recheck without another APK/catalog change. The normal Samsung app displayed IQD 2,600 and passed cancellation, test-card decline, approved purchase, both pending outcomes, acknowledgement, purchased-user offline launch, missing-receipt recovery/manual restore, and test refund with online revocation. Actual alert and combined-widget saves passed in locked and purchased states (2 cases each, no skips). The earlier non-buyer offline/recreation test also passed. The final free test purchase is PURCHASED and acknowledged=true; Pro is unlocked. No real charge occurred.

Original business preferences and One UI widget ID 8 were preserved, temporary receipt backups removed, Wi-Fi restored, and monitoring is enabled/running without a start error. Only opt-in test code and reports changed during these live Billing checks; production Billing logic and FreeReflection were unchanged. The declined-payment message/retry UX issue remains unfixed. Offline refund revocation waits for a successful online query, as verified on the test order. Full uninstall/reinstall, purchase-account switching, Play-signed installation checkout and Bluetooth hardware are not covered. See [Billing validation and remaining checks](billing-validation-2026-09-11.md).

## OPPO Bluetooth compatibility fix (2026-09-11)

The connected OPPO runs Android 16 / ColorOS 16.1 and has an Enco Free4 headset and Huawei Watch GT 4. After the data-preserving upgrade from debug 2.6/target33 to 3.3/target36, the app incorrectly showed no connected devices. A normal cold-start log confirmed FreeReflection 3.1.0 failed to load its writable bootstrap DEX under the newer target rules; the helper's fallback then failed because it looked up `Class.getDeclaredMethod` without its parameter types.

The initial code fix upgraded FreeReflection to 3.2.2 and repaired the meta-reflection fallback. Debug and test APK builds succeeded, 35 JVM tests passed, and Debug Lint had 0 errors / 222 warnings. The user deferred device testing, then explicitly requested replacement with HiddenApiBypass; this 3.2.2 build is now historical and was never verified on OPPO. No Play artifact was updated. See [OPPO evidence and device checklist](oppo-bluetooth-validation-2026-09-11.md), including pending restoration of the temporary USB-awake setting and preservation of OPPO widget IDs 7/8.

## HiddenApiBypass migration completed (2026-09-11)

Following the user's explicit replacement request, current code uses fixed Maven Central dependency `org.lsposed.hiddenapibypass:hiddenapibypass:6.1`. FreeReflection and the old meta-reflection fallback are removed. Initialization runs once in `attachBaseContext`, gates library access to API 28+, preserves ordinary reflection on older systems or initialization failure, and avoids repeated access to a failed library class. Exact parameter types are retained for fallback lookup; target methods execute at most once. minSdk 21, targetSdk 36 and normal Billing behavior are unchanged.

Final Debug APK, AndroidTest APK and unsigned Release AAB builds passed. **36 JVM tests passed without failures or skips; Release Lint has 0 errors / 223 warnings.** APK and AAB DEX definitions contain HiddenApiBypass and no FreeReflection classes, and APK bytecode confirms calls into the new backend. APK signature verification passed with the original OPPO debug certificate. Independent review found no blockers. See [migration details and current artifacts](hidden-api-bypass-migration-2026-09-11.md).

No device commands, installations or Play changes occurred during this migration. Actual OPPO headset/watch readings and Android 16 compatibility remain pending physical validation; the existing internal-track version 33 does not contain this replacement.


## OPPO HiddenApiBypass and Pro Bluetooth follow-up (2026-09-11)

The migration APK has now been installed on the OPPO with data preserved. HiddenApiBypass initializes successfully; both system and app confirm Enco Free4 connected with 100%. The Huawei watch is connected but provides no battery reading, a limitation acknowledged by the user. Three ordinary-package locked Bluetooth checks passed; the isolated QA package passed three unlocked form/selection/boundary checks and a real AppWidgetHost render/filter case. The normal app's Play product query still returns Billing Unavailable, so QA is not evidence of a real purchased entitlement or restore.

The QA host initially failed with “Can't load widget” because an AppCompat Activity context inflated AppCompatImageView, whose setImageBitmap is rejected by RemoteViews. Changing only the QA host to the application context fixed actual rendering; a direct same-layout/image-action comparison captured the exact exception. Ordinary production code has no custom AppWidgetHost. An additional late host rerun failed its live-watch precondition, which is recorded separately rather than counted as passing. Natural low-battery notification delivery, disconnect/reconnect, and standby timing remain outside these passing results. Original business preferences and OPPO widget IDs 7/8 were preserved. See [complete results, evidence and boundaries](oppo-pro-bluetooth-validation-2026-09-11.md).
