package com.codex.evoxquickfix;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public final class OneBackUiContractTest {
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final Path MAIN = Path.of("src", "main");

    @Test
    public void manifestExposesHeliBoardToDiagnosticsButNotTestActivity() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Element manifest = factory.newDocumentBuilder()
                .parse(MAIN.resolve("AndroidManifest.xml").toFile())
                .getDocumentElement();

        assertTrue(hasAndroidName(manifest.getElementsByTagName("package"),
                "helium314.keyboard"));

        NodeList activities = manifest.getElementsByTagName("activity");
        boolean foundTestActivity = false;
        for (int index = 0; index < activities.getLength(); index++) {
            Element activity = (Element) activities.item(index);
            if (".ImeBackTestActivity".equals(activity.getAttributeNS(ANDROID_NS, "name"))) {
                foundTestActivity = true;
                assertTrue(activity.hasAttributeNS(ANDROID_NS, "exported"));
                assertFalse(Boolean.parseBoolean(
                        activity.getAttributeNS(ANDROID_NS, "exported")));
            }
        }
        assertTrue(foundTestActivity);
    }

    @Test
    public void testScreenShowsImeWithoutInterceptingBack() throws Exception {
        String source = source("ImeBackTestActivity.java");
        assertTrue(source.contains("showSoftInput(editor"));
        assertFalse(source.contains("onBackPressed("));
        assertFalse(source.contains("registerOnBackInvokedCallback"));
        assertFalse(source.contains("KEYCODE_BACK"));
    }

    @Test
    public void mainScreenKeepsOneBackIndependentAndWiresBothScopeOperations()
            throws Exception {
        String source = source("MainActivity.java");
        assertTrue(source.contains("OperationStateStore.OP_ONE_BACK,"));
        assertTrue(source.contains("fixes::enableOneBack"));
        assertTrue(source.contains("OperationStateStore.OP_ONE_BACK_DISABLE,"));
        assertTrue(source.contains("fixes::disableOneBack"));
        assertTrue(source.contains("new Intent(this, ImeBackTestActivity.class)"));
        assertTrue(source.contains("report.heliBoardDefaultIme"));
        assertTrue(source.contains("report.vectorImeScopeReady"));
        assertTrue(source.contains("report.oneBackSupported"));
        assertTrue(source.contains("report.standaloneOneBackEnabled"));
        assertTrue(source.contains("one_back_status_standalone_conflict"));
    }

    private static boolean hasAndroidName(NodeList nodes, String expected) {
        for (int index = 0; index < nodes.getLength(); index++) {
            Element element = (Element) nodes.item(index);
            if (expected.equals(element.getAttributeNS(ANDROID_NS, "name"))) {
                return true;
            }
        }
        return false;
    }

    private static String source(String fileName) throws Exception {
        return Files.readString(MAIN.resolve(Path.of(
                "java", "com", "codex", "evoxquickfix", fileName)),
                StandardCharsets.UTF_8);
    }
}
