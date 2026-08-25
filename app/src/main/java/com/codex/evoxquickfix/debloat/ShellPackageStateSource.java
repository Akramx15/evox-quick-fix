package com.codex.evoxquickfix.debloat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ShellPackageStateSource implements PackageStateSource {
    private static final Pattern USER_ZERO = Pattern.compile(
            "(?m)^\\s*User 0:\\s*([^\\r\\n]*)$");
    private static final Pattern ENABLED = Pattern.compile("(?:^|\\s)enabled=([0-4])(?:\\s|$)");
    private static final Pattern INSTALLED = Pattern.compile("(?:^|\\s)installed=(true|false)(?:\\s|$)");

    private final DebloatShell shell;

    ShellPackageStateSource(DebloatShell shell) {
        this.shell = java.util.Objects.requireNonNull(shell);
    }

    @Override
    public PackageEnabledState stateForUserZero(String packageName) {
        PackageId.requireValid(packageName);
        DebloatShell.ShellResult result = shell.run(
                "dumpsys package " + PackageId.shellQuote(packageName));
        result.requireSuccess("inspect package " + packageName);
        Matcher user = USER_ZERO.matcher(result.output);
        if (!user.find()) {
            return null;
        }
        String fields = user.group(1);
        Matcher installed = INSTALLED.matcher(fields);
        if (installed.find() && !Boolean.parseBoolean(installed.group(1))) {
            return null;
        }
        Matcher enabled = ENABLED.matcher(fields);
        if (!enabled.find()) {
            throw new IllegalStateException("missing enabled state for " + packageName);
        }
        return PackageEnabledState.fromValue(Integer.parseInt(enabled.group(1)));
    }
}
