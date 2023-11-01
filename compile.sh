# compiles abuild and puts it into /usr/bin/abuild

LIB="/usr/lib/abuild"

OUT="build/"
mkdir -p "$OUT"

kotlinc -d "$OUT" src/abuild/*.kt

sudo rm -rf "$LIB"
sudo mkdir -p "$LIB/lib"
sudo mv "$OUT/"* "$LIB/lib"
sudo cp "start.kts" "$LIB/start.kts"