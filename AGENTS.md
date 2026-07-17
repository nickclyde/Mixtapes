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

1. **Game list extraction:** Parse YouTube **chapter titles** from the video description first (these creators almost always chapter per game). Avoid requiring a YouTube API key if feasible.
   - **Transcript extraction (optional, BYOK):** captions are fetched from the timedtext URLs already present in the watch-page player response (ANDROID-client Innertube fallback for PoToken-gated `exp=xpe` tracks; `get_transcript` is a documented-but-deferred third tier), then one OpenAI-compatible `chat/completions` call extracts the game list. The user supplies the API key/base URL/model in Settings; chapters stay the primary path and the toggle defaults off. On-device inference was researched and rejected for now (6–17 min prefill for a 4B model on the Thor's CPU).
2. **Matching:** Normalize both sides (strip No-Intro/Redump tags like `(USA)`, `(Rev 1)`, `[!]`; normalize punctuation, articles, roman numerals), then token-based fuzzy match. Ambiguous or multi-system matches go to a **review screen** — never silently guess.
3. **All on-device.** No backend. Filesystem access via Storage Access Framework (persisted URI permissions for the ES-DE and ROMs directories).

## Stack & conventions

- Kotlin + Jetpack Compose, Material 3. Two modules: `:core` (pure JVM/Kotlin — chapters, normalize, match, collection, youtube extraction; all unit tests live here) and `:app` (Android — SAF, Compose UI, OkHttp client).
- Min SDK 29, compile/target SDK 36. Versions in `gradle/libs.versions.toml` are pinned deliberately (e.g. AGP 8.x, not 9.x) — don't bump without asking.
- Dependency-light: OkHttp for fetching video metadata; avoid pulling in heavyweight libs for fuzzy matching (implement or use a small one).
- Retro-inspired but tasteful UI. App name is "Mixtapes" — cassette/mixtape theming welcome.

## Testing

- Matching + parsing logic must be **pure Kotlin, unit-testable, no Android deps**. This is where most bugs will live.
- Fixtures in `fixtures/`: sample video descriptions (with chapters) and a fake ROM directory tree covering naming edge cases (regions, revisions, subtitles, "The" prefixes, multi-disc `.m3u`, same game on multiple systems).
- Run unit tests with `./gradlew :core:test` (fast, no Android toolchain) before considering any matching change done. `fixtures/` is wired in as `:core` test resources.
- Use `scripts/adb-smoke.sh` for the repeatable on-device smoke test. It builds and installs the debug APK, seeds an isolated `/sdcard/MixtapesSmoke` fixture tree, selects it through SAF, and covers article extraction, YouTube chapters, pasted chapters, pasted prose, whole-library ROM search, collection writing, and collection editing. It validates the final `.cfg` over ADB and never selects or modifies the device's real ES-DE/ROMs directories.
- The canonical smoke sources are:
  - YouTube: `https://youtu.be/QzcheNcwh2Q?si=NtGTZj5CUvqNpnTT`
  - Article: `https://www.nintendolife.com/guides/50-best-super-nintendo-snes-games-of-all-time`
- The full smoke requires those URLs through `MIXTAPES_SMOKE_YOUTUBE_URL` and `MIXTAPES_SMOKE_ARTICLE_URL`, plus `MIXTAPES_SMOKE_API_KEY`; set `ADB_SERIAL` when multiple devices are attached. Run `scripts/adb-smoke.sh --help` for endpoint/model overrides and other options.
- Run the ADB smoke after UI, SAF, network extraction, collection-writing, or editor changes. An emulator is acceptable for iteration, but filesystem/ES-DE integration still needs a Thor run before release.

## Workflow notes

- Building requires JDK 17+ (default system `java` may be 11 and will fail) — e.g. point `JAVA_HOME` at Android Studio's JBR.
- Verify builds with `./gradlew assembleDebug` — don't assume compilation.
- Use scrcpy if you need to see the device screen.
- Ask before adding permissions beyond SAF or any network endpoint beyond the approved list: youtube.com/googlevideo metadata (watch pages, timedtext, youtubei) and the user-configured OpenAI-compatible LLM endpoint (default openrouter.ai).
