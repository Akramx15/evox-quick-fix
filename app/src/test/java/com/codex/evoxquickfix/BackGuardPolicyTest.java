package com.codex.evoxquickfix;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public final class BackGuardPolicyTest {
    @Test
    public void blocksOnlyExactHomeTrueAndHomeRole() {
        assertTrue(BackGuardPolicy.shouldBlock(
                AppConstants.QUICK_SEARCH_HOME, List.of(Boolean.TRUE), true));
    }

    @Test
    public void allowsOtherActivity() {
        assertFalse(BackGuardPolicy.shouldBlock(
                "com.tk.quicksearch.settings.SettingsActivity", List.of(Boolean.TRUE), true));
    }

    @Test
    public void allowsFalseArgument() {
        assertFalse(BackGuardPolicy.shouldBlock(
                AppConstants.QUICK_SEARCH_HOME, List.of(Boolean.FALSE), true));
    }

    @Test
    public void allowsWhenQuickSearchIsNotHome() {
        assertFalse(BackGuardPolicy.shouldBlock(
                AppConstants.QUICK_SEARCH_HOME, List.of(Boolean.TRUE), false));
    }

    @Test
    public void allowsMissingArgument() {
        assertFalse(BackGuardPolicy.shouldBlock(
                AppConstants.QUICK_SEARCH_HOME, List.of(), true));
    }
}
