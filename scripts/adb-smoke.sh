#!/usr/bin/env bash

# End-to-end UI smoke test for a debug build of Mixtapes.
#
# The run is intentionally destructive only inside Mixtapes' app data and the
# isolated device directory /sdcard/MixtapesSmoke. Real ES-DE and ROMs trees are
# never selected or modified.

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PACKAGE="tech.clyde.mixtapes"
COMPONENT="$PACKAGE/.MainActivity"
DEVICE_ROOT="${MIXTAPES_SMOKE_DEVICE_ROOT:-/sdcard/MixtapesSmoke}"
DEVICE_ESDE="$DEVICE_ROOT/esde"
DEVICE_ROMS="$DEVICE_ROOT/roms"
APK_PATH="${MIXTAPES_SMOKE_APK:-$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk}"
YOUTUBE_URL="${MIXTAPES_SMOKE_YOUTUBE_URL:-}"
ARTICLE_URL="${MIXTAPES_SMOKE_ARTICLE_URL:-}"
LLM_API_KEY="${MIXTAPES_SMOKE_API_KEY:-}"
LLM_BASE_URL="${MIXTAPES_SMOKE_LLM_BASE_URL:-}"
LLM_MODEL="${MIXTAPES_SMOKE_LLM_MODEL:-}"
ADB_BIN="${ADB:-adb}"
SERIAL="${ADB_SERIAL:-${ANDROID_SERIAL:-}}"
BUILD=true
REMOTE_UI_DUMP="/data/local/tmp/mixtapes-smoke-window.xml"
TEMP_DIR=""
UI_DUMP=""
SCREEN_WIDTH=1920
SCREEN_HEIGHT=1080

usage() {
    cat <<'EOF'
Usage: scripts/adb-smoke.sh [--serial SERIAL] [--apk PATH] [--skip-build]

Required environment:
  MIXTAPES_SMOKE_YOUTUBE_URL  Public YouTube video with a chapter list
  MIXTAPES_SMOKE_ARTICLE_URL  Public HTTPS game-list article
  MIXTAPES_SMOKE_API_KEY      API key for AI article/prose extraction

Optional environment:
  MIXTAPES_SMOKE_LLM_BASE_URL OpenAI-compatible base URL (OpenRouter by default)
  MIXTAPES_SMOKE_LLM_MODEL    Model name (the app default by default)
  MIXTAPES_SMOKE_DEVICE_ROOT  Isolated shared-storage root
  MIXTAPES_SMOKE_APK          Debug APK path
  ADB_SERIAL / ANDROID_SERIAL Device serial when more than one is attached

The script rebuilds and installs the debug APK, clears Mixtapes app data, replaces
only /sdcard/MixtapesSmoke, grants SAF access through the system picker, and drives
the UI with uiautomator. The article and YouTube sources must currently be public
and readable from the device.
EOF
}

die() {
    echo "adb-smoke: $*" >&2
    capture_failure
    exit 1
}

cleanup() {
    if [[ -n "$TEMP_DIR" && -d "$TEMP_DIR" ]]; then
        rm -rf "$TEMP_DIR"
    fi
}

trap cleanup EXIT

