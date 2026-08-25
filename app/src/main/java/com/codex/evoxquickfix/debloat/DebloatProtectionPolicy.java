package com.codex.evoxquickfix.debloat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Hard protection plus live role-holder and contextual-search-provider protection. */
public final class DebloatProtectionPolicy {
    private static final String CONTEXTUAL_ACTION =
            "android.app.contextualsearch.action.LAUNCH_CONTEXTUAL_SEARCH";
    private static final String[] ROLES = {
            "android.app.role.HOME",
            "android.app.role.DIALER",
            "android.app.role.SMS",
            "android.app.role.BROWSER",
            "android.app.role.ASSISTANT"
    };
    private static final Pattern PACKAGE_TOKEN = Pattern.compile(
            "[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+");
    private static final Pattern COMPONENT = Pattern.compile(
            "([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+)/");

    private static final Set<String> BUILT_INS = Set.of(
            "android",
            "com.codex.evoxquickfix",
            "com.android.shell",
            "com.android.systemui",
            "com.android.settings",
            "com.android.launcher3",
            "com.tk.quicksearch",
            "com.google.android.googlequicksearchbox",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.android.vending"
    );

    private final LinkedHashMap<String, String> protectedReasons;

    private DebloatProtectionPolicy(LinkedHashMap<String, String> protectedReasons) {
        this.protectedReasons = protectedReasons;
    }

    public static DebloatProtectionPolicy runtime(DebloatShell shell,
                                                   Set<String> additionalHardProtection) {
        java.util.Objects.requireNonNull(shell);
        LinkedHashMap<String, String> reasons = baseReasons(additionalHardProtection);
        for (String role : ROLES) {
            DebloatShell.ShellResult result = shell.run(
                    "cmd role get-role-holders --user 0 " + role);
            result.requireSuccess("read role holders for " + role);
            Matcher holder = PACKAGE_TOKEN.matcher(result.output);
            while (holder.find()) {
                String packageName = holder.group();
                if (!packageName.startsWith("android.app.role.")) {
                    reasons.put(packageName, "role holder: " + role);
                }
            }
        }
        DebloatShell.ShellResult contextual = shell.run(
                "cmd package query-activities --brief --user 0 -a " + CONTEXTUAL_ACTION);
        contextual.requireSuccess("read contextual-search providers");
        Matcher component = COMPONENT.matcher(contextual.output);
        while (component.find()) {
            reasons.put(component.group(1), "contextual-search provider");
        }
        DebloatShell.ShellResult ime = shell.run(
                "settings --user 0 get secure default_input_method");
        ime.requireSuccess("read default input method");
        Matcher inputMethod = COMPONENT.matcher(ime.output);
        if (inputMethod.find()) {
            reasons.put(inputMethod.group(1), "current input method");
        }
        protectComponentSetting(shell, reasons, "assistant", "current assistant");
        protectComponentSetting(shell, reasons, "voice_interaction_service",
                "current voice interaction service");
        return new DebloatProtectionPolicy(reasons);
    }

    /** Deterministic constructor for a UI-provided/runtime-captured protection snapshot. */
    public static DebloatProtectionPolicy fromSnapshot(Set<String> additionalHardProtection,
                                                        Map<String, Set<String>> roleHolders,
                                                        Set<String> contextualProviders) {
        LinkedHashMap<String, String> reasons = baseReasons(additionalHardProtection);
        if (roleHolders != null) {
            for (Map.Entry<String, Set<String>> role : roleHolders.entrySet()) {
                if (role.getValue() == null) continue;
                for (String packageName : role.getValue()) {
                    reasons.put(PackageId.requireValid(packageName),
                            "role holder: " + role.getKey());
                }
            }
        }
        if (contextualProviders != null) {
            for (String packageName : contextualProviders) {
                reasons.put(PackageId.requireValid(packageName),
                        "contextual-search provider");
            }
        }
        return new DebloatProtectionPolicy(reasons);
    }

    public boolean isProtected(String packageName) {
        return protectedReasons.containsKey(PackageId.requireValid(packageName));
    }

    public String reason(String packageName) {
        return protectedReasons.get(PackageId.requireValid(packageName));
    }

    public Set<String> protectedPackages() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(protectedReasons.keySet()));
    }

    public static Set<String> builtInHardProtection() {
        return BUILT_INS;
    }

    private static void protectComponentSetting(DebloatShell shell,
                                                  LinkedHashMap<String, String> reasons,
                                                  String setting, String reason) {
        DebloatShell.ShellResult value = shell.run(
                "settings --user 0 get secure " + setting);
        value.requireSuccess("read " + setting);
        Matcher component = COMPONENT.matcher(value.output);
        if (component.find()) {
            reasons.put(component.group(1), reason);
        }
    }

    private static LinkedHashMap<String, String> baseReasons(Set<String> additional) {
        LinkedHashMap<String, String> reasons = new LinkedHashMap<>();
        for (String packageName : BUILT_INS) {
            reasons.put(packageName, "built-in hard protection");
        }
        if (additional != null) {
            for (String packageName : additional) {
                reasons.put(PackageId.requireValid(packageName), "profile hard protection");
            }
        }
        return reasons;
    }
}
