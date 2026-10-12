package com.cyanrbt.walkbuddy;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

/** Shipping contract: ordinary camera permission, system PiP, no overlay or hidden service. */
public class PipContractTest {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";

    @Test public void previewUsesSystemPipWithoutOverlayOrBackgroundPermissions() throws Exception {
        Document manifest = xml("app/src/main/AndroidManifest.xml");
        NodeList permissions = manifest.getElementsByTagName("uses-permission");
        Set<String> requested = new HashSet<>();
        for (int i = 0; i < permissions.getLength(); i++) {
            Element item = (Element) permissions.item(i);
            if (!"remove".equals(item.getAttributeNS("http://schemas.android.com/tools", "node")))
                requested.add(item.getAttributeNS(ANDROID, "name"));
        }
        assertEquals(Set.of("android.permission.CAMERA", "android.permission.VIBRATE"), requested);
        assertEquals(0, manifest.getElementsByTagName("service").getLength());
        NodeList receivers = manifest.getElementsByTagName("receiver");
        assertEquals(1, receivers.getLength());
        Element stopReceiver = (Element) receivers.item(0);
        assertEquals(".StopPreviewReceiver", stopReceiver.getAttributeNS(ANDROID, "name"));
        assertEquals("false", stopReceiver.getAttributeNS(ANDROID, "exported"));
        Element preview = null;
        NodeList activities = manifest.getElementsByTagName("activity");
        for (int i = 0; i < activities.getLength(); i++) {
            Element item = (Element) activities.item(i);
            if (".PreviewActivity".equals(item.getAttributeNS(ANDROID, "name"))) preview = item;
        }
        assertNotNull(preview);
        assertEquals("true", preview.getAttributeNS(ANDROID, "supportsPictureInPicture"));
        assertEquals("false", preview.getAttributeNS(ANDROID, "exported"));
        assertEquals("singleTask", preview.getAttributeNS(ANDROID, "launchMode"));
        Set<String> handled = Set.of(preview.getAttributeNS(ANDROID, "configChanges").split("\\|"));
        assertTrue(handled.containsAll(Set.of("screenSize", "smallestScreenSize", "screenLayout", "orientation")));
    }

    @Test public void previewHasExplicitPipStopAndRecoveryAndDefaultsToSilent() throws Exception {
        Document layout = xml("app/src/main/res/layout/activity_preview.xml");
        for (String id : new String[]{"enter_pip", "preview_stop", "restart_preview", "preview_recognition", "preview_sound"}) {
            Element control = byId(layout, id);
            assertNotNull(id, control);
            assertEquals("48dp", control.getAttributeNS(ANDROID, "layout_height"));
            assertNotEquals("true", control.getAttributeNS(ANDROID, "checked"));
        }
        assertNotNull(byId(layout, "camera_preview"));
        assertNotNull(byId(layout, "preview_cover"));
        Document home = xml("app/src/main/res/layout/activity_main.xml");
        assertNull(byId(home, "row_overlay"));
        assertNull(byId(home, "row_notice"));
        assertNotNull(byId(home, "row_pip"));
    }

    private static Element byId(Document document, String id) {
        NodeList elements = document.getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (("@+id/" + id).equals(element.getAttributeNS(ANDROID, "id"))) return element;
        }
        return null;
    }

    private static Document xml(String relative) throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("settings.gradle"))) root = root.getParent();
        assertNotNull(root);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(root.resolve(relative).toFile());
    }
}
