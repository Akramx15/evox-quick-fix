package com.codex.evoxquickfix.debloat;

import java.io.File;

final class RootDebloatReplica implements DebloatLedgerStore.RootReplica {
    static final String ROOT_DIRECTORY = "/data/adb/evox-quick-fix";
    static final String ROOT_LEDGER = ROOT_DIRECTORY + "/debloat-ledger-v1.json";
    private static final String ROOT_LEDGER_TEMP = ROOT_LEDGER + ".tmp";
    private static final String ROOT_SCRIPT_TEMP = DebloatEmergencyScript.ROOT_PATH + ".tmp";

    private final DebloatShell shell;

    RootDebloatReplica(DebloatShell shell) {
        this.shell = java.util.Objects.requireNonNull(shell);
    }

    @Override
    public String readLedger() {
        DebloatShell.ShellResult result = shell.run(
                "if test -L " + ROOT_DIRECTORY + " -o -L " + ROOT_LEDGER
                        + "; then exit 45; fi; "
                        + "if test ! -e " + ROOT_LEDGER + "; then exit 44; fi; "
                        + "test -f " + ROOT_LEDGER + " || exit 46; cat " + ROOT_LEDGER);
        if (!result.timedOut && result.exitCode == 44) {
            return null;
        }
        if (!result.timedOut && result.exitCode == 45) {
            throw new SecurityException("root debloat ledger path is a symlink");
        }
        result.requireSuccess("read root debloat ledger");
        return result.output;
    }

    @Override
    public void write(File localLedger, File localScript) {
        String command = "if test -L " + ROOT_DIRECTORY + " -o -L " + ROOT_LEDGER
                + " -o -L " + ROOT_LEDGER_TEMP + " -o -L "
                + DebloatEmergencyScript.ROOT_PATH + " -o -L " + ROOT_SCRIPT_TEMP
                + "; then exit 45; fi; "
                + "mkdir -p " + ROOT_DIRECTORY + " && test -d " + ROOT_DIRECTORY
                + " && chown 0:0 " + ROOT_DIRECTORY + " && chmod 700 " + ROOT_DIRECTORY
                + " && cp " + PackageId.quotePath(localScript.getAbsolutePath()) + " "
                + ROOT_SCRIPT_TEMP + " && chown 0:0 " + ROOT_SCRIPT_TEMP
                + " && chmod 700 " + ROOT_SCRIPT_TEMP + " && mv -f " + ROOT_SCRIPT_TEMP
                + " " + DebloatEmergencyScript.ROOT_PATH
                + " && cp " + PackageId.quotePath(localLedger.getAbsolutePath()) + " "
                + ROOT_LEDGER_TEMP + " && chown 0:0 " + ROOT_LEDGER_TEMP
                + " && chmod 600 " + ROOT_LEDGER_TEMP + " && mv -f " + ROOT_LEDGER_TEMP
                + " " + ROOT_LEDGER;
        DebloatShell.ShellResult result = shell.run(command);
        if (!result.timedOut && result.exitCode == 45) {
            throw new SecurityException("root debloat state path is a symlink");
        }
        result.requireSuccess("write root debloat state");
    }
}
