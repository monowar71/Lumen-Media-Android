# AGENTS.md — client_android (Android / Android TV)

Нативный клиент LumenMedia на Kotlin + Jetpack Compose, единое приложение для телефонов/планшетов и Android TV. Репозиторий: [Lumen-Media-Android](https://github.com/monowar71/Lumen-Media-Android). Сервер: [Lumen-Media-Server](https://github.com/monowar71/Lumen-Media-Server). В umbrella-монорепо также см. корневой `AGENTS.md` и `docs/clients.md`.

## Стек

- **Kotlin** (последняя стабильная), **Jetpack Compose** + **Compose for TV** для UI.
- **Media3 / ExoPlayer** для воспроизведения (HLS, HW-декод, субтитры).
- **Coroutines / Flow** для асинхронности и состояния.
- **Hilt** для DI, **Retrofit/Ktor** — под сгенерированным из OpenAPI SDK.
- Мин. SDK: разумный современный (напр. API 24+); отдельный flavor/entry для Android TV (`leanback`, баннер).

## Архитектура

```
client_android/
├── app/                     # entry, навигация, DI-граф
├── core/
│   ├── network/             # сгенерированный SDK + APIClient (адрес, токены)
│   ├── designsystem/        # тема (web-палитра + Manrope), Dimens, компоненты, TV focus
│   └── model/               # UI-модели
├── feature/
│   ├── auth/
│   ├── library/
│   ├── details/
│   ├── player/
│   └── settings/
└── tv/                      # TV-специфичные экраны/навигация (Compose for TV)
```

- Паттерн: **MVVM/MVI**. `Composable` ↔ `ViewModel` (экспонирует `StateFlow<UiState>`) ↔ Repository (SDK + кэш Room для офлайн-эпизодов).
- Односторонний поток данных: события → редьюсер/ViewModel → новое `UiState`.
- Зависимости через интерфейсы + Hilt (тестируемость).
- Токены — `EncryptedSharedPreferences`; адрес сервера — DataStore.
- **Офлайн-кеш сериалов:** `OfflineDownloadManager` качает оригинал через `GET /api/v1/items/{id}/download` в `filesDir/offline_media/`; метаданные в Room. Скачивание — по эпизоду или сезону на экране деталей. Плеер сначала проверяет локальный файл и играет Direct Play без `playback/decision`. Настройки: лимит размера, список, очистка. На ТВ пункты настроек — focusable-строки; TextField только в диалоге по OK (без IME при D-pad навигации).

## Плеер

- **Media3 ExoPlayer** + Compose `PlayerView`/`AndroidView`.
- Перед стартом: `POST /playback/decision` c device profile (поддерживаемые кодеки берём из `MediaCodecList`/`CodecCapabilities`, разрешение, HDR).
- DirectPlay → URL; иначе HLS `index.m3u8` через `HlsMediaSource`.
- Выбор дорожек через `TrackSelector`; субтитры — WebVTT.
- **Выбор качества:** UI-селектор со списком `availableQualities`. Auto → `master.m3u8` (ABR ExoPlayer). Manual → `TrackSelectionParameters` (`setMaxVideoBitrate`/`setMaxVideoSize`/override дорожки). Смена на лету: `set-quality` + смена `MediaItem`/параметров + seek на позицию.
- **Сеть/кап:** раздельные капы для Wi-Fi/Ethernet и сотовой; тип сети через `ConnectivityManager`/`NetworkCapabilities`; подставлять актуальный `maxBitrateKbps`.
- **Android TV 100 Мбит/с:** учитывать, что Direct Play 4K-remux может насыщать порт — Auto или кап решают это (сервер транскодирует вниз).
- **Нестабильная сеть:** ABR понижает качество вместо паузы; индикатор буферизации; повтор с backoff.
- Прогресс: `PUT /progress` периодически и по событиям жизненного цикла.

## TV-специфика

- Compose for TV: `TvLazyGrid`/`TvLazyRow`, состояния фокуса, D-pad навигация; long-press на эпизоде открывает меню действий (watched / offline / delete).
- Обработка пульта (media keys), корректный focus order, крупные постеры.
- Баннер-иконка и `leanback`-фильтр в манифесте для попадания в Android TV launcher.

## Конвенции

- ktlint + detekt, предупреждения = ошибки в CI.
- Compose: stateless-компоненты + hoisting состояния; превью (`@Preview`) для экранов.
- Визуальный язык — как web: тёмный cinema (`#0b1f1a`), mint accent `#3ecf9a`, Manrope, poster 2:3, focus ring = accent.
- Никаких блокирующих вызовов в main-потоке; всё через Coroutines/Flow.

## Контроль ресурсов (CPU/ОЗУ)

- Освобождать `ExoPlayer` (`player.release()`) в `onStop`/`onDestroy` жизненного цикла; не держать плеер вне экрана.
- Coil/Glide с downsampling под размер ячейки и ограниченным кэшем; не грузить полноразмерные постеры в сетку.
- `LazyColumn`/`TvLazyGrid` (виртуализация); `collectAsStateWithLifecycle` — не собирать Flow вне видимости.
- Никаких фоновых таймеров/поллинга на неактивных экранах; корутины отменяются по scope.
- Профилировать Android Studio Profiler (Memory/CPU); особый бюджет памяти для Android TV (слабее железо).

## Тестирование

- **JUnit + Turbine** для ViewModel/Flow (замоканный Repository).
- **Compose UI tests** для критических экранов и фокус-навигации TV.
- Инструментальные тесты плеера (smoke) на эмуляторе/устройстве.
- Ручной чек-лист воспроизведения на телефоне и Android TV перед релизом.

## Definition of Done

- Собирается для мобайла и TV, линтеры/тесты зелёные.
- Экран работает с реальным сервером (dev) и на моках.
- Обновлён SDK при изменении API.
