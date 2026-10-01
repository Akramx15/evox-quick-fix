package com.codex.evoxquickfix;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class VectorStateParserTest {
    @Test
    public void scopeMatchingRequiresExactPackageAndUserZero() {
        assertTrue(VectorStateParser.scopeEntry(
                "helium314.keyboard", 0, AppConstants.HELIBOARD_PACKAGE, 0));
        assertFalse(VectorStateParser.scopeEntry(
                "helium314.keyboard", 10, AppConstants.HELIBOARD_PACKAGE, 0));
        assertFalse(VectorStateParser.scopeEntry(
                "helium314.keyboard.fake", 0, AppConstants.HELIBOARD_PACKAGE, 0));
    }

    @Test
    public void moduleMatchingIsExactAndRequiresEnabledStatus() {
        assertTrue(VectorStateParser.enabledModuleEntry(
                "dev.oneback.ime", "ENABLED", AppConstants.STANDALONE_ONE_BACK_PACKAGE));
        assertFalse(VectorStateParser.enabledModuleEntry(
                "dev.oneback.ime", "disabled", AppConstants.STANDALONE_ONE_BACK_PACKAGE));
        assertFalse(VectorStateParser.enabledModuleEntry(
                "dev.oneback.ime.fake", "enabled",
                AppConstants.STANDALONE_ONE_BACK_PACKAGE));
    }
}