while [[ $# -gt 0 ]]; do
    case "$1" in
        --serial)
            [[ $# -ge 2 ]] || { usage >&2; exit 2; }
            SERIAL="$2"
            shift 2
            ;;
        --apk)
            [[ $# -ge 2 ]] || { usage >&2; exit 2; }
            APK_PATH="$2"
            shift 2
            ;;
        --skip-build)
            BUILD=false
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            usage >&2
            echo "Unknown argument: $1" >&2
            exit 2
            ;;
    esac
done

[[ -n "$YOUTUBE_URL" ]] || { usage >&2; echo "Missing MIXTAPES_SMOKE_YOUTUBE_URL" >&2; exit 2; }
[[ -n "$ARTICLE_URL" ]] || { usage >&2; echo "Missing MIXTAPES_SMOKE_ARTICLE_URL" >&2; exit 2; }
[[ -n "$LLM_API_KEY" ]] || { usage >&2; echo "Missing MIXTAPES_SMOKE_API_KEY" >&2; exit 2; }

if [[ ! "$DEVICE_ROOT" =~ ^(/sdcard|/storage/emulated/0)/MixtapesSmoke(-[A-Za-z0-9._-]+)?$ ]]; then
    echo "Refusing unsafe MIXTAPES_SMOKE_DEVICE_ROOT (name it MixtapesSmoke or MixtapesSmoke-*): $DEVICE_ROOT" >&2
    exit 2
fi

command -v "$ADB_BIN" >/dev/null 2>&1 || { echo "adb not found: $ADB_BIN" >&2; exit 2; }
command -v perl >/dev/null 2>&1 || { echo "perl is required to read uiautomator XML" >&2; exit 2; }

if [[ -z "$SERIAL" ]]; then
    SERIAL="$($ADB_BIN devices | awk 'NR > 1 && $2 == "device" { print $1 }' | sed -n '1p')"
    DEVICE_COUNT="$($ADB_BIN devices | awk 'NR > 1 && $2 == "device" { count++ } END { print count + 0 }')"
    [[ "$DEVICE_COUNT" -eq 1 ]] || {
        echo "Expected one connected device; found $DEVICE_COUNT. Set ADB_SERIAL." >&2
        exit 2
    }
fi

ADB_ARGS=(-s "$SERIAL")

adb_cmd() {
    "$ADB_BIN" "${ADB_ARGS[@]}" "$@"
}

TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/mixtapes-adb-smoke.XXXXXX")"
UI_DUMP="$TEMP_DIR/window.xml"

dump_ui() {
    adb_cmd shell uiautomator dump "$REMOTE_UI_DUMP" >/dev/null 2>&1 || return 1
    adb_cmd exec-out cat "$REMOTE_UI_DUMP" >"$UI_DUMP" 2>/dev/null
    [[ -s "$UI_DUMP" ]]
}

# Prints centers of nodes whose visible text or content description exactly
# matches the requested label. Compose merges button semantics into these nodes.
node_centers() {
    local wanted="$1"
    MIXTAPES_WANTED_TEXT="$wanted" perl -0777 -ne '
        my $wanted = $ENV{"MIXTAPES_WANTED_TEXT"};
        while (/<node\b[^>]*>/g) {
            my $node = $&;
            my ($text) = $node =~ /\btext="([^"]*)"/;
            my ($desc) = $node =~ /\bcontent-desc="([^"]*)"/;
            for ($text, $desc) {
                next unless defined;
                s/&quot;/"/g; s/&apos;/'"'"'/g; s/&lt;/</g; s/&gt;/>/g; s/&amp;/&/g;
            }
            next unless (defined($text) && $text eq $wanted) ||
                (defined($desc) && $desc eq $wanted);
            if ($node =~ /\bbounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/) {
                print int(($1 + $3) / 2), " ", int(($2 + $4) / 2), "\n";
            }
        }
    ' "$UI_DUMP"
}

find_center() {
    local label="$1"
    local occurrence="${2:-1}"
    dump_ui || return 1
    node_centers "$label" | sed -n "${occurrence}p"
}

has_text() {
    local label="$1"
    [[ -n "$(find_center "$label" 1 || true)" ]]
}

wait_text() {
    local label="$1"
    local timeout="${2:-30}"
    local elapsed=0
    while [[ "$elapsed" -lt "$timeout" ]]; do
        if has_text "$label"; then
            return 0
        fi
        sleep 1
        elapsed=$((elapsed + 1))
    done
    die "Timed out after ${timeout}s waiting for: $label"
}

tap_text() {
    local label="$1"
    local occurrence="${2:-1}"
    local scroll="${3:-false}"
    local center=""
    local attempt=0
    while [[ "$attempt" -lt 12 ]]; do
        center="$(find_center "$label" "$occurrence" || true)"
        if [[ -n "$center" ]]; then
            local x="${center%% *}"
            local y="${center##* }"
            adb_cmd shell input tap "$x" "$y"
            return 0
        fi
        if [[ "$scroll" == true ]]; then
            adb_cmd shell input swipe $((SCREEN_WIDTH / 2)) $((SCREEN_HEIGHT * 3 / 4)) \
                $((SCREEN_WIDTH / 2)) $((SCREEN_HEIGHT / 4)) 300
        fi
        sleep 1
        attempt=$((attempt + 1))
    done
    die "Could not tap visible text: $label"
}

tap_text_if_present() {
    local label="$1"
    local center=""
    center="$(find_center "$label" 1 || true)"
    [[ -n "$center" ]] || return 1
    adb_cmd shell input tap "${center%% *}" "${center##* }"
}

clear_focused_field() {
    # Android versions with keycombination can select-all in one shot. The
    # device-side delete loop is the compatibility fallback for older builds.
    if adb_cmd shell input keycombination KEYCODE_CTRL_LEFT KEYCODE_A >/dev/null 2>&1; then
        adb_cmd shell input keyevent KEYCODE_DEL
    else
        adb_cmd shell input keyevent KEYCODE_MOVE_END
        # The loop is intentionally expanded by the device shell, not this one.
        # shellcheck disable=SC2016
        adb_cmd shell 'i=0; while [ "$i" -lt 256 ]; do input keyevent KEYCODE_DEL; i=$((i + 1)); done'
    fi
}

