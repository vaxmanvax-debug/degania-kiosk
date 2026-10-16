# Degania Kiosk — Android v0.3 (test)
Website: **https://degania-sprayers.com/**

A lightweight Android WebView display app for Degania Sprayers. Only the official website is permitted for top-level navigation. Includes full-screen immersive interface, first-launch PIN, 5 fast taps to reveal administrator PIN, a 30-second lock after five invalid PINs, web loading progress, Wi-Fi error and Retry button, and optional Device Owner Lock Task support.

**Not a full system kiosk by default:** Immersive fullscreen does NOT disable Android Home, status bar, system gestures, notifications or OEM navigation. Device Owner provisioning on a dedicated exhibition device is necessary for true lock task mode. Installing or launching the APK alone does not grant this.

**Install:** Download APK from Releases, remove v0.1/v0.2 first due to per-build debug signing, then install on Android 8+ (API 26+). No native libraries: compatible with 32-bit and 64-bit devices. This debug package is for internal testing, not for public distribution. If Android reports installation failed, record exact error screenshot; different signatures, invalid downloads, policy blocks and storage can cause it.

**Known limits:** No PDF downloads/viewer yet, third-party embeds can run inside the page, Device Owner provisioning needs separate setup and must not be attempted on a daily personal phone. Keep a recovery path to settings before deploying on ATECH.
