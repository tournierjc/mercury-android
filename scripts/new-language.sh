#!/usr/bin/env bash
# Adds a language: copies the base strings to `values-<tag>/` and prints the registry row to paste.
#
# The copy is a starting point, not a translation — every value is still in the base language, and
# the build will say so: `StringParityTest` compares keys, not text, so a copied folder passes the
# key check and only a human makes the strings read.
#
# Usage: scripts/new-language.sh de "Deutsch"
set -euo pipefail

tag="${1:-}"
name="${2:-}"

if [ -z "$tag" ] || [ -z "$name" ]; then
    echo "usage: $0 <bcp47-tag> <native-name>" >&2
    echo "example: $0 de \"Deutsch\"" >&2
    exit 2
fi

if ! printf '%s' "$tag" | grep -Eq '^[a-z]{2,3}(-[A-Za-z0-9]+)*$'; then
    echo "not a BCP 47 tag: $tag (expected something like de, pt-BR, zh-Hans)" >&2
    exit 2
fi

root="$(cd "$(dirname "$0")/.." && pwd)"
base="$root/app/src/main/res/values"
target="$root/app/src/main/res/values-$tag"

if [ -d "$target" ]; then
    echo "$target already exists — nothing copied" >&2
    exit 1
fi

mkdir -p "$target"
for file in "$base"/strings*.xml; do
    cp "$file" "$target/$(basename "$file")"
done

echo "copied $(find "$target" -name 'strings*.xml' | wc -l | tr -d ' ') files to app/src/main/res/values-$tag"
echo
echo "now translate them, then add this row to Languages in"
echo "  app/src/main/java/io/github/tournierjc/mercury/device/AppLanguage.kt"
echo
echo "    val ${tag} = AppLanguage(tag = \"$tag\", nativeName = \"$name\"),"
echo
echo "and list it in Languages.all. The tests will tell you what is still missing:"
echo "  ./gradlew :app:testDebugUnitTest --tests '*LanguagesTest' --tests '*StringParityTest'"
