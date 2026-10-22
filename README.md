# Card Dispute Portal (Android)

Android client for the [card dispute portal backend](https://github.com/Goboitumelo/card-dispute-portal). Customers sign in, view their card transactions, and raise, track, and withdraw disputes. Built with Kotlin, Jetpack Compose, Room, and Retrofit.

## Requirements

- JDK 25 (Gradle provisions one if it is missing)
- Android SDK with platform `android-37.0`. Android Studio installs it for you.
- Docker Desktop, only for the container build

## Configuration

The API base URL depends on the build type:

| Build type | Base URL | Source |
| --- | --- | --- |
| `debug` | `http://10.0.2.2:8080/` | Fixed in `app/build.gradle.kts`. `10.0.2.2` is the emulator's alias for the host machine. |
| `release` | Value of `-PapiBaseUrl` | Must start with `https://`. The build fails otherwise. |

Cleartext HTTP is allowed only for `10.0.2.2` and `localhost` in debug builds. Release builds allow no cleartext traffic.

Start the backend before you run the debug app:

```bash
cd ../card-dispute-portal
docker compose up --build -d
```

## Build

```bash
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

## Run

Open the project in Android Studio and run the `app` configuration on an emulator or device (API 26 or later).

To install from the command line, with a device or emulator connected:

```bash
./gradlew installDebug
adb shell am start -n com.example.disputeportal/.MainActivity
```

The demo accounts are `thandi@example.com` and `ben@example.com`, both with the password `password`. These come from the backend `dev` profile.

## Release build

```bash
./gradlew assembleRelease -PapiBaseUrl=https://api.example.com/
```

The base URL must start with `https://`, or the task stops with an error. Signing is optional. To sign the release build, set these in `~/.gradle/gradle.properties`. Do not commit them to the repository:

```properties
releaseKeystorePath=/path/to/release.jks
releaseStorePassword=...
releaseKeyAlias=...
releaseKeyPassword=...
```

Without these properties, the release APK is unsigned.

## Test

Unit tests run on the JVM:

```bash
./gradlew testDebugUnitTest
```

Instrumented tests need a running emulator or device. They use MockWebServer in place of the backend:

```bash
./gradlew connectedDebugAndroidTest
```

Test reports are written to `app/build/reports/tests/`.

## Docker

The Dockerfile runs the unit tests, then assembles the debug APK. The build fails if a test fails. Export the APKs to `./dist`:

```bash
docker build --target artifacts --output type=local,dest=./dist .
```

To also produce an unsigned release APK, pass an HTTPS base URL:

```bash
docker build --target artifacts --build-arg API_BASE_URL=https://api.example.com/ \
  --output type=local,dest=./dist .
```

The build runs as `linux/amd64`, because the Android SDK's `aapt2` tool ships only for x86-64 Linux. On Apple Silicon this runs under emulation, so the first build is slow. The container cannot run an emulator. Install the exported APK on a device with `adb install`.

## Project layout

```
app/src/main/java/com/example/disputeportal/
  data/         Repository, Room cache, and Retrofit API
  session/      In-memory sign-in state
  ui/           Compose screens and MainViewModel
  AppContainer.kt  Manual dependency wiring
app/src/test/           Unit tests
app/src/androidTest/    Instrumented tests (Compose UI)
```