type_text() {
    local value="$1"
    local encoded="${value// /%s}"
    adb_cmd shell input text "$encoded"
}

replace_field() {
    local label="$1"
    local value="$2"
    tap_text "$label" 1 true
    clear_focused_field
    type_text "$value"
}

append_line() {
    local value="$1"
    type_text "$value"
    adb_cmd shell input keyevent KEYCODE_ENTER
}

visible_texts() {
    [[ -s "$UI_DUMP" ]] || return 0
    perl -0777 -ne '
        while (/<node\b[^>]*>/g) {
            my $node = $&;
            for my $attr (qw(text content-desc)) {
                if ($node =~ /\b$attr="([^"]+)"/) {
                    my $value = $1;
                    $value =~ s/&quot;/"/g; $value =~ s/&apos;/'"'"'/g;
                    $value =~ s/&lt;/</g; $value =~ s/&gt;/>/g; $value =~ s/&amp;/&/g;
                    print "$value\n";
                }
            }
        }
    ' "$UI_DUMP" | awk '!seen[$0]++' | sed -n '1,80p'
}

capture_failure() {
    [[ -n "$TEMP_DIR" ]] || return 0
    local report_dir="$ROOT_DIR/build/reports/adb-smoke"
    mkdir -p "$report_dir"
    dump_ui || true
    if [[ -s "$UI_DUMP" ]]; then
        cp "$UI_DUMP" "$report_dir/window.xml"
        echo "Visible UI text:" >&2
        visible_texts >&2 || true
    fi
    adb_cmd exec-out screencap -p >"$report_dir/failure.png" 2>/dev/null || true
    echo "Failure artifacts: $report_dir" >&2
}

refresh_screen_size() {
    local dimensions=""
    dump_ui || return 0
    dimensions="$(perl -0777 -ne '
        my ($max_x, $max_y) = (0, 0);
        while (/\bbounds="\[\d+,\d+\]\[(\d+),(\d+)\]"/g) {
            $max_x = $1 if $1 > $max_x;
            $max_y = $2 if $2 > $max_y;
        }
        print "$max_x $max_y\n" if $max_x && $max_y;
    ' "$UI_DUMP")"
    if [[ -n "$dimensions" ]]; then
        SCREEN_WIDTH="${dimensions%% *}"
        SCREEN_HEIGHT="${dimensions##* }"
    fi
}

select_fixture_tree() {
    tap_text "Pick"
    wait_text "Use this folder" 15
    tap_text "Use this folder"
    sleep 1
    tap_text_if_present "Allow" || tap_text_if_present "ALLOW" || true
    wait_text "Set up Mixtapes" 15
}

share_url_and_submit() {
    local url="$1"
    adb_cmd shell am start -W -a android.intent.action.SEND -t text/plain \
        --es android.intent.extra.TEXT "$url" -n "$COMPONENT" >/dev/null
    wait_text "Make Mixtape" 15
    tap_text "Make Mixtape"
}

wait_for_review() {
    local source_name="$1"
    local timeout="${2:-210}"
    echo "  waiting for $source_name extraction and matching"
    wait_text "Collection name" "$timeout"
}

back_to_input() {
    adb_cmd shell input keyevent KEYCODE_BACK
    wait_text "Video or article URL" 15
}

ensure_paste_box() {
    if has_text "Paste a game list instead"; then
        tap_text "Paste a game list instead"
    fi
    wait_text "Chapters, game list, or prose" 15
}

if [[ "$BUILD" == true ]]; then
    echo "[1/8] Building debug APK"
    (cd "$ROOT_DIR" && ./gradlew assembleDebug)
else
    echo "[1/8] Reusing debug APK"
fi
[[ -f "$APK_PATH" ]] || die "Debug APK not found: $APK_PATH"

echo "[2/8] Installing on $SERIAL and seeding isolated fixtures"
adb_cmd install -r "$APK_PATH" >/dev/null
adb_cmd shell am force-stop "$PACKAGE"
adb_cmd shell pm clear "$PACKAGE" >/dev/null
adb_cmd shell rm -rf "$DEVICE_ROOT"
adb_cmd shell mkdir -p "$DEVICE_ESDE/collections" "$DEVICE_ROMS"
adb_cmd push "$ROOT_DIR/fixtures/roms/." "$DEVICE_ROMS/" >/dev/null

SIZE_LINE="$(adb_cmd shell wm size | tr -d '\r' | tail -n 1)"
if [[ "$SIZE_LINE" =~ ([0-9]+)x([0-9]+) ]]; then
    SCREEN_WIDTH="${BASH_REMATCH[1]}"
    SCREEN_HEIGHT="${BASH_REMATCH[2]}"
