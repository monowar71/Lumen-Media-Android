# LumenMedia Android

[![CI](https://github.com/monowar71/Lumen-Media-Android/actions/workflows/ci.yml/badge.svg)](https://github.com/monowar71/Lumen-Media-Android/actions/workflows/ci.yml)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

**Kotlin + Jetpack Compose** client for phones, tablets, and Android TV. Thin UI over [Lumen-Media-Server](https://github.com/monowar71/Lumen-Media-Server) with Media3 ExoPlayer (Direct Play + HLS).

## Features

- Phone/tablet Material 3 UI and Android TV leanback-friendly navigation
- Home, library (sort/filters), details, search, settings (including admin library ops)
- ExoPlayer DirectPlay / HLS with quality, audio, and subtitle selection
- Offline episode cache via server download API + Room
- UI locale `ru`/`en`; design tokens aligned with the web client (`#0b1f1a` / mint `#3ecf9a`)

## Requirements

- Android Studio / JDK 17+
- Android SDK (see `compileSdk` in Gradle)
- A running [LumenMedia Server](https://github.com/monowar71/Lumen-Media-Server)

## Build

```bash
git clone https://github.com/monowar71/Lumen-Media-Android.git
cd Lumen-Media-Android

export ANDROID_HOME=$HOME/Library/Android/sdk   # or your SDK path
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK (unsigned): `app/build/outputs/apk/release/app-release-unsigned.apk`

Default emulator API base URL: `http://10.0.2.2:8096`.

**Do not commit** keystores, passwords, or `local.properties`.

## Architecture

Compose + Hilt + Navigation; MVVM/MVI with `StateFlow`. Features under `app/` (`feature/*`, `core/*`). See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [AGENTS.md](AGENTS.md).

## Related repositories

| Repo | Role |
| --- | --- |
| [Lumen-Media-Server](https://github.com/monowar71/Lumen-Media-Server) | Backend API + transcoding |
| [Lumen-Media-iOS](https://github.com/monowar71/Lumen-Media-iOS) | iOS / iPad client |
| [Lumen-Media-Web](https://github.com/monowar71/Lumen-Media-Web) | Web client |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) and the [Code of Conduct](CODE_OF_CONDUCT.md). Security: [SECURITY.md](SECURITY.md).

## License

[GNU General Public License v3.0](LICENSE)

Copyright © 2026 Alexander Goncharow and contributors.
