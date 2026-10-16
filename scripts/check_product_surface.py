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
overlay = read("app/src/main/res/layout/activity_preview.xml")
strings = read("app/src/main/res/values/strings.xml")
colors = read("app/src/main/res/values/colors.xml")
launcher = read("app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml")
round_icon = read("app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml")
foreground = read("app/src/main/res/drawable/walkbuddy_foreground.xml")
mono = read("app/src/main/res/drawable/walkbuddy_mono.xml")
primary = read("app/src/main/res/drawable/bg_primary.xml")
stop = read("app/src/main/res/drawable/bg_stop.xml")
toggle = read("app/src/main/res/drawable/bg_toggle.xml")

assert '<string name="app_name">WalkBuddy</string>' in strings
assert '<string name="hero_title">WalkBuddy</string>' in strings
assert "@string/hero_title" in home or "@string/app_name" in home
assert "@string/caution" in home and "@string/hero_lead" in home and "@string/see_body" in home
assert "@+id/journey_hint" in home and "@+id/more_panel" in home and "@+id/more_toggle" in home
assert "@+id/home_step_camera" in home and "@+id/home_step_preview" in home and "@+id/home_step_pip" in home
assert '<string name="journey_label">三步开始</string>' in strings
assert '<string name="journey_allow_camera">允许摄像头</string>' in strings
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
for id_ in ("preview_header", "preview_stop", "preview_recognition", "preview_sound", "preview_cover", "depth_guide", "depth_guide_hint"):
    assert f"@+id/{id_}" in overlay
for step in ("guide_step_service", "guide_step_ground", "guide_step_depth", "guide_step_alert"):
    assert f"@+id/{step}" in overlay
for label in ("guide_step_service", "guide_step_ground", "guide_step_depth", "guide_step_alert"):
    assert f'<string name="{label}">' in strings
assert "risk_captured_desc" in strings and "risk_caution_desc" in strings and "risk_near_desc" in strings
assert "accessibilityLiveRegion" in overlay and "importantForAccessibility" in overlay
assert 'android:checked="true"' not in overlay
recognition = chunk(overlay, "preview_recognition", "preview_sound")
sound = chunk(overlay, "preview_sound", "preview_stop")
assert "@drawable/bg_toggle" in recognition and "@drawable/bg_toggle" in sound
assert 'android:button="@null"' not in recognition and 'android:button="@null"' not in sound
cover = chunk(overlay, "preview_cover", "/>")
assert 'android:layout_width="match_parent"' in cover
assert 'android:layout_height="match_parent"' in cover
assert "@color/scrim" in cover
assert "<adaptive-icon" in launcher
assert "@drawable/walkbuddy_background" in launcher
assert "@drawable/walkbuddy_foreground" in launcher
assert "@drawable/walkbuddy_mono" in launcher
assert launcher == round_icon
assert "@drawable/walkbuddy_mascot" in foreground
assert "<vector" in mono
assert (root / "app/src/main/res/drawable-nodpi/walkbuddy_mascot.png").stat().st_size > 0
service = read("app/src/main/java/com/cyanrbt/walkbuddy/PreviewActivity.java")
activity = read("app/src/main/java/com/cyanrbt/walkbuddy/MainActivity.java")
assert "DepthGuide.resolve" in service and "renderDepthGuide" in service
assert "AccessibilitySpeech" in service and "announceForAccessibility" in service
assert "status.setText(report)" in activity
assert "paintJourney" in activity
assert "帧龄" not in activity and "毫秒" not in activity and " ms" not in activity
assert "帧龄" not in service and "毫秒" not in service and " ms" not in service
assert "lastLatency" not in service
assert 'publicReport=state.reason+"\\n"+(state.recognition?"识别开着":"识别关着");' in service
# PiP permission and component contract, mirrored by PipContractTest.
import xml.etree.ElementTree as ET
android = "{http://schemas.android.com/apk/res/android}"
tools = "{http://schemas.android.com/tools}"
manifest = ET.fromstring(read("app/src/main/AndroidManifest.xml"))
permissions = {node.get(android + "name") for node in manifest.findall("uses-permission")
               if node.get(tools + "node") != "remove"}
assert permissions == {"android.permission.CAMERA", "android.permission.VIBRATE"}
assert not manifest.findall("application/service")
receivers = manifest.findall("application/receiver")
assert len(receivers) == 1 and receivers[0].get(android + "exported") == "false"
assert receivers[0].get(android + "name") == ".StopPreviewReceiver"
preview = next(node for node in manifest.findall("application/activity")
               if node.get(android + "name") == ".PreviewActivity")
assert preview.get(android + "supportsPictureInPicture") == "true"
assert preview.get(android + "exported") == "false"
assert preview.get(android + "launchMode") == "singleTask"
assert {"screenSize", "smallestScreenSize", "screenLayout", "orientation"} <= set(preview.get(android + "configChanges").split("|"))
assert "row_overlay" not in home and "row_notice" not in home and "row_pip" in home
print("ProductSurfaceTest + PiP contract: ok")
