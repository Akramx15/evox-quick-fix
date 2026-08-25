package com.codex.evoxquickfix.debloat;

/** Exact PackageManager enabled-setting values persisted in the debloat ledger. */
public enum PackageEnabledState {
    DEFAULT(0, "default-state"),
    ENABLED(1, "enable"),
    DISABLED(2, "disable"),
    DISABLED_USER(3, "disable-user"),
    DISABLED_UNTIL_USED(4, "disable-until-used");

    public final int value;
    private final String restoreVerb;

    PackageEnabledState(int value, String restoreVerb) {
        this.value = value;
        this.restoreVerb = restoreVerb;
    }

    public static PackageEnabledState fromValue(int value) {
        for (PackageEnabledState state : values()) {
            if (state.value == value) {
                return state;
            }
        }
        throw new IllegalArgumentException("unsupported enabled state: " + value);
    }

    public boolean isExternallyDisabledBaseline() {
        return this == DISABLED || this == DISABLED_USER || this == DISABLED_UNTIL_USED;
    }

    public String exactRestoreCommand(String packageName) {
        return "pm " + restoreVerb + " --user 0 " + PackageId.shellQuote(packageName);
    }

    public static String disableUserCommand(String packageName) {
        return "pm disable-user --user 0 " + PackageId.shellQuote(packageName);
    }
}
