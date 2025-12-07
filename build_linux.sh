#!/bin/bash
set -e

echo "==========================================="
echo "   MegumiDownloader Build Script (Linux)   "
echo "==========================================="

# 1. Build Android APK
echo "[1/3] Building Android Release APK..."
./gradlew :app:assembleRelease

echo "Android APK created at:"
echo "  app/build/outputs/apk/release/app-release-unsigned.apk"

# 2. Build Linux Standalone
echo "[2/3] Building Linux Standalone Distributable..."
./gradlew :app:createReleaseDistributable

# 3. Pack Linux Version
echo "[3/3] Packing Linux version..."
VERSION=$(grep "versionName" app/build.gradle.kts | sed 's/.*= "//;s/"//')
DIST_DIR="app/build/compose/binaries/main-release/app"
OUTPUT_ZIP="MegumiDownload-Linux-${VERSION}.zip"

if [ -d "$DIST_DIR" ]; then
    # Create zip from the distribution directory
    # We cd into the dir so the zip doesn't have the full path
    pushd "$DIST_DIR" > /dev/null
    zip -r "../../../../../../$OUTPUT_ZIP" .
    popd > /dev/null
    
    echo "Linux Standalone ZIP created at:"
    echo "  $OUTPUT_ZIP"
else
    echo "Error: Distributable directory not found at $DIST_DIR"
    exit 1
fi

echo "==========================================="
echo "           Build Complete!                 "
echo "==========================================="
