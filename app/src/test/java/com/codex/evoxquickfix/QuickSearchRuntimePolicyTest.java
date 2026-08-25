package com.codex.evoxquickfix;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class QuickSearchRuntimePolicyTest {
    @Test
    public void acceptsOnlyVerifiedEnvironmentAndPrimaryUser() {
        assertTrue(QuickSearchRuntimePolicy.isSupported("SM-S918B", 36, 10_321));
        assertTrue(QuickSearchRuntimePolicy.isSupported("sm-s918b", 36, 10_321));
        assertFalse(QuickSearchRuntimePolicy.isSupported("SM-S916B", 36, 10_321));
        assertFalse(QuickSearchRuntimePolicy.isSupported("SM-S918B", 35, 10_321));
        assertFalse(QuickSearchRuntimePolicy.isSupported("SM-S918B", 36, 110_321));
        assertFalse(QuickSearchRuntimePolicy.isSupported("SM-S918B", 36, -1));
    }
}
