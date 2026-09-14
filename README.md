# AndroidBatteryWidget

🏆 Battery Widget on Android Launcher, small but beautiful and powerful

![Battery Widget App Icon](https://raw.githubusercontent.com/xckevin/AndroidBatteryWidget/master/app/src/main/res/mipmap-mdpi/ic_app.png "App Icon")

## preview

It should look like this

![Battery Widget Preview Image](https://raw.githubusercontent.com/xckevin/AndroidBatteryWidget/master/app/src/main/res/drawable-nodpi/single_widget_preview.png "Preview")

or you can find it in Google Play
## Build

Of course you should have an Android Phone, then toggle `develop mode` in your settings。 Connecting your phone
and Computer， and confirm `Allow USB debugging`.

### requirements

It is highly recommended that using Android Studio to build project, just download the latest version.

+ JDK 17 (Java/Kotlin bytecode target remains Java 8)
+ Android SDK 36 / Build Tools 36.0.0
+ Gradle 9.1.0 (use the checked-in `./gradlew` wrapper)
+ Android Gradle Plugin 9.0.1
+ Kotlin 2.2.20 (AGP built-in Kotlin)

### script

on Mac or Linux, use follow command
```groovy
./gradlew :app:installDebug
```

onWindows, just use
```groovy
./gradlew.bat :app:installDebug
```
## dependencies
+ fastjson: https://github.com/alibaba/fastjson
+ color pick: https://github.com/QuadFlask/colorpicker
+ hidden API access: https://github.com/LSPosed/AndroidHiddenApiBypass (`6.1`)

## Widget updates

Enable **Advance → Show In Status Bar** for continuous monitoring. Allow notifications
when prompted. The ongoing battery notification includes a **Stop monitoring** action.

- A foreground service refreshes widgets when phone battery, charging, power saver,
  or Bluetooth state changes. Turning the screen on also triggers a refresh.
- While the screen is on, a 60-second check covers missed events. Screen-off stops
  this timer; no wake lock or repeating wake-up alarm is held.
- WorkManager provides a 15-minute fallback when continuous monitoring is off or
  unavailable. Android can defer this work, especially during Doze.
- HiddenApiBypass enables reflection reads of the connected devices and battery values cached by Android.
  For connected BLE devices with no framework battery value, the standard Battery
  Service is read at most once per minute, with a 15-second connection timeout.
  Actual Bluetooth freshness still depends on the peripheral reporting a new value.

Continuous monitoring resumes after reboot or an app update if it was enabled and
notifications remain allowed. Android/OEM process restrictions can still interrupt
monitoring; after a force-stop, open the app again. Retry backoff is not used as a
timer because repeated retries become progressively farther apart.

To check a running monitor on a connected test device:

```sh
adb shell dumpsys activity service com.github.xckevin927.android.battery.widget/.service.WidgetUpdateService
```

The output includes the refresh count, last refresh uptime, screen state, and battery
level. Build and static checks: `./gradlew build :app:bundleRelease`.

> just create issue
