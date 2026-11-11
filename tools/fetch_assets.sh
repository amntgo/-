#!/usr/bin/env bash
# ينزّل شعار الكلية والخطوط (كايرو وتجوال) ويولّد الأيقونات. أي فشل لا يوقف البناء.
set +e
B=https://dic-portal.dreamcampus-erp-sd.com/brand
A=app/src/main/assets
RES=app/src/main/res
mkdir -p $A/fonts store
G=https://raw.githubusercontent.com/google/fonts/main/ofl
curl -fsSL "$G/tajawal/Tajawal-Regular.ttf" -o $A/fonts/Tajawal-Regular.ttf
curl -fsSL "$G/tajawal/Tajawal-Medium.ttf"  -o $A/fonts/Tajawal-Medium.ttf
curl -fsSL "$G/tajawal/Tajawal-Bold.ttf"    -o $A/fonts/Tajawal-Bold.ttf
curl -fsSL "$G/cairo/Cairo%5Bslnt%2Cwght%5D.ttf" -o $A/fonts/Cairo.ttf
for f in $A/fonts/*; do [ -s "$f" ] || rm -f "$f"; done

rm -f /tmp/logo.png
curl -fsSL "$B/logo-512.png" -o /tmp/logo.png || curl -fsSL "$B/logo-256.png" -o /tmp/logo.png || curl -fsSL "$B/logo-128.png" -o /tmp/logo.png
if [ -s /tmp/logo.png ]; then
  cp /tmp/logo.png $A/logo.png
  IM=$(command -v magick || command -v convert)
  if [ -n "$IM" ]; then
    mkdir -p $RES/drawable-nodpi
    $IM -size 432x432 xc:none -fill white -draw "circle 216,216 216,76" \( /tmp/logo.png -resize 190x190 \) -gravity center -composite $RES/drawable-nodpi/ic_launcher_foreground.png \
      && rm -f $RES/drawable/ic_launcher_foreground.xml
    $IM -size 512x512 xc:'#0b1730' -fill white -draw "circle 256,256 256,70" \( /tmp/logo.png -resize 290x290 \) -gravity center -composite store/icon-512.png
  fi
fi
echo "assets done:"; ls -la $A $A/fonts store 2>/dev/null
exit 0
