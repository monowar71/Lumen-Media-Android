# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [0.1.11] - 2026-08-05

### Added

- Torrent library type in settings; player HUD shows TorrServer seeders / peers / download speed.
- Player applies play-time `probedFormat` from ping for torrent codec labels.
- Hide placeholder `unknown` codecs in format HUD.

## [0.1.10] - 2026-08-04

### Added

- Detail meta row parity with web: year–endYear, status, ratings, runtime, studios, tagline/originalTitle; episode rows show air date.

## [0.1.8] - 2026-08-02

### Added

- Player HDR→SDR menu lists Off plus server methods (Hardware VAAPI / hable / mobius / reinhard / bt2390) and can switch mid-playback via `hdrToneMapMethod`.

### Fixed

- TV details: restore initial focus on the poster/title band so opening a card does not jump to «Смотреть» and clip the header; D-pad Up from CTAs returns to the meta band.
- Player network Mbps HUD wires HTTP downloads into `DefaultBandwidthMeter` (`TransferListener` on the data source).

## [0.1.7] - 2026-08-02

### Fixed

- Do not send `forceHdrToSdr` on quality/audio `set-quality` calls so the server keeps the session HDR→SDR flag until the player menu toggles it.

## [0.1.6] - 2026-07-29

### Added

- Ambient theme audio on movie/series detail when the server exposes `themeUrl` (ThemerrDB cache).

### Fixed

- Sidecar subtitles on HLS: use `DefaultMediaSourceFactory` (HlsMediaSource ignored `SubtitleConfiguration`), always treat server delivery as WebVTT, and re-select text tracks when groups become available.

## [0.1.5] - 2026-07-29

### Fixed

- Debug APKs from CI and local builds share one project keystore, so `adb install -r` no longer fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` between releases (one uninstall still required when upgrading from older ephemeral-CI builds).

## [0.1.4] - 2026-07-29

### Added

- Player menus for HDR→SDR (when source is HDR) and audio channel layout (stereo / 2.1 / 5.1 / mono).
- Player HUD shows source→output format when transcoding (e.g. HEVC HDR → H.264 SDR).
- Movie details show source video/audio format per media file.

### Fixed

- `supportsHdr` in the device profile now uses display HDR capabilities instead of treating HEVC as HDR.

## [0.1.3] - 2026-07-29

### Added

- Mark as unwatched is available for in-progress media (not only fully watched) on details and in the player chrome.
- Player HUD shows estimated network throughput and video/audio format badges (resolution, HDR / Dolby Vision, Atmos / DD+, channel layout).
- `MediaStream` model now deserializes `hdr` and related probe fields from the API.

## [0.1.2] - 2026-07-29

### Fixed

- ExoPlayer attaches only the active WebVTT sidecar (not every `deliveryUrl`), avoiding parallel VTT fetches that starve HLS.
- Text subtitle sidecars swap in-place without opening a new playback session; burn-in still re-decides on the server.

## [0.1.1] - 2026-07-26

### Added

- Detail screens show genre badges (localized when known), matching the web client.
- Player audio/subtitle menus show container track titles (dubbing studio / track name) when provided by the server.
- UI locale switcher (`ru` default / `en`) with Android per-app language APIs.
- Library filters: genre, year, watched/unwatched; sort fields title/year/added/rating/runtime + order.
- Player audio and subtitle track menus (server re-decision + WebVTT sidecar).

### Changed

- Player chrome aligned with web: buffering ring around play, no skip ±10 buttons.
- Poster cards no longer show a play glyph overlay (navigate to details only).
- Hardcoded English UI strings moved to `values` / `values-ru` resources.

## [0.1.0] - 2026-07-19

### Added

- Initial LumenMedia Android import for the LumenMedia stack.
- Open-source repository scaffolding (license, contributing guide, security policy, CI, issue/PR templates).

[Unreleased]: https://github.com/monowar71/Lumen-Media-Android/compare/v0.1.8...HEAD
[0.1.8]: https://github.com/monowar71/Lumen-Media-Android/releases/tag/v0.1.8
[0.1.7]: https://github.com/monowar71/Lumen-Media-Android/releases/tag/v0.1.7
[0.1.6]: https://github.com/monowar71/Lumen-Media-Android/releases/tag/v0.1.6
[0.1.5]: https://github.com/monowar71/Lumen-Media-Android/releases/tag/v0.1.5
[0.1.4]: https://github.com/monowar71/Lumen-Media-Android/releases/tag/v0.1.4
[0.1.3]: https://github.com/monowar71/Lumen-Media-Android/releases/tag/v0.1.3
[0.1.2]: https://github.com/monowar71/Lumen-Media-Android/releases/tag/v0.1.2
[0.1.1]: https://github.com/monowar71/Lumen-Media-Android/releases/tag/v0.1.1
[0.1.0]: https://github.com/monowar71/Lumen-Media-Android/releases/tag/v0.1.0
