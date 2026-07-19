# FreePlex Android

Kotlin + Jetpack Compose client for phones/tablets (Android TV leanback entry included).

## Stack

- Compose + Material 3, Navigation, Hilt
- Retrofit + Kotlinx Serialization (API aligned with `server/openapi.json`)
- Media3 ExoPlayer (Direct Play + HLS)
- DataStore settings, EncryptedSharedPreferences tokens

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

## Screens (web parity for current phase)

Login/setup, Home, Library grid, Item details (movie/series), Search, Settings (caps + admin libraries), Player (quality selector, progress, session ping/stop).

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

Covers URL helpers, playback source mapping, and AuthViewModel login flow (mocked repository).
