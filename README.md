# MetroFlow for MetroFuse

MetroFlow 1.0.1 is an alternate interface for MetroFuse with navy surfaces, amber accents and artwork shelves. Desktop adds a library sidebar, a search bar, and centered playback controls. Mobile uses icon navigation, quick access cards, and a compact player. Both layouts use MetroFuse?s playback, library and catalog.

This independent repository contains the JavaScript interface in `javascript/` and Compose interface in `native/`. Both originate from [MetroFuse](https://github.com/956tris/MetroFuse); see [SOURCE](SOURCE), each plugin's provenance notice and [LICENSE](LICENSE) (GPL-3.0).

## Install

Use [MetroFuse UI SDK preview 003](https://github.com/956tris/MetroFuse/releases/tag/v10.0.0-ui-sdk-preview-003) with API 1.1.0 and the public `dev.metrofuse.sdk.resources` catalog.

Version 1.0.1 needs the host artwork and UI recovery fixes released with it. Those host changes restore local cover paths, render the JavaScript typography and sidebar height, and put the original-interface shortcut in the top-right corner. The plugin leaves space for that shortcut without a blank strip below the player.

Download a plugin from this repository's releases, then import it through MetroFuse Settings → Plugins and select it as the custom interface:

- `metroflow.mfp`: JavaScript interface for Android and desktop.
- `metroflow.jar`: native Compose interface for desktop.
- `metroflow.apk`: native Compose plugin for Android; import it through Plugins. It is an unsigned plugin container, not an Android application to install with the system package installer.

Choose either JavaScript or native MetroFlow. Keep the app's default interface available when changing plugins. Music comes from your local library and separately installed providers.

The native plugin requires the matching host ABI: Kotlin 2.4.21, Compose 1.13.0-beta02 and API 1.1.0. Older MetroFuse builds without the public SDK resources cannot run this plugin. UI strings and resources are supplied by the host SDK and are not copied into the native package. This build also requires the host `ui.source/v1` service for the selected source and public account display name. Source and profile controls open the host settings and plugin manager. The expanded player uses the host queue, seek, previous/next, shuffle and repeat capabilities. Shuffle/repeat state and liking are not exposed by the current player API, so MetroFlow does not display a fabricated active state or a like control.

## Build

Use JDK 25 (the Gradle daemon criteria and Kotlin toolchain select it), Node.js for contract tests, and PowerShell for MFP packaging. Native Android packaging additionally requires Android SDK build-tools 37.0.0 and platform android-37.0. Set `ANDROID_HOME`, `ANDROID_SDK_ROOT` or `sdk.dir` in an untracked `local.properties` file.

Build the matching MetroFuse host `:plugin-api:desktopJar` and `:plugin-sdk:desktopJar` first, then provide their files explicitly. From this repository in PowerShell:

```powershell
./gradlew.bat :native:packagePluginJar :native:packagePluginApk `
  '-PmetrofuseApiJar=C:/path/to/MetroFuse/plugin-api/build/libs/plugin-api-desktop-1.1.0.jar' `
  '-PmetrofuseSdkJar=C:/path/to/MetroFuse/plugin-sdk/build/libs/plugin-sdk-desktop-1.1.0.jar'
powershell -NoProfile -ExecutionPolicy Bypass -File tooling/pack-mfp.ps1 -Source javascript -Output build/outputs/plugins/metroflow.mfp
node tests/test_metroflow.mjs
python tests/check_native_package.py
```

All three distributable files are written to `build/outputs/plugins/`. Native code targets JVM 21. Android packaging dexes the same plugin code for min API 24, links its plugin manifest/assets and zipaligns the unsigned container. API, SDK, Compose, coroutines and serialization dependencies are compile-only; host classes and resource catalogs are not bundled.

The Node contract checks cover JavaScript paging, stale asynchronous responses, playback actions, event disposal and UI tree limits. They use synthetic test fixtures only. The Python packaging check inspects APK entries and DEX class definitions to reject bundled host/runtime classes. Native packaging checks do not replace installing and exercising the interface in the matching desktop and Android host.
