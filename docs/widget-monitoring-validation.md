# SDK 36 widget monitoring verification

Verified on 2026-09-08 using an Android 16 / API 36 emulator, with the debug APK
built from the current working tree. The existing user's emulator was not modified.

## Build

- `./gradlew build :app:bundleRelease`: passed.
- Debug lint: 0 errors, 166 warnings. Warnings have not all been resolved.
- Existing JVM unit test: 1 passed; it is the template arithmetic test and does not
  validate monitoring. Runtime evidence below comes from emulator checks.
- APK metadata: compileSdk 36, targetSdk 36, minSdk 21, version 3.1 (31).

## Runtime checks

- A fresh install did not start a foreground service while monitoring was disabled.
- Enabling **Advance → Show In Status Bar** requested notification permission.
  After allowing, `dumpsys activity services` reported `isForeground=true`,
  notification ID 13364 and foreground type `0x40000000` (`specialUse`).
- Reinstalling over an enabled installation restored monitoring through the
  package-replaced event, without opening an activity.
- With the app in the background, changing emulator battery level triggered a
  service refresh within 778 ms as observed by `dumpsys` polling.
- After screen-off and forced Doze, the refresh count remained unchanged for 65
  seconds (one full 60-second timer period plus margin).
- Changing the battery level during this test and waking the screen triggered a
  refresh within 541 ms. The actual home-screen widget and notification both
  displayed the new value, 35%.
- The notification's **Stop monitoring** action removed the notification and
  foreground service.
- The settings category was visible below its ActionBar after the insets fix.

Timings include adb/polling overhead and represent individual emulator observations,
not device-independent latency guarantees. Simulated battery and Doze state were
restored after testing.

## Remaining device coverage

Physical Bluetooth battery reporting, OEM background restrictions, reboot recovery,
and runtime behavior on API 21–35 still need device testing. Framework reflection for
`isConnected` and `getBatteryLevel` now uses HiddenApiBypass 6.1 after the user's
2026-09-11 replacement request; the earlier runs in this report used FreeReflection. A BLE fallback cannot
force a peripheral to report a newer battery value.

The 15-minute WorkManager fallback is inexact. Continuous `Result.retry()` with
linear backoff increases the delay after each retry, so it is not a fixed-frequency
timer. See the [WorkManager work request documentation](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work)
and [Doze restrictions](https://developer.android.com/training/monitoring-device-state/doze-standby).
