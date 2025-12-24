# Avtodigix

Avto-scan app for car users.

## Prerequisites
- **Android Studio** (Giraffe/2022.3 or newer recommended)
- **JDK** 17 (Android Studio embedded JDK is fine)
- **Gradle** (uses the Gradle wrapper in this repo)
- **Android SDK** with:
  - Android SDK Platform 34+
  - Android SDK Build-Tools 34+
  - Android SDK Platform-Tools

## Build commands
From the repository root:

```bash
./gradlew clean
./gradlew assembleDebug
./gradlew assembleRelease
```

### Install on a connected device/emulator
```bash
./gradlew installDebug
```

## Produce APKs
- **Debug APK**
  - Command: `./gradlew assembleDebug`
  - Output: `app/build/outputs/apk/debug/app-debug.apk`

- **Release APK**
  - Command: `./gradlew assembleRelease`
  - Output: `app/build/outputs/apk/release/app-release.apk`
  - Note: Configure signing before distributing. Update `app/build.gradle` with your keystore settings or use Android Studio’s “Generate Signed Bundle / APK”.

## Supported parameters and limitations
### Supported parameters
- **OBD-II PIDs**: Standard OBD-II PID requests (e.g., speed, RPM, coolant temperature) supported by the connected vehicle/ECU.
- **Connection types**: Bluetooth OBD-II adapters (classic Bluetooth) are the primary target.
- **Android versions**: Android 10 (API 29) and above.

## UI stack
- **View Binding** is enabled for layouts (see `buildFeatures { viewBinding true }`).

## Data persistence
- **Room** is used for scan snapshots (latest scan and history).

### Limitations
- **Adapter variability**: Some low-cost ELM327 clones may not fully support all PIDs or protocols.
- **Vehicle variability**: Supported PIDs depend on ECU capabilities; not all vehicles expose all sensors.
- **Protocol support**: Advanced manufacturer-specific PIDs are not guaranteed.
- **Connectivity**: Requires a compatible Bluetooth adapter and granted Bluetooth permissions.

## Tested adapters and vehicles
> The list below reflects internal testing and is not exhaustive.

### Tested adapters
- ELM327 Bluetooth (v1.5-compatible)
- Vgate iCar Pro Bluetooth

### Tested vehicles
- Toyota Corolla (2010)
- Hyundai Elantra (2014)
- Volkswagen Golf Mk6 (2011)
