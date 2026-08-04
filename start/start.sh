#!/bin/sh
# LingConsole - A Server WebUI control panel
# Copyright (C) 2026  XIAZHIRUI HUANG
# 
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published
# by the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
# 
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
# 
# You should have received a copy of the GNU Affero General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
CFG="$SCRIPT_DIR/config.txt"
JAR_NAME=LingConsole.jar
JAVA_PATH=default
MAX_RAM=auto
MIN_RAM=auto
WEB_ON=true
DAMON_ON=true
SINGLE_USER_MODE=true
if [ -f "$CFG" ]; then
  while IFS='=' read -r key val || [ -n "$key" ]; do
    case "$key" in
      jarName)   JAR_NAME="${val:-LingConsole.jar}" ;;
      java_path) JAVA_PATH="${val:-default}" ;;
      MaxRAM)    MAX_RAM="$val" ;;
      MinRAM)    MIN_RAM="$val" ;;
      web)       WEB_ON="$val" ;;
      damon)     DAMON_ON="$val" ;;
      singleUserMode) SINGLE_USER_MODE="$val" ;;
    esac
  done < "$CFG"
fi
JAR="$SCRIPT_DIR/$JAR_NAME"

[ "$JAVA_PATH" = "default" ] && JAVA_CMD=java || JAVA_CMD="$JAVA_PATH"

if [ ! -f "$JAR" ]; then
  echo "[ERROR] $JAR_NAME not found."
  echo "        Place the downloaded $JAR_NAME in: $SCRIPT_DIR"
  echo
  exit 1
fi

if [ "$JAVA_CMD" = "java" ]; then
  if ! command -v java >/dev/null 2>&1; then
    echo "[ERROR] java not found in PATH. Install JDK 25 or set java_path in config.txt."
    exit 1
  fi
else
  if [ ! -x "$JAVA_CMD" ]; then
    echo "[ERROR] java not found at: $JAVA_CMD"
    echo "        Please fix java_path in config.txt."
    exit 1
  fi
fi

JVM_ARGS="-XX:+ExitOnOutOfMemoryError"
case "$MIN_RAM" in
  ""|auto|AUTO|Auto) ;;
  *) JVM_ARGS="$JVM_ARGS -Xms$MIN_RAM" ;;
esac
case "$MAX_RAM" in
  ""|auto|AUTO|Auto) ;;
  *) JVM_ARGS="$JVM_ARGS -Xmx$MAX_RAM" ;;
esac

APP_ARGS=""
[ "$WEB_ON" != "true" ] && APP_ARGS="$APP_ARGS --webui false"
[ "$DAMON_ON" != "true" ] && APP_ARGS="$APP_ARGS --damon false"
[ "$SINGLE_USER_MODE" != "true" ] && APP_ARGS="$APP_ARGS --singleUserMode false"

echo "============================================================"
echo "  LingConsole one-click launcher"
echo "  JAR : $JAR"
echo "  JAVA: $JAVA_CMD"
echo "  JVM : $JVM_ARGS"
echo "  APP : $APP_ARGS"
echo "============================================================"
echo

exec "$JAVA_CMD" $JVM_ARGS -jar "$JAR" $APP_ARGS "$@"
