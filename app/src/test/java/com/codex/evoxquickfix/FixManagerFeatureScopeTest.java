package com.codex.evoxquickfix;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FixManagerFeatureScopeTest {
    @Test
    public void individualScopesDoNotIncludeUnrelatedFeatures() {
        assertTrue(FixManager.TRANSPARENCY_SCOPE.transparency());
        assertFalse(FixManager.TRANSPARENCY_SCOPE.back());
        assertFalse(FixManager.TRANSPARENCY_SCOPE.circle());

        assertFalse(FixManager.BACK_SCOPE.transparency());
        assertTrue(FixManager.BACK_SCOPE.back());
        assertFalse(FixManager.BACK_SCOPE.circle());

        assertFalse(FixManager.CIRCLE_SCOPE.transparency());
        assertFalse(FixManager.CIRCLE_SCOPE.back());
        assertTrue(FixManager.CIRCLE_SCOPE.circle());
    }

    @Test
    public void allScopeIncludesEveryFeature() {
        assertTrue(FixManager.ALL_SCOPE.transparency());
        assertTrue(FixManager.ALL_SCOPE.back());
        assertTrue(FixManager.ALL_SCOPE.circle());
    }
}
