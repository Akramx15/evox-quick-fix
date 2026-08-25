package com.codex.evoxquickfix;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FeatureStatusTest {
    @Test
    public void hasStableStringResources() {
        assertEquals(R.string.status_ready, FeatureStatus.READY.labelRes);
        assertEquals(R.string.status_reboot_required, FeatureStatus.REBOOT_REQUIRED.labelRes);
        assertEquals(R.string.status_blocked, FeatureStatus.BLOCKED.labelRes);
    }
}
