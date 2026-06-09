# Getting CityWall into the official F-Droid catalogue

F-Droid builds from source on their server and signs with their own key, so this
requires the repo to be **public** with an **OSI licence** (GPL-3.0, see `LICENSE`).
Submission is a merge request to F-Droid's `fdroiddata` repo, reviewed by their team
(typically a couple of weeks).

## Metadata to submit
Create `metadata/io.dmcc.citywall.yml` in a fork of
<https://gitlab.com/fdroid/fdroiddata>:

```yaml
Categories:
  - Personalization
License: GPL-3.0-only
AuthorName: Danny McClelland
SourceCode: https://github.com/dannymcc/citywall
IssueTracker: https://github.com/dannymcc/citywall/issues

AutoName: CityWall
Summary: Dark street-map wallpapers of the city you're in
Description: |-
    CityWall sets a dark, roads-only OpenStreetMap street-map of the town or city
    you're in as your wallpaper, and refreshes it as you travel.

RepoType: git
Repo: https://github.com/dannymcc/citywall.git

Builds:
  - versionName: 0.3.2
    versionCode: 30200
    commit: v0.3.2
    subdir: app
    gradle:
      - fdroid

# CityWall fetches pre-rendered maps and the Pathfinder leaderboard from a self-hosted
# server (citywall.dmcc.io), so it depends on a non-free network service.
AntiFeatures:
  - NonFreeNet

AutoUpdateMode: Version v%v
UpdateCheckMode: Tags
CurrentVersion: 0.3.2
CurrentVersionCode: 30200
```

## Steps
1. Make the GitHub repo public (done as part of this) and ensure `v0.3.2` is tagged.
2. Fork `gitlab.com/fdroid/fdroiddata`, add the metadata above, and run
   `fdroid lint io.dmcc.citywall` + `fdroid build io.dmcc.citywall:30200` locally to
   confirm it builds reproducibly.
3. Open a merge request against `fdroiddata`. Address reviewer feedback.
4. Once merged, F-Droid builds + signs each tagged release automatically.

## Notes
- F-Droid signs with **its own key**, so F-Droid installs are a separate install base
  from our GitHub/self-hosted (`citywall.dmcc.io/fdroid`) APKs — they can't cross-update.
- The `fdroid` product flavour (the one the metadata builds) has no self-updater:
  F-Droid's inclusion policy forbids self-updating apps, so that flavour strips
  `REQUEST_INSTALL_PACKAGES` and hides the update UI (`BuildConfig.SELF_UPDATER`).
  Our own GitHub/self-hosted APKs are the `standard` flavour, which keeps it.
- The submitted `commit:` must be a tag that contains the flavour split (v0.3.5+);
  earlier tags only have the unflavoured build.
- `fastlane/metadata/android/en-US/` in this repo provides the listing text/changelogs.
