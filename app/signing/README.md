# Shared debug signing (sideload / CI)

`lumenmedia-debug.keystore` is the **intentional** shared debug key for
`com.lumenmedia.android.debug`. CI and local `assembleDebug` must use the same
file so `adb install -r` works across releases without uninstall.

- Store / key password: `android` (same as the Android SDK default debug key)
- Alias: `androiddebugkey`
- **Not** for Play Store / production. Do not reuse for release signing.

Release / upload keystores stay out of git (see repo root `.gitignore` and SECURITY.md).
