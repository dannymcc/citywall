# CityWall — Play Console Data Safety answers

Fill the Play Console **Data safety** form with the following. Privacy policy URL:
**https://citywall.dmcc.io/privacy**

## Overview
- Does your app collect or share any of the required user data types? **Yes**
- Is all collected data encrypted in transit? **Yes** (HTTPS)
- Do you provide a way for users to request that their data be deleted? **Yes**
  (in-app: Pathfinder → "Delete my Pathfinder data"; also via the privacy-policy contact)

## Data types

### Location → Approximate location
- Collected: **Yes** · Shared: **Yes**
- Why collected: **App functionality** (work out which city to map; render it server-side)
- Why shared: **App functionality** — the area's coordinates are sent to an
  OpenStreetMap **Overpass** service to fetch the roads/water for the map
- Processed ephemerally only: **No** (rendered maps are cached by location on our server)
- Required or optional: **Optional** (the user can pick a location manually, or not grant
  location at all)
- Precise location: **No** — coarse/approximate only

### Device or other IDs
- Collected: **Yes** (only if the user opts in to Pathfinder) · Shared: **No**
- What: a random, app-generated "explorer ID" stored on the device. **Not** an
  advertising ID or hardware/device ID; not linked to identity.
- Why: **App functionality** (the Pathfinder leaderboard)
- Optional: **Yes** (Pathfinder is off by default)

## Not collected / not present
Name, email, phone, address; financial info; health/fitness; messages; contacts;
calendar; photos/videos (the wallpaper is generated, not taken from the user); browsing
history. **No advertising or marketing. No third-party analytics or trackers. No data
sold.**

## Notes
- Personal location data is only sent when the user uses device location (not manual).
- Pathfinder claim data (claimed city names + approximate coordinates + timestamp +
  explorer ID) is only sent when the user joins Pathfinder, and is deletable in-app.
- The contact email in the privacy policy is set via the backend `CONTACT_EMAIL` env
  (currently `dmcc365@gmail.com`) — change it there if you want a dedicated address.
