#!/bin/bash
set -e
folder="$(cd -- "$(dirname -- "$0")" && pwd)"
wekinator="$HOME/Applications/Wekinator.app"
processing="/Applications/Processing.app/Contents/MacOS/Processing"
if [[ ! -x "$wekinator/Contents/MacOS/Wekinator" ]]; then
  printf 'Wekinator 앱을 사용자 응용 프로그램 폴더에서 찾지 못했습니다.\n'
  exit 1
fi
if [[ ! -x "$processing" ]]; then
  printf 'Processing 앱을 응용 프로그램 폴더에서 찾지 못했습니다.\n'
  exit 1
fi
if /usr/sbin/lsof -nP -iUDP:12014 >/dev/null 2>&1; then
  printf 'Beat Map · Four Corners 창이 이미 열려 있습니다. 해당 창에서 연주할 수 있어요.\n'
  exit 0
fi
if ! /usr/sbin/lsof -nP -iUDP:6460 >/dev/null 2>&1; then
  /usr/bin/open -n -a "$wekinator" --args "$folder/CornerModel/CornerModel.wekproj" --run
fi
exec "$processing" cli --sketch="$folder/BeatMap" --run
