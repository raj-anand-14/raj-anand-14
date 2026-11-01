# Itachi blue CleanYourTV theme

This theme uses the user's uploaded Itachi image as its full-screen wallpaper. Three dark blue grades of that image rotate every five minutes; the red Sharingan remains red. They are treatments of the same image, not additional characters. Add more pictures to `wallpapers/` to include other heroes in the rotation.

The launcher draws only a small transparent eye overlay and a blue spotlight over the supplied image. On Home, the eyes shift toward the selected app, blink at random intervals, and spin briefly when Home returns. App tiles use the foreground of adaptive app icons tinted white, with no card fill; the selected tile grows and shows its name underneath. A blue seconds dial sits beside the clock. Opening an app zooms from its selected tile.

The pinned app order remains Netflix, Prime Video, YouTube and JioHotstar. This theme does not add or change remote button mappings. The existing Home helper remains configured for CleanYourTV.

## Wallpaper files

- `wallpapers/01-blue-rain.jpg` — blue rain grade
- `wallpapers/02-midnight-blue.jpg` — darker midnight grade
- `wallpapers/03-moonlit-blue.jpg` — brighter moonlit grade
- `wallpaper.jpg` — fallback copy if the rotating collection is unavailable

The originals used for these exports are in `source-art/`. `BlueWallpaperMaker.java` can regenerate the 1920 × 1080 versions with JDK 17.

## Build and apply on Windows

~~~powershell
$env:JAVA_HOME = '<JDK 17 folder>'
$env:ANDROID_HOME = '<Android SDK folder>'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
Push-Location work\itachi-launcher
.\gradlew.bat --no-daemon :app:assembleRelease
Pop-Location
~~~

With ADB authorized on the TV, install the signed update and push this theme:

~~~powershell
$adb = '<Android SDK folder>\platform-tools\adb.exe'
& $adb connect '<TV IPv4>:5555'
& $adb -s '<TV IPv4>:5555' install -r work\itachi-launcher\app\build\outputs\apk\release\app-release.apk
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\theme.ps1 -TvHost '<TV IPv4>' .\work\itachi-inspired
~~~

`-ExecutionPolicy Bypass` applies only to that PowerShell child process. After verifying CleanYourTV on Home, turn ADB off in Developer options or run:

~~~powershell
& $adb -s '<TV IPv4>:5555' shell settings put global adb_enabled 0
~~~

To restore the stock CleanYourTV theme, run `tools\theme.ps1 -TvHost '<TV IPv4>' -Reset`. The custom APK stays installed until replaced with the regular CleanYourTV APK.
