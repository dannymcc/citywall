# CityWall — world map, claims, and the online service (design)

Status: **design only**. None of the networked features below are built yet. This
document captures the decisions made so the next phase has a clear brief. The app
today is fully local: nothing leaves the device.

## Product idea

A shared **CityWall world map**. When a user visits a city for the first time and it
hasn't been claimed by anyone, they **claim** it. The world map shows who holds each
city. This turns "your wallpaper is the place you're in" into a light collection /
exploration game.

## Hard requirements (decided)

1. **Opt-in, privacy-first.** Participation in the world map is **off by default**
   (`Settings.joinWorldMap`). While off, no location, city, or identifier ever leaves
   the device — the app stays the local-only product it is now. Turning it on is the
   *only* thing that enables any upload. This must be a Play Store data-safety
   disclosure when it ships.
2. **The arbiter of "first" must be server-side.** "First to claim" is global shared
   state; it cannot be decided on-device. This needs a backend.
3. **Anti-cheat.** GPS can be spoofed. Claims need at least basic plausibility checks
   (rate limiting, travel-speed sanity between consecutive claims, server-side
   reverse-geocode of submitted coordinates) before they count.

## Proposed backend — `citywall.dmcc.io`

Fits the standard hyperion stack: **Flask + SQLite**, Dockerised, behind the usual
reverse proxy. One service does double duty:

### A. Claims / world map API
- `POST /claims` — body: anonymous device key + city slug + coords + timestamp.
  Server reverse-geocodes/validates, applies anti-cheat, records the claim if the
  city is unclaimed. Returns claim status (you / someone else / newly yours).
- `GET /map` — claimed cities + holder handles, for the in-app world map screen.
- Identity: start with an **anonymous per-install key** (a random UUID kept in
  `Settings`), no account. Optional handle for the leaderboard. Add real sign-in only
  if it's ever needed.

### B. Server-rendered wallpapers (the "download nearby cities" idea)
The `WallpaperGenerator` interface was built so this is a one-file swap: add a
`RemoteWallpaperGenerator` that GETs a pre-rendered PNG from
`citywall.dmcc.io/wallpaper/<slug>?w=&h=&palette=` instead of hitting Overpass and
rendering on-device.

Why server-side rendering is worth it (note: **not** mainly for app size — on-device
Overpass+Canvas is only a few KB; Compose dominates the APK):
- Offloads Overpass — the public endpoint isn't hit once per device per city.
- Consistent look across devices; palette/zoom tuning happens in one place.
- Enables **prefetch of nearby cities** on first load for an instant experience.
- Reuses the backend we already need for claims.

Keep `MapWallpaperGenerator` (on-device) as the offline/no-server fallback, selected
behind the same interface.

## Bundled data (shipped now)

- `Capitals.kt` ships **all world capitals with coordinates** (data only, a few KB),
  so capital mode is instant and offline. We deliberately do **not** bundle rendered
  capital images — ~200 full-res PNGs would add tens to hundreds of MB. If we want an
  instant first-launch wallpaper, bundle a small curated handful (5–10), not all.

## Where it plugs into the UI

The map-first layout has a `WORLD MAP` section already (the opt-in switch). A future
"World" screen (claimed-cities map + your claims) slots in alongside the home screen
without disturbing the local flow.
