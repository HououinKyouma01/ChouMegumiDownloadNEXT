# Build Instructions

## 🐧 Linux (Builds Android & Linux Version)

### Requirements
- **JDK 21 (Full)** installed (JRE is not enough, `jlink` is required).
  - Run: `sudo apt install openjdk-21-jdk-headless`
- **Android SDK** installed and `ANDROID_HOME` set.
- **Zip** utility (`sudo apt install zip`).

### Usage
1. Open terminal in project root.
2. Run script:
   ```bash
   chmod +x build_linux.sh
   ./build_linux.sh
   ```
3. **Outputs**:
   - Android APK: `app/build/outputs/apk/release/app-release-unsigned.apk`
   - Linux App: `MegumiDownload-Linux-1.0.zip`

---

## 🪟 Windows (Builds Windows Version)

To compile the Windows standalone version, you must build on a Windows machine (VM or physical).

### Requirements (On Windows)
1.  **Java Development Kit (JDK) 17**:
    -   Download from [Adoptium (Temurin)](https://adoptium.net/) or Oracle.
    -   Ensure `JAVA_HOME` environment variable is set.

2.  **Android SDK**:
    -   Even though you are building the Desktop app, the project contains an Android module, so Gradle needs the Android SDK to configure the project.
    -   **Option A (Easiest)**: Install [Android Studio](https://developer.android.com/studio).
    -   **Option B (Minimal)**: Download "Command line tools only" from Android website, unzip, and set `ANDROID_HOME` environment variable to that folder.
    -   **Important**: You might need to create a `local.properties` file in the project root with the path:
        ```properties
        sdk.dir=C:\\Users\\<YourUser>\\AppData\\Local\\Android\\Sdk
        ```
        (Note the double backslashes `\\`).

### Usage
1.  Copy the entire project folder to your Windows VM.
2.  Open Command Prompt (cmd) or PowerShell in the project folder.
3.  Run the script:
    ```cmd
    build_windows.bat
    ```
4.  **Output**:
    -   Windows App: `MegumiDownload-Windows.zip`
    -   Inside the zip is `MegumiDownload.exe` and necessary files. It runs standalone.

### Troubleshooting Windows Build
-   **"SDK location not found"**: Make sure you created `local.properties` pointing to your Android SDK or set `ANDROID_HOME`.
-   **Execution failed**: Run `gradlew.bat --stacktrace` to see details.
