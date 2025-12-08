#!/bin/bash
echo "==========================================="
echo "   MegumiDownloader Build Script (Linux)   "
echo "==========================================="

echo "[1/3] Building Android Release APK..."
./gradlew :app:assembleRelease

echo "[2/3] Building Linux Standalone Distributable..."
# Clean previous binaries to ensure we package the fresh build
rm -rf app/build/compose/binaries
./gradlew :app:createDistributable

echo "[3/3] Packing Linux version..."
VERSION=$(grep "versionName" app/build.gradle.kts | sed 's/.*= "//;s/"//')
DIST_DIR="app/build/compose/binaries/main-release/app"
OUTPUT_ZIP="MegumiDownload-Linux-${VERSION}.zip"
DIST_DIR_ALT="app/build/compose/binaries/main/app"

# Clean old zip
rm -f "$OUTPUT_ZIP"

if [ -d "$DIST_DIR_ALT" ]; then
    pushd "$DIST_DIR_ALT" > /dev/null
    rm -f *.zip # Remove any nested/stray zips before packing
    zip -r "../../../../../../$OUTPUT_ZIP" .
    popd > /dev/null
    
    echo "Linux Standalone ZIP created at:"
    echo "  $OUTPUT_ZIP"
elif [ -d "$DIST_DIR" ]; then
   # Fallback to main-release if main doesn't exist
    pushd "$DIST_DIR" > /dev/null
    rm -f *.zip
    zip -r "../../../../../../$OUTPUT_ZIP" .
    popd > /dev/null

    echo "Linux Standalone ZIP created at:"
    echo "  $OUTPUT_ZIP"
else
    echo "Error: Distributable directory not found."
    exit 1
fi
