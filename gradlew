#!/bin/sh

# Gradle wrapper start script
# Download Gradle if not present, then run the build.

APP_HOME=$( cd "${APP_HOME:-./}" > /dev/null && pwd -P ) || exit

# Determine Gradle version
GRADLE_VERSION="8.5"
GRADLE_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"

# Find Java
if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1; then
    JAVACMD="java"
else
    echo "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH."
    exit 1
fi

# Download Gradle if needed
GRADLE_HOME="${APP_HOME}/.gradle/${GRADLE_VERSION}"
GRADLE_CMD="${GRADLE_HOME}/bin/gradle"

if [ ! -f "$GRADLE_CMD" ]; then
    echo "Downloading Gradle ${GRADLE_VERSION}..."
    mkdir -p "$(dirname "$GRADLE_HOME")"
    cd /tmp
    curl -sSL "$GRADLE_URL" -o "gradle-${GRADLE_VERSION}-bin.zip"
    unzip -q "gradle-${GRADLE_VERSION}-bin.zip" -d "$(dirname "$GRADLE_HOME")"
    rm "gradle-${GRADLE_VERSION}-bin.zip"
    echo "Gradle ${GRADLE_VERSION} downloaded"
fi

# Run Gradle
exec "$GRADLE_CMD" -p "$APP_HOME" "$@"
