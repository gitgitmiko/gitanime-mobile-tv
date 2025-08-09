# GitAnime TV (Android TV)

Aplikasi Android TV yang memuat `https://gitanime-web.vercel.app/` di dalam WebView, mendukung kontrol remote (DPAD, OK/Enter), kontrol media (Play/Pause/FF/RW), fullscreen video, Splash Screen, ikon adaptive, dan Picture-in-Picture.

## Build via GitHub Actions (disarankan)
1. Push ke repo ini (branch `main`).
2. Buka tab **Actions** → workflow "Android CI (APK Debug)" akan berjalan otomatis.
3. Setelah selesai, unduh artifact `GitAnimeTV-debug-apk` → `app-debug.apk`.
4. Sideload ke Android TV:
   - ADB Wi-Fi: `adb connect <IP_TV:5555>` lalu `adb install -r app-debug.apk`
   - Atau via USB storage + file manager di TV.

## Build lokal (opsional)
- Prasyarat: Java 17, Android SDK (platform-tools, build-tools 34.0.0, platforms;android-34)
- Perintah: `./gradlew assembleDebug`
- APK: `app/build/outputs/apk/debug/app-debug.apk`

## Fitur
- Remote DPAD scroll, OK/Enter klik elemen fokus/tengah layar
- Kontrol `<video>` HTML5: Play/Pause/Stop/FF/RW (+/-10s)
- Fullscreen video via WebChromeClient
- Splash screen & adaptive icon
- Picture-in-Picture

Situs sumber: https://gitanime-web.vercel.app/
