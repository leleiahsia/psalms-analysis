# Psalms Analysis

Cloudflare Pages deployment uses `wrangler.toml` and publishes the `web` directory. Deploy with
`npx wrangler pages deploy web --project-name psalms-analysis`, or connect this GitHub repository
to Cloudflare Pages with `web` as the build output directory and no build command.

The native Android app and browser client share annotations through the signed-in user's private Google Drive `appDataFolder`.

## Web app

Copy `app\src\main\res\raw\psalms_kjv.json` to `web\psalms_kjv.json`, put a Google OAuth web client ID in `web\config.js`, then serve `web` over HTTP. For Android, configure a Google OAuth Android client for package `com.psalmsanalysis.app` and its signing certificate. Enable the Google Drive API and allow the `drive.appdata` scope.

Native Android app for analyzing each chapter of Psalms in the KJV according to the 11 Features of Hebrew Poetry.

## Build

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
```

The debug APK is written to `app\build\outputs\apk\debug\app-debug.apk`.

## Text Source

The bundled Psalms KJV JSON comes from `aruljohn/Bible-kjv`, which publishes KJV chapter and verse JSON under the MIT license. The KJV text is public domain.