fi

ESDE_DOCUMENT_ID="primary:${DEVICE_ESDE#/sdcard/}"
ROMS_DOCUMENT_ID="primary:${DEVICE_ROMS#/sdcard/}"
ESDE_DOCUMENT_ID="${ESDE_DOCUMENT_ID#primary:/storage/emulated/0/}"
ROMS_DOCUMENT_ID="${ROMS_DOCUMENT_ID#primary:/storage/emulated/0/}"
[[ "$ESDE_DOCUMENT_ID" == primary:* ]] || ESDE_DOCUMENT_ID="primary:$ESDE_DOCUMENT_ID"
[[ "$ROMS_DOCUMENT_ID" == primary:* ]] || ROMS_DOCUMENT_ID="primary:$ROMS_DOCUMENT_ID"

echo "[3/8] Granting isolated directories through SAF and configuring AI"
adb_cmd shell am start -W -n "$COMPONENT" \
    --es tech.clyde.mixtapes.extra.SMOKE_ESDE_DOCUMENT_ID "$ESDE_DOCUMENT_ID" \
    --es tech.clyde.mixtapes.extra.SMOKE_ROMS_DOCUMENT_ID "$ROMS_DOCUMENT_ID" >/dev/null
wait_text "Set up Mixtapes" 20
refresh_screen_size
select_fixture_tree
select_fixture_tree

tap_text "Set up" 1 true
wait_text "API key" 15
tap_text "API key" 1 true
type_text "$LLM_API_KEY"
if [[ -n "$LLM_BASE_URL" ]]; then
    replace_field "Base URL" "$LLM_BASE_URL"
fi
if [[ -n "$LLM_MODEL" ]]; then
    replace_field "Model" "$LLM_MODEL"
fi
tap_text "Continue" 1 true
wait_text "New mixtape" 20

echo "[4/8] YouTube chapters"
share_url_and_submit "$YOUTUBE_URL"
wait_for_review "YouTube" 90
back_to_input

echo "[5/8] Article extraction"
share_url_and_submit "$ARTICLE_URL"
wait_for_review "article"
back_to_input

echo "[6/8] Pasted prose extraction"
ensure_paste_box
replace_field "Chapters, game list, or prose" \
    "Chrono Trigger EarthBound and Shantae are three essential retro games"
tap_text "Use pasted list" 1 true
wait_for_review "pasted prose"
back_to_input

echo "[7/8] Pasted chapters and collection write"
ensure_paste_box
tap_text "Chapters, game list, or prose" 1 true
clear_focused_field
append_line "0:00 Intro"
append_line "1:00 Chrono Trigger"
type_text "2:00 EarthBound"
tap_text "Use pasted list" 1 true
wait_for_review "pasted chapters" 60
tap_text "Write collection"
wait_text "Mixtape recorded ✔" 30

CFG_PATH="$DEVICE_ESDE/collections/custom-mixtape.cfg"
CFG_CONTENT="$(adb_cmd exec-out cat "$CFG_PATH" 2>/dev/null | tr -d '\r')" || \
    die "Collection was not written: $CFG_PATH"
[[ "$CFG_CONTENT" == *"%ROMPATH%/snes/Chrono Trigger (USA).sfc"* ]] || \
    die "Collection is missing Chrono Trigger"
[[ "$CFG_CONTENT" == *"%ROMPATH%/snes/EarthBound (USA).sfc"* ]] || \
    die "Collection is missing EarthBound"

echo "[8/8] Collection editing and whole-library ROM search"
tap_text "Library"
wait_text "Your tape rack" 20
wait_text "mixtape" 20
tap_text "mixtape"
wait_text "Add game" 20
tap_text "Add game"
wait_text "Search all ROMs" 30
tap_text "Search all ROMs"
type_text "Shantae"
wait_text "Shantae (USA).gbc" 15
tap_text "Shantae (USA).gbc"
tap_text "Save"
wait_text "Your tape rack" 30
wait_text "mixtape" 30

CFG_CONTENT="$(adb_cmd exec-out cat "$CFG_PATH" 2>/dev/null | tr -d '\r')" || \
    die "Edited collection could not be read: $CFG_PATH"
[[ "$CFG_CONTENT" == *"%ROMPATH%/gbc/Shantae (USA).gbc"* ]] || \
    die "Edited collection is missing the searched Shantae ROM"

echo
echo "ADB smoke passed on $SERIAL"
echo "Collection: $CFG_PATH"
printf '%s\n' "$CFG_CONTENT"
