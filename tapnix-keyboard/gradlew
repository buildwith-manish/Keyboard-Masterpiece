#!/bin/sh
#
# Copyright © 2015-2021 the original authors.
# Gradle wrapper shell script for POSIX-compatible shells.
#
GRADLE_OPTS="${GRADLE_OPTS:-""} -Dorg.gradle.daemon=false"
APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")

# Resolve APP_HOME robustly
_SCRIPT=$(readlink -f "$0" 2>/dev/null)
if [ -z "$_SCRIPT" ]; then _SCRIPT="$0"; fi
APP_HOME=$(dirname "$_SCRIPT")

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
DEFAULT_JVM_OPTS="-Xmx4g -Xms512m"

# Prefer OpenJDK 17 (required by AGP 8.x jlink transform) over GraalVM
PREFERRED_JDK="/nix/store/xad649j61kwkh0id5wvyiab5rliprp4d-openjdk-17.0.15+6"
if [ -x "$PREFERRED_JDK/bin/java" ]; then
    JAVA_HOME="$PREFERRED_JDK"
elif [ -z "$JAVA_HOME" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
    _JAVA_CMD=$(which java 2>/dev/null)
    if [ -n "$_JAVA_CMD" ]; then
        _JAVA_CMD=$(readlink -f "$_JAVA_CMD" 2>/dev/null || echo "$_JAVA_CMD")
        JAVA_HOME=$(dirname "$(dirname "$_JAVA_CMD")")
    fi
fi

JAVA_EXE="$JAVA_HOME/bin/java"
if [ ! -x "$JAVA_EXE" ]; then
    echo "ERROR: Could not locate Java 17+. Set JAVA_HOME." >&2
    exit 1
fi

# Set ANDROID_HOME if not set and SDK is present
if [ -z "$ANDROID_HOME" ] && [ -d "/home/runner/android-sdk" ]; then
    export ANDROID_HOME=/home/runner/android-sdk
    export ANDROID_SDK_ROOT=/home/runner/android-sdk
fi

export JAVA_HOME
exec "$JAVA_EXE" $DEFAULT_JVM_OPTS ${GRADLE_OPTS} -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
