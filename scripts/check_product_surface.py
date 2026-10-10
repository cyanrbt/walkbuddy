#!/usr/bin/env python3
"""Same assertions as ProductSurfaceTest, for a machine without a JDK."""
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]

def read(rel):
    path = root / rel
    assert path.is_file(), path
    return path.read_text(encoding="utf-8")

def between(text, name, end):
    at = text.index(f'name="{name}">')
    start = text.index(">", at) + 1
    stop = text.index(end, start)
    return text[start:stop]

def chunk(text, id_, until):
    at = text.index(f"@+id/{id_}")
    stop = text.index(until, at + len(id_))
    assert stop > at
    return text[at:stop]

home = read("app/src/main/res/layout/activity_main.xml")
overlay = read("app/src/main/res/layout/overlay_window.xml")
strings = read("app/src/main/res/values/strings.xml")
colors = read("app/src/main/res/values/colors.xml")
launcher = read("app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml")
round_icon = read("app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml")
mark = read("app/src/main/res/drawable/ic_launcher_mark.xml")
primary = read("app/src/main/res/drawable/bg_primary.xml")
stop = read("app/src/main/res/drawable/bg_stop.xml")
toggle = read("app/src/main/res/drawable/bg_toggle.xml")

assert '<string name="app_name">Walk Buddy</string>' in strings
assert '<string name="hero_title">Walk Buddy</string>' in strings
assert "@string/hero_title" in home or "@string/app_name" in home
assert "@string/caution" in home and "@string/hero_lead" in home and "@string/see_body" in home
assert "毫秒" not in home
assert "@string/app_name" in overlay
assert 'name="ink"' in colors and 'name="stop_text"' in colors and 'name="scrim"' in colors
assert "@drawable/bg_primary" in home and "@drawable/bg_stop" in home and "@color/stop_text" in home
assert "@color/paper" in home
assert "@color/ink" in overlay and "@drawable/bg_toggle" in overlay and "@drawable/bg_stop" in overlay
assert "@color/stop_text" in overlay and "@color/scrim" in overlay
assert "@color/mint" in primary and "@color/ink_raised" in stop and "@color/ink_raised" in toggle
assert "毫秒" not in overlay and "按住" not in overlay and "拖动" not in overlay
assert '<string name="window_live">实时</string>' in strings
assert "毫秒" not in between(strings, "window_live", "</string>")
for phrase in ("没有提醒", "你自己看", "不保存", "网络权限"):
    assert phrase in strings, phrase
for id_ in ("overlay_header", "overlay_resize", "overlay_stop", "overlay_recognition", "overlay_sound", "overlay_cover"):
    assert f"@+id/{id_}" in overlay
assert 'android:checked="true"' not in overlay
recognition = chunk(overlay, "overlay_recognition", "overlay_sound")
sound = chunk(overlay, "overlay_sound", "overlay_stop")
assert "@drawable/bg_toggle" in recognition and "@drawable/bg_toggle" in sound
assert 'android:button="@null"' in recognition and 'android:button="@null"' in sound
cover = chunk(overlay, "overlay_cover", "/>")
assert 'android:layout_width="match_parent"' in cover
assert 'android:layout_height="match_parent"' in cover
assert "@color/scrim" in cover
assert "<adaptive-icon" in launcher
assert "@color/ink" in launcher and "@drawable/ic_launcher_mark" in launcher
assert ".png" not in launcher
assert launcher == round_icon.replace("ic_launcher_round.xml", "ic_launcher.xml") or launcher.replace("ic_launcher.xml", "") == round_icon.replace("ic_launcher_round.xml", "")
assert "<vector" in mark and 'android:viewportWidth="108"' in mark
assert 'android:fillType="evenOdd"' in mark and "M42,34" in mark and "a10,10" in mark
assert not (root / "app/src/main/res/drawable-nodpi/ic_launcher_art.png").exists()
assert not (root / "app/src/main/res/drawable/ic_observe.xml").exists()
print("ProductSurfaceTest: ok")
