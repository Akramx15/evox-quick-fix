package com.codex.evoxquickfix.debloat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Map;
import java.util.Set;

public final class DebloatProtectionPolicyTest {
    @Test
    public void protectsBuiltInsRoleHoldersAndContextualProviders() {
        DebloatProtectionPolicy policy = DebloatProtectionPolicy.fromSnapshot(
                Set.of("com.example.extra"),
                Map.of("android.app.role.HOME", Set.of("com.example.home"),
                        "android.app.role.SMS", Set.of("com.example.sms")),
                Set.of("com.example.contextual"));

        assertTrue(policy.isProtected("com.android.systemui"));
        assertTrue(policy.isProtected("com.android.shell"));
        assertTrue(policy.isProtected("com.android.vending"));
        assertTrue(policy.isProtected("com.example.extra"));
        assertTrue(policy.isProtected("com.example.home"));
        assertTrue(policy.isProtected("com.example.sms"));
        assertTrue(policy.isProtected("com.example.contextual"));
        assertEquals("contextual-search provider", policy.reason("com.example.contextual"));
        assertFalse(policy.isProtected("com.example.optional"));
    }

    @Test
    public void runtimeDiscoveryParsesRolesAndContextualComponents() {
        DebloatShell shell = command -> {
            if (command.contains("android.app.role.HOME")) {
                return new DebloatShell.ShellResult(0, "com.example.home\n", false);
            }
            if (command.startsWith("cmd package query-activities")) {
                return new DebloatShell.ShellResult(0,
                        "com.example.contextual/.SearchActivity\n", false);
            }
            if (command.contains("default_input_method")) {
                return new DebloatShell.ShellResult(0,
                        "com.example.ime/.Keyboard\n", false);
            }
            if (command.endsWith(" secure assistant")) {
                return new DebloatShell.ShellResult(0,
                        "com.example.assistant/.Assist\n", false);
            }
            return new DebloatShell.ShellResult(0, "", false);
        };

        DebloatProtectionPolicy policy = DebloatProtectionPolicy.runtime(shell, Set.of());

        assertTrue(policy.isProtected("com.example.home"));
        assertTrue(policy.isProtected("com.example.contextual"));
        assertTrue(policy.isProtected("com.example.ime"));
        assertTrue(policy.isProtected("com.example.assistant"));
    }
}
