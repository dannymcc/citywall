# CityWall

**A dark street-map of wherever you are, as your phone wallpaper.**

CityWall sets a roads-only map of the town or city you're in as your home and lock
screen, and quietly refreshes it as you travel — a new place, a new map. No labels, no
clutter; just the streets (and a hint of the rivers) on a dark background.

## Install

Android 8.0+. Not on Google Play.

### F-Droid (recommended — automatic updates)

1. In the **F-Droid** app: **Settings → Repositories → ➕**.
2. Paste this (the fingerprint pins the repo's signing key):

   ```
   https://citywall.dmcc.io/fdroid/repo?fingerprint=7478B6A7A77BE7BA332EBF98955255A47814C6986F447CC426834EAFC7DAF4D1
   ```

3. Search for **CityWall** and install. Updates then arrive through F-Droid.

> An official **f-droid.org** listing is in progress.

### Direct APK

Download **[citywall.apk](https://github.com/dannymcc/citywall/releases/latest/download/citywall.apk)**
(or pick a version on [Releases](https://github.com/dannymcc/citywall/releases)), allow
"install unknown apps" when prompted, and open it. The app can update itself from
**About → Check for updates**.

## What it does

- **Roads-only street-maps** with subtle rivers, on a dark background.
- **Colour schemes** — the default dark CityWall scheme, a light "Daylight" theme, and
  more — plus adjustable **zoom** and how prominently **rivers** are drawn.
- Set the wallpaper on the **home screen, lock screen, or both**.
- **Updates as you travel** — only changes when you reach a new city, to save battery.
- Map your **real location**, your **country's capital**, or **any place** you pick
  manually. Out in the countryside? It maps the **nearest town**.
- **Pathfinder** (opt-in) — be the first to a city and claim it on a leaderboard, and
  keep a list of the places you've been.
- Optionally mark a chosen **country's embassies** on the map.

## Privacy

CityWall is built to know as little about you as possible.

- **Approximate location only.** It uses coarse, city-level location — never your exact
  position — and only to work out which place to draw. Prefer not to share it? Pick a
  location manually instead.
- **No accounts, no ads, no analytics, no trackers, no advertising ID.** There's nothing
  that identifies you.
- **Pathfinder is off by default.** Nothing about claims leaves your device unless you
  turn it on. You can delete your Pathfinder data any time (Pathfinder → *Delete my
  data*).

### What the server sees

Maps are drawn by a small companion server. When the app asks it for your wallpaper, the
entire request looks like this:

```
GET /wallpaper?lat=48.86&lon=2.35&palette=CityWall&w=1080&h=2340&zoom=2200&river=subtle
```

That's the whole story: an **approximate area** and **how you'd like it drawn**. No name,
no account, no device or advertising ID, no contacts — there is nothing to tie it to you,
and the coordinates are coarse (≈ city-level), not your doorstep.

If you join **Pathfinder**, claiming a city additionally sends a random, anonymous
"explorer ID" with the city's name and approximate coordinates — and that's deletable in
the app at any time.

Full policy: **<https://citywall.dmcc.io/privacy>**.

## Issues & feedback

Bugs and ideas are welcome — open an issue at
**<https://github.com/dannymcc/citywall/issues>**. For anything visual, please include
your Android version and a screenshot.

## Licence

GPL-3.0-only — see [LICENSE](LICENSE).
