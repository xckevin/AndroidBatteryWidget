# AndroidBatteryWidget
ð Battery Widget on Android Launcher, small but beautiful and powerful

![Battery Widget App Icon](https://raw.githubusercontent.com/xckevin/AndroidBatteryWidget/master/app/src/main/res/mipmap-xhdpi/ic_app.png "App Icon")

## preview

It should look like this

![Battery Widget Preview Image](https://raw.githubusercontent.com/xckevin/AndroidBatteryWidget/master/app/src/main/res/drawable-nodpi/single_widget_preview.png "Preview")

or you can find it in Google Play
## Build

Of course you should have an Android Phone, then toggle `develop mode` in your settings。 Connecting your phone
and Computer， and confirm `Allow USB debugging`.

### requirements

It is highly recommended that using Android Studio to build project, just download the latest version.

+ Java11 
+ Android SDK 31
+ Gradle 7.1

### script

on Mac or Linux, use follow command
```groovy
./gradlew :app:installDebug
```

onWindows, just use
```groovy
./gradlew.bat :app:installDebug
```

## Next

...