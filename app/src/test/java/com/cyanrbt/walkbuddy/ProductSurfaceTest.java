package com.cyanrbt.walkbuddy;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/** Reads the shipped resource XML. It does not reimplement the layouts. */
public class ProductSurfaceTest {
    @Test public void homeAndPreviewAreOneProduct() throws Exception {
        Path root = root();
        String home = read(root.resolve("app/src/main/res/layout/activity_main.xml"));
        String overlay = read(root.resolve("app/src/main/res/layout/activity_preview.xml"));
        String strings = read(root.resolve("app/src/main/res/values/strings.xml"));
        String colors = read(root.resolve("app/src/main/res/values/colors.xml"));
        String launcher = read(root.resolve("app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml"));
        String round = read(root.resolve("app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml"));
        String mark = read(root.resolve("app/src/main/res/drawable/ic_launcher_mark.xml"));
        String primary = read(root.resolve("app/src/main/res/drawable/bg_primary.xml"));
        String stop = read(root.resolve("app/src/main/res/drawable/bg_stop.xml"));
        String toggle = read(root.resolve("app/src/main/res/drawable/bg_toggle.xml"));

        assertTrue(strings.contains("<string name=\"app_name\">Walk Buddy</string>"));
        assertTrue(strings.contains("<string name=\"hero_title\">Walk Buddy</string>"));
        assertTrue(home.contains("@string/app_name") || home.contains("@string/hero_title"));
        assertTrue(home.contains("@string/caution") && home.contains("@string/hero_lead") && home.contains("@string/see_body"));
        assertFalse(home.contains("毫秒"));
        assertTrue(overlay.contains("@string/app_name"));

        assertTrue(colors.contains("name=\"ink\""));
        assertTrue(colors.contains("name=\"stop_text\""));
        assertTrue(colors.contains("name=\"scrim\""));
        assertTrue(home.contains("@drawable/bg_primary"));
        assertTrue(home.contains("@drawable/bg_stop"));
        assertTrue(home.contains("@color/stop_text"));
        assertTrue(home.contains("@color/paper"));
        assertTrue(overlay.contains("@color/ink"));
        assertTrue(overlay.contains("@drawable/bg_toggle"));
        assertTrue(overlay.contains("@drawable/bg_stop"));
        assertTrue(overlay.contains("@color/stop_text"));
        assertTrue(overlay.contains("@color/scrim"));
        assertTrue(primary.contains("@color/mint"));
        assertTrue(stop.contains("@color/ink_raised"));
        assertTrue(toggle.contains("@color/ink_raised"));

        assertFalse(overlay.contains("毫秒"));
        assertFalse(overlay.contains("按住"));
        assertFalse(overlay.contains("拖动"));
        assertTrue(strings.contains("<string name=\"window_live\">实时</string>"));
        assertFalse(between(strings, "window_live", "</string>").contains("毫秒"));
        assertTrue(strings.contains("没有提醒"));
        assertTrue(strings.contains("你自己看"));
        assertTrue(strings.contains("不保存"));
        assertTrue(strings.contains("网络权限"));

        for (String id : new String[]{"preview_header", "preview_stop", "preview_recognition", "preview_sound", "preview_cover"}) {
            assertTrue(overlay.contains("@+id/" + id));
        }
        assertFalse(overlay.contains("android:checked=\"true\""));
        String recognition = chunk(overlay, "preview_recognition", "preview_sound");
        String sound = chunk(overlay, "preview_sound", "preview_stop");
        assertTrue(recognition.contains("@drawable/bg_toggle"));
        assertTrue(sound.contains("@drawable/bg_toggle"));
        assertTrue(recognition.contains("android:button=\"@null\""));
        assertTrue(sound.contains("android:button=\"@null\""));
        String cover = chunk(overlay, "preview_cover", "/>");
        assertTrue(cover.contains("android:layout_width=\"match_parent\""));
        assertTrue(cover.contains("android:layout_height=\"match_parent\""));
        assertTrue(cover.contains("@color/scrim"));

        assertTrue(launcher.contains("<adaptive-icon"));
        assertTrue(launcher.contains("@color/ink"));
        assertTrue(launcher.contains("@drawable/ic_launcher_mark"));
        assertFalse(launcher.contains(".png"));
        assertEquals(launcher, round);
        assertTrue(mark.contains("<vector"));
        assertTrue(mark.contains("android:viewportWidth=\"108\""));
        assertTrue(mark.contains("@color/mint_bright"));
        assertTrue(mark.contains("@color/on_ink"));
        assertTrue(mark.contains("@color/mint"));
        assertTrue(mark.contains("M60,24"));
        assertTrue(launcher.contains("@drawable/ic_launcher_mono"));
        assertFalse(Files.exists(root.resolve("app/src/main/res/drawable-nodpi/ic_launcher_art.png")));
        assertFalse(Files.exists(root.resolve("app/src/main/res/drawable/ic_observe.xml")));
        String service = read(root.resolve("app/src/main/java/com/cyanrbt/walkbuddy/PreviewActivity.java"));
        String activity = read(root.resolve("app/src/main/java/com/cyanrbt/walkbuddy/MainActivity.java"));
        assertTrue(activity.contains("status.setText(PreviewActivity.report())"));
        assertFalse(activity.contains("帧龄") || activity.contains("毫秒") || activity.contains(" ms"));
        assertFalse(service.contains("帧龄") || service.contains("毫秒") || service.contains(" ms"));
        assertFalse(service.contains("lastLatency"));
        assertTrue(service.contains("publicReport=state.reason+\"\\n\"+(state.recognition?\"识别开着\":\"识别关着\");"));
    }

    private static String between(String text, String name, String end) {
        int at = text.indexOf("name=\"" + name + "\">");
        assertTrue(at > 0);
        int start = text.indexOf('>', at) + 1;
        int stop = text.indexOf(end, start);
        return text.substring(start, stop);
    }

    private static String chunk(String text, String id, String until) {
        int at = text.indexOf("@+id/" + id);
        assertTrue(id, at > 0);
        int stop = text.indexOf(until, at + id.length());
        assertTrue(stop > at);
        return text.substring(at, stop);
    }

    private static String read(Path path) throws Exception {
        assertTrue(path.toString(), Files.isRegularFile(path));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path root() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            if (Files.exists(dir.resolve("settings.gradle")) && Files.exists(dir.resolve("app/src/main/res/layout/activity_preview.xml"))) return dir;
            dir = dir.getParent();
        }
        throw new AssertionError("project root not found from " + System.getProperty("user.dir"));
    }
}
