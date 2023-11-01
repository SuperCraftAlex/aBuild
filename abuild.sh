#!/usr/bin/env bash
LIB="/usr/lib/abuild"

FILE=$1
shift

old="$(cat "$FILE")"
start="$LIB/start.kts"
cat "$start" >> "$FILE"

kotlin -cp "$LIB/lib" "$FILE" $@

echo "$old" > "$FILE"