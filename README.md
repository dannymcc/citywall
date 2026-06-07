# CityWall

A personal Android app that periodically detects your location, resolves it to a
town or city, and sets the home and lock screen wallpaper to a generated **dark
street-map** of that place. Each city is generated once and cached forever.

Release-signed APKs via GitHub Releases and a self-hosted F-Droid repo.

## Install

Android 8.0+ (`minSdk 26`). Not on Google Play.

### F-Droid (recommended — automatic updates)

Add the self-hosted repository, then install CityWall from it:

1. In the **F-Droid** app: **Settings → Repositories → ➕**.
2. Paste this URL (the fingerprint pins the repo's signing key):

   ```
   https://citywall.dmcc.io/fdroid/repo?fingerprint=7478B6A7A77BE7BA332EBF98955255A47814C6986F447CC426834EAFC7DAF4D1
   ```

3. Search for **CityWall** and install. Updates then arrive through F-Droid.

> The official **f-droid.org** listing is in progress — see
> [`docs/fdroid-official.md`](docs/fdroid-official.md).

### Direct APK from GitHub

1. Download **[citywall.apk](https://github.com/dannymcc/citywall/releases/latest/download/citywall.apk)**
   (or choose a version on the [Releases page](https://github.com/dannymcc/citywall/releases)).
2. When prompted, allow "install unknown apps" for your browser or files app.
3. Open the APK to install. It can then update itself via **About → Check for updates**.

## Issues & feedback

Bugs and ideas are welcome — open an issue:
**<https://github.com/dannymcc/citywall/issues>**. For visual problems, include your
Android version and a screenshot.

## The look

Roads only, on a near-black slate-navy (`#1A1E27`) background. No labels, no POIs,
no fills. Road hierarchy is visible — motorways thick and light, residential streets
thin and dim. Roundabouts appear naturally as small loops.

## How it works

- **UI:** Jetpack Compose + Material 3, a dark cartographic theme (slate-mono accent),
  adaptive icon and splash screen.
- **Map data:** OpenStreetMap via the [Overpass API](https://overpass-api.de),
  rendered onto a `Canvas` by hand. No tiles, no labels, no API key, no Mapbox/Google.
- **Location:** AOSP `LocationManager` (coarse only). No Play Services. Async APIs on
  API 33+, version-guarded legacy fallbacks below.
- **Scheduling:** `WorkManager` periodic work. Survives reboot on its own.
- **Caching:** one PNG per city (per palette) in internal storage. A cache hit skips
  the network.
- **Capitals:** all world capitals with coordinates are bundled (data only), so
  capital mode is instant and offline.
- **JSON:** built-in `org.json`.

**Device support:** `minSdk 26` (Android 8.0+), `compile`/`targetSdk 35`, Java 17.

Maps are rendered by a companion server (`citywall.dmcc.io`), so the area you're
mapping is sent to fetch the map (approximate location only, never your identity). The
opt-in **Pathfinder** leaderboard (off by default) lets you claim cities you visit. See
the in-app privacy policy and [`docs/gamification.md`](docs/gamification.md).

## Build & install

There is no Android SDK in this checkout's tooling, so build with Android Studio:

1. Open the project in Android Studio. It will set up the Gradle wrapper and sync.
   (Or, with a local Gradle ≥ 8.9: `gradle wrapper` then `./gradlew assembleDebug`.)
2. Plug in the Pixel with USB debugging on, hit Run, or
   `./gradlew installDebug`.
3. Launch CityWall, tap **Grant location permissions** (foreground first, then the
   separate background prompt), then **Update wallpaper now**.
4. Tap **Enable hourly updates** to schedule the background refresh.

`minSdk 26`, `targetSdk`/`compileSdk 35`, Java 17. Let Studio bump AGP/Kotlin if it
offers a newer stable combo.

## In-app settings

All opt-in; defaults are hourly, the CityWall scheme, and your real town. Persisted
in `SharedPreferences`. The UI is split into bottom tabs: Wallpaper, Pathfinder,
Settings, About.

- **Update frequency** — 15 minutes up to daily. 15 min is the platform floor.
  Re-tap *Enable periodic updates* after changing it to apply the new interval.
- **Colour palette** — CityWall (default, dark roads on slate), Midnight Slate, Carbon, Blueprint, Amber, Forest. Each city
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

## Licence

GPL-3.0-only — see [LICENSE](LICENSE).
