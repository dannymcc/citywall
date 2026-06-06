# CityWall

A personal Android app that periodically detects your location, resolves it to a
town or city, and sets the home and lock screen wallpaper to a generated **dark
street-map** of that place. Each city is generated once and cached forever.

Sideload build for one device (recent Pixel). Not Play Store ready, by design.

## The look

Roads only, on a near-black slate-navy (`#1A1E27`) background. No labels, no POIs,
no fills. Road hierarchy is visible — motorways thick and light, residential streets
thin and dim. Roundabouts appear naturally as small loops.

## How it works

- **Map data:** OpenStreetMap via the [Overpass API](https://overpass-api.de),
  rendered onto a `Canvas` by hand. No tiles, no labels, no API key, no Mapbox/Google.
- **Location:** AOSP `LocationManager` (coarse only). No Play Services.
- **Scheduling:** `WorkManager` periodic work, hourly. Survives reboot on its own.
- **Caching:** one PNG per city in internal storage. A cache hit skips the network.
- **JSON:** built-in `org.json`.

## Build & install

There is no Android SDK in this checkout's tooling, so build with Android Studio:

1. Open the project in Android Studio. It will set up the Gradle wrapper and sync.
   (Or, with a local Gradle ≥ 8.9: `gradle wrapper` then `./gradlew assembleDebug`.)
2. Plug in the Pixel with USB debugging on, hit Run, or
   `./gradlew installDebug`.
3. Launch CityWall, tap **Grant location permissions** (foreground first, then the
   separate background prompt), then **Update wallpaper now**.
4. Tap **Enable hourly updates** to schedule the background refresh.

`minSdk 33`, `targetSdk`/`compileSdk 35`, Java 17. Let Studio bump AGP/Kotlin if it
offers a newer stable combo.

## In-app settings

All opt-in; defaults reproduce the original behaviour (hourly, Midnight Slate, real
town). Persisted in `SharedPreferences`.

- **Update frequency** — 15 minutes up to daily. 15 min is the platform floor.
  Re-tap *Enable periodic updates* after changing it to apply the new interval.
- **Colour palette** — Midnight Slate, Carbon, Blueprint, Amber, Forest. Each city
  caches per palette, so switching theme regenerates once rather than serving stale.
- **Capital-city mode** — map the capital of the country you're in instead of your
  actual town (falls back to your town if the country isn't in the lookup).

The permission flow guides you through Settings when a dialog can't grant a
permission (background location on Android 11+, or anything permanently denied).

## Tuning knobs (in code)

- **Zoom:** `halfHeightMetres` in `MapWallpaperGenerator` (default 2200 m).
- **Palettes:** add presets to `MapWallpaperGenerator.Palette.ALL`; they appear in the
  picker automatically.
- **Which roads draw:** the `RoadClass` table and `classify()` in
  `MapWallpaperGenerator`. footway/path/cycleway/pedestrian/steps are skipped — add
  cases to bring them back.
- **Capital lookup:** `Capitals.MAP` (ISO country code → capital name).

## Overpass etiquette

The default endpoint is the **shared public instance**. That's fine for the lazy,
one-city-at-a-time path the app uses by default (one query per new city you visit).

It is **not** fine for `WallpaperRepository.warm(...)`, which fires a query per city
in the list. If you want to pre-warm `MAJOR_CITIES`, stand up a private Overpass
instance on a VPS first and pass its URL in:

```kotlin
MapWallpaperGenerator(overpassUrl = "https://your-overpass.example/api/interpreter")
```

`warm()` is opt-in and never called on launch.
