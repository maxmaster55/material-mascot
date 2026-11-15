#!/usr/bin/env bash
# Regenerates the animated README images in docs/img from the Rust core.
# Needs cargo and ImageMagick (`magick`). Run from anywhere: tools/make-gifs.sh
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
out="$root/docs/img"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

cargo build --release --manifest-path "$root/Cargo.toml" -p mascot-core --example frames
frames="$root/target/release/examples/frames"
fps=20

# gif <file> <states> <look> <eyes> <palette> <size> <ms>
gif() {
    "$frames" "$tmp/f.ppm" "$2" "$3" "$4" "$5" "$6" "$fps" "$7" 2>/dev/null
    magick -delay $((100 / fps)) -loop 0 "$tmp/f.ppm" -layers Optimize "$out/$1.gif"
}

# Every state, default look. Each clip runs a whole number of the state's own rhythm
# (the same figures as BotState.posePeriodMs) so the loop does not jump.
states="idle:3400 listening:3000 thinking:2800 working:1600 responding:600 narrating:550
reading:2600 alert:500 notify:2400 happy:1800 smile:3200 wink:3000 laugh:640 excited:1000
love:1800 proud:3600 greet:2600 confused:2600 silly:900 sad:3200 sleepy:3200 angry:100
dizzy:1600 heart:1100 star:2400 droplet:1600 cloud:4000 ghost:2000 bubble:2400 bulb:800
gear:873 moon:3400 flame:400 bolt:625 diamond:2200 bell:500 check:2400 cross:2400
magnifier:2856 hourglass:4800 cat:3400 egg:1400 hexagon:3000 play:1200 exclaim:1600
burst:2600 comet:2400 orbit:3400 swirl:1800"
for entry in $states; do
    name="${entry%%:*}"
    period="${entry##*:}"
    loops=$(( (3000 + period / 2) / period ))
    [ "$loops" -lt 1 ] && loops=1
    gif "state-$name" "$name" flat grok grape 128 $((loops * period))
done

for look in flat chrome outline clay neon pixel led; do
    gif "finish-$look" love "$look" grok grape 160 3600
done

for eyes in grok classic big round pupil block visor dot; do
    gif "eyes-$eyes" idle flat "$eyes" grape 128 3400
done

for palette in grape ink ember mint handheld; do
    gif "palette-$palette" happy flat grok "$palette" 128 3600
done

# The hero: one mascot morphing through a handful of states.
gif hero idle,happy,heart,ghost,love,star,cat,sleepy,flame,moon chrome grok grape 240 1500

du -ch "$out"/*.gif | tail -1
