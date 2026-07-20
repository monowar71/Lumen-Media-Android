# Architecture — LumenMedia Android

Thin Compose client. Business logic stays on [Lumen-Media-Server](https://github.com/monowar71/Lumen-Media-Server).

```
app/
  feature/*     # screens + ViewModels
  core/network  # API client
  core/designsystem
  tv/           # TV-oriented navigation / focus
```

- **MVVM/MVI** with `StateFlow` + Hilt
- Playback: Media3 ExoPlayer after `POST /playback/decision`
- Tokens in EncryptedSharedPreferences; offline cache via Room + download API

See [AGENTS.md](../AGENTS.md) for player, TV, and DoD rules.
