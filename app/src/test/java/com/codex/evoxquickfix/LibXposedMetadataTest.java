package com.codex.evoxquickfix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.Test;

public final class LibXposedMetadataTest {
    private static final Path MODULE_PROP = Path.of(
            "src", "main", "resources", "META-INF", "xposed", "module.prop");

    @Test
    public void moduleTargetsThePinnedApi101Contract() throws Exception {
        byte[] data = Files.readAllBytes(MODULE_PROP);
        Properties properties = new Properties();
        properties.load(new ByteArrayInputStream(data));

        assertEquals("101", properties.getProperty("minApiVersion"));
        assertEquals("101", properties.getProperty("targetApiVersion"));
        assertEquals("true", properties.getProperty("staticScope"));
        assertEquals("protective", properties.getProperty("exceptionMode"));
        assertFalse(properties.containsKey("autoHotReload"));
    }
}
