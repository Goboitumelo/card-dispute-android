# syntax=docker/dockerfile:1

# Pinned to linux/amd64: the Android SDK's aapt2 ships only for x86-64 Linux.
# On Apple Silicon this runs under emulation, so expect a slow first build.
FROM --platform=linux/amd64 eclipse-temurin:25-jdk AS sdk
ARG ANDROID_CMDLINE_TOOLS_BUILD=11076708
ARG ANDROID_PLATFORM=android-37.0
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools

RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL -o /tmp/cmdline-tools.zip \
        "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDLINE_TOOLS_BUILD}_latest.zip" \
    && mkdir -p "${ANDROID_HOME}/cmdline-tools" \
    && unzip -q /tmp/cmdline-tools.zip -d "${ANDROID_HOME}/cmdline-tools" \
    && mv "${ANDROID_HOME}/cmdline-tools/cmdline-tools" "${ANDROID_HOME}/cmdline-tools/latest" \
    && rm /tmp/cmdline-tools.zip

# AGP installs the build-tools version it needs on first use, once licenses are accepted.
RUN yes | sdkmanager --licenses > /dev/null \
    && sdkmanager "platform-tools" "platforms;${ANDROID_PLATFORM}"

# Build stage: runs unit tests, then assembles the debug APK. The build fails if a test fails.
FROM sdk AS build
WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY app ./app

# Set to an https:// URL to also produce a release APK. Leave empty to skip it.
ARG API_BASE_URL=

RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew \
    && ./gradlew --no-daemon --console=plain testDebugUnitTest assembleDebug \
    && if [ -n "$API_BASE_URL" ]; then \
        ./gradlew --no-daemon --console=plain assembleRelease -PapiBaseUrl="$API_BASE_URL"; \
    fi

# Output stage: only the APKs. Export them with:
#   docker build --target artifacts --output type=local,dest=./dist .
FROM scratch AS artifacts
COPY --from=build /workspace/app/build/outputs/apk/ /apk/
