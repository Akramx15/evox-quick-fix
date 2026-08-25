package com.codex.evoxquickfix.debloat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class DebloatEmergencyScriptTest {
    private static final String HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Test
    public void rendersExactRestoreCommandsForStatesZeroThroughFourInReverse() {
        DebloatLedger ledger = new DebloatLedger(HASH);
        for (int value = 0; value <= 4; value++) {
            DebloatLedger.Entry entry = ledger.createEntry(
                    "com.example.s" + value, PackageEnabledState.fromValue(value));
            entry.managed = true;
            entry.sequence = value + 1L;
        }
        ledger.nextSequence = 6L;

        String script = DebloatEmergencyScript.render(ledger);

        assertEquals("/data/adb/evox-quick-fix/debloat-restore.sh",
                DebloatBackend.emergencyRestoreScriptPath());
        assertTrue(script.indexOf("pm disable-until-used --user 0 'com.example.s4'")
                < script.indexOf("pm disable-user --user 0 'com.example.s3'"));
        assertTrue(script.indexOf("pm disable-user --user 0 'com.example.s3'")
                < script.indexOf("pm disable --user 0 'com.example.s2'"));
        assertTrue(script.indexOf("pm disable --user 0 'com.example.s2'")
                < script.indexOf("pm enable --user 0 'com.example.s1'"));
        assertTrue(script.indexOf("pm enable --user 0 'com.example.s1'")
                < script.indexOf("pm default-state --user 0 'com.example.s0'"));
    }
}
