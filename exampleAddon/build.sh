#!/bin/sh
# ============================================================
#  LingConsole example addon - standalone build (Linux/macOS)
#
#  Prereq: JDK 25 (javac/jar on PATH)
#  Dep:    libs/lingconsole-api.jar + libs/javalin.jar
#          (only API package + Javalin compile dependency)
# ============================================================
set -e
DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
CP="$DIR/libs/lingconsole-api.jar:$DIR/libs/javalin.jar"

if [ ! -f "$DIR/libs/lingconsole-api.jar" ]; then
  echo "[ERROR] libs/lingconsole-api.jar not found."
  exit 1
fi
if [ ! -f "$DIR/libs/javalin.jar" ]; then
  echo "[ERROR] libs/javalin.jar not found."
  exit 1
fi

SRC="$DIR/src/main/java"
OUT="$DIR/out"
ADDON_JAR="$DIR/exampleAddon.jar"

rm -rf "$OUT"
mkdir -p "$OUT"

echo "Compiling (javac, cp=$CP) ..."
javac -encoding UTF-8 -cp "$CP" -d "$OUT" "$SRC/im/xz/cn/example/addon/ExampleAddon.java"

echo "Packaging (jar) ..."
jar cf "$ADDON_JAR" -C "$DIR" addon.toml -C "$OUT" .

echo ""
echo "Built: $ADDON_JAR"
echo "Put exampleAddon.jar into the LingConsole addons/ dir and restart."

