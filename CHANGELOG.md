# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

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
