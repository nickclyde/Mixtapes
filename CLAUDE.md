# Mixtapes

Android app that turns a YouTube video of curated retro games (e.g. TechDweeb, Retro Game Corps "best games" lists) into an ES-DE custom collection on-device. Paste a link → parse the game list → fuzzy-match against the local ROM library → review matches → write the collection file.

## Target environment

- **Device:** AYN Thor (Android) and similar retro game emulation Android devices. Target orientation is landscape. Most have 16:9 screens, but also support 4:3. Primary test target via wireless adb: `adb connect <THOR_IP>:<PORT>` (ask me if not paired).
- **Frontend it integrates with:** ES-DE (EmulationStation Desktop Edition) for Android.
- **Emulator:** fine for UI work, but filesystem/ES-DE integration must be verified on the Thor.

## ES-DE integration (the whole point)

- A custom collection is a **plain text file**: `<ES-DE dir>/collections/custom-<name>.cfg`
- One absolute ROM path per line. ES-DE also supports a `%ROMPATH%` prefix for portability — prefer it when the ROM lies under the configured ROM directory.
- Default Android locations (both user-configurable, so the app must let the user pick via SAF):
  - ES-DE dir: `/storage/emulated/0/ES-DE`
  - ROMs dir: `/storage/emulated/0/ROMs` (one subdirectory per system, e.g. `snes/`, `psx/`, `gba/`)
- ES-DE reads collections at startup; no notification mechanism needed. Never modify any other ES-DE files.

## Architecture decisions

1. **Game list extraction:** Parse YouTube **chapter titles** from the video description first (these creators almost always chapter per game). Transcript parsing is a fallback/v2 concern. Avoid requiring a YouTube API key if feasible.
2. **Matching:** Normalize both sides (strip No-Intro/Redump tags like `(USA)`, `(Rev 1)`, `[!]`; normalize punctuation, articles, roman numerals), then token-based fuzzy match. Ambiguous or multi-system matches go to a **review screen** — never silently guess.
3. **All on-device.** No backend. Filesystem access via Storage Access Framework (persisted URI permissions for the ES-DE and ROMs directories).

## Stack & conventions

- Kotlin + Jetpack Compose, Material 3. Single module to start.
- Min SDK 29, target latest stable.
- Dependency-light: OkHttp for fetching video metadata; avoid pulling in heavyweight libs for fuzzy matching (implement or use a small one).
- Retro-inspired but tasteful UI. App name is "Mixtapes" — cassette/mixtape theming welcome.

## Testing

- Matching + parsing logic must be **pure Kotlin, unit-testable, no Android deps**. This is where most bugs will live.
- Fixtures in `fixtures/`: sample video descriptions (with chapters) and a fake ROM directory tree covering naming edge cases (regions, revisions, subtitles, "The" prefixes, multi-disc `.m3u`, same game on multiple systems).
- Run unit tests with `./gradlew test` before considering any matching change done.
- On-device smoke test: install via adb, point at fixture dirs, confirm a valid `.cfg` is produced.

## Workflow notes

- Verify builds with `./gradlew assembleDebug` — don't assume compilation.
- Use scrcpy if you need to see the device screen.
- Ask before adding permissions beyond SAF or any network endpoint beyond youtube.com/googlevideo metadata.
