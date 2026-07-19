# LumenMedia Android

Kotlin + Jetpack Compose client for phones/tablets (Android TV leanback entry included).

## Stack

- Compose + Material 3, Navigation, Hilt
- Retrofit + Kotlinx Serialization (API aligned with `server/openapi.json`)
- Media3 ExoPlayer (Direct Play + HLS)
- DataStore settings, EncryptedSharedPreferences tokens
- Room offline episode cache (`GET /items/{id}/download`)

## Build

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Debug APK (publishable for sideload/QA): `app/build/outputs/apk/debug/app-debug.apk`  
Release APK (unsigned): `app/build/outputs/apk/release/app-release-unsigned.apk`

Default API base URL for emulator: `http://10.0.2.2:8096` (host loopback).

### Release / Play Store signing

`assembleRelease` produces an **unsigned** APK. For Play Store upload, sign with an upload keystore
(`apksigner` / Play App Signing) and prefer AAB via `:app:bundleRelease` once a keystore is configured
in `app/build.gradle.kts` (do not commit keystore files or passwords).

## Screens (web-aligned UI)

Login/setup, Home (hero + shelves), Library grid, Item details, Search, Settings (TV-safe rows, caps, offline cache, admin libraries), Player.

Design tokens match the web client (`#0f1014` / `#1a1c23` / accent `#e5a00d`, Manrope): see `core/designsystem/` (`Theme`, `FpDimens`, `Components`). Phone uses bottom nav; TV uses left sidebar with D-pad focus rings.

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

Covers URL helpers, playback source mapping, and AuthViewModel login flow (mocked repository).
