#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKSPACE_DIR="$(cd "$ROOT_DIR/.." && pwd)"
OUTPUT_DIR="$ROOT_DIR/store-assets/google-play"
SCREENSHOT_DIR="$OUTPUT_DIR/screenshots-smoking"
MAGICK_BIN="${MAGICK_BIN:-$(command -v magick || true)}"

if [[ -z "$MAGICK_BIN" ]]; then
    echo "ImageMagick 'magick' is required to build Play store assets." >&2
    exit 1
fi

APP_ICON_SOURCE="$WORKSPACE_DIR/ios-native/NeogulMapNative/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png"
FEATURE_ICON_SOURCE="$ROOT_DIR/app/src/main/res/drawable-xxxhdpi/ic_launcher_foreground.png"
SCREENSHOT_SOURCE_DIR="$ROOT_DIR/docs/release-evidence"

required_sources=(
    "$APP_ICON_SOURCE"
    "$FEATURE_ICON_SOURCE"
    "$SCREENSHOT_SOURCE_DIR/smoking-home.png"
    "$SCREENSHOT_SOURCE_DIR/smoking-detail.png"
    "$SCREENSHOT_SOURCE_DIR/smoking-menu.png"
    "$SCREENSHOT_SOURCE_DIR/smoking-settings.png"
)

for source in "${required_sources[@]}"; do
    if [[ ! -f "$source" ]]; then
        echo "Missing required source image: $source" >&2
        exit 1
    fi
done

mkdir -p "$SCREENSHOT_DIR"

# Play icon: resize the existing opaque AppIcon only; do not redraw the brand.
"$MAGICK_BIN" "$APP_ICON_SOURCE" \
    -filter Lanczos \
    -resize 512x512! \
    -alpha remove \
    -alpha off \
    -colorspace sRGB \
    -strip \
    "PNG24:$OUTPUT_DIR/play-icon-512.png"

# Feature graphic: center the existing Android launcher foreground on flat brand padding.
"$MAGICK_BIN" "$FEATURE_ICON_SOURCE" \
    -filter Lanczos \
    -resize 440x440 \
    -background '#E8F2EF' \
    -gravity center \
    -extent 1024x500 \
    -alpha remove \
    -alpha off \
    -colorspace sRGB \
    -strip \
    "PNG24:$OUTPUT_DIR/feature-graphic-1024x500.png"

screenshots=(
    "smoking-home.png|01-smoking-zones.png"
    "smoking-detail.png|02-smoking-detail.png"
    "smoking-menu.png|03-menu.png"
    "smoking-settings.png|04-settings-and-policy.png"
)

for pair in "${screenshots[@]}"; do
    source_name="${pair%%|*}"
    output_name="${pair##*|}"
    # Preserve every source pixel. Add 60 px of flat padding on each side so
    # 1080x2400 captures become Play-compliant 1200x2400 images at a 1:2 ratio.
    "$MAGICK_BIN" "$SCREENSHOT_SOURCE_DIR/$source_name" \
        -background '#E8F2EF' \
        -gravity center \
        -extent 1200x2400 \
        -alpha remove \
        -alpha off \
        -colorspace sRGB \
        -strip \
        "PNG24:$SCREENSHOT_DIR/$output_name"
done

echo "Generated Google Play assets in $OUTPUT_DIR"

if [[ -f "$OUTPUT_DIR/manifest.json" ]]; then
    python3 "$ROOT_DIR/scripts/verify-play-store-assets.py"
fi
