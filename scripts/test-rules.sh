#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
TASK_CLASSES="$(mktemp -d)"
trap 'rm -rf "$TASK_CLASSES"' EXIT
"${JAVA_HOME:+$JAVA_HOME/bin/}javac" -d "$TASK_CLASSES" \
    app/src/main/java/dk/natmad/home/AppRules.java \
    app/src/main/java/dk/natmad/home/AppFriction.java \
    app/src/main/java/dk/natmad/home/ColorWindow.java \
    app/src/test/java/dk/natmad/home/AppRulesTest.java \
    app/src/test/java/dk/natmad/home/ColorWindowTest.java
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$TASK_CLASSES" dk.natmad.home.AppRulesTest
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$TASK_CLASSES" dk.natmad.home.ColorWindowTest
