package com.codex.evoxquickfix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LeAudioStatusTest {
    @Test public void activeNeedsCurrentBootSuccessAllPoliciesAndNoPendingUpdate() {
        assertEquals(LeAudioStatus.State.ACTIVE, status(1, 0, 0, 1, 3, 1).state());
        assertEquals(LeAudioStatus.State.PENDING, status(1, 0, 0, 1, 3, 0).state());
        assertEquals(LeAudioStatus.State.PENDING, status(1, 0, 0, 1, 2, 1).state());
        assertEquals(LeAudioStatus.State.PENDING, status(1, 1, 0, 1, 3, 1).state());
    }

    @Test public void disableRetainsRecoveryAfterRomChanges() {
        LeAudioStatus changed = status(1, 0, 0, 0, 0, 0);
        assertFalse(changed.canApply());
        assertTrue(changed.canDisable());
        assertEquals(LeAudioStatus.State.DISABLE_PENDING, status(1, 0, 1, 1, 3, 1).state());
        assertEquals(LeAudioStatus.State.DISABLED, status(1, 0, 1, 1, 0, 0).state());
        assertFalse(status(1, 0, 1, 1, 0, 0).canDisable());
        assertEquals(LeAudioStatus.State.DISABLED, status(1, 0, 1, 0, 0, 0).state());
        assertFalse(status(1, 0, 1, 0, 0, 0).canApply());
    }

    @Test public void failedAndIncompleteInspectionNeverPermitsMutation() {
        for (CommandResult result : new CommandResult[]{
                new CommandResult(41, "LE_AUDIO_BLOCKED:unknown_module", false),
                new CommandResult(0, "present=0\nrom_ok=1", false),
                new CommandResult(0, "", true)}) {
            assertFalse(LeAudioStatus.parse(result).canApply());
            assertFalse(LeAudioStatus.parse(result).canDisable());
        }
    }

    @Test public void legacyUpgradeAndPendingCurrentAreDistinct() {
        LeAudioStatus legacy = versionedStatus(0, "legacy", "none");
        assertEquals(LeAudioStatus.State.UPGRADE_AVAILABLE, legacy.state());
        assertTrue(legacy.canApply());
        assertTrue(legacy.canDisable());
        LeAudioStatus upgrading = versionedStatus(1, "legacy", "current");
        assertEquals(LeAudioStatus.State.PENDING, upgrading.state());
        assertTrue(upgrading.canApply());
        LeAudioStatus oldPending = versionedStatus(1, "legacy", "legacy");
        assertEquals(LeAudioStatus.State.LEGACY_PENDING, oldPending.state());
        assertFalse(oldPending.canApply());
        assertTrue(oldPending.canDisable());
        assertEquals(LeAudioStatus.State.UNKNOWN, versionedStatus(0, "current", "current").state());
        assertEquals(LeAudioStatus.State.UNKNOWN, versionedStatus(1, "current", "none").state());
    }

    private LeAudioStatus versionedStatus(int staged, String current, String update) {
        return LeAudioStatus.parse(new CommandResult(0, "present=1\nstaged=" + staged
                + "\ndisabled=0\nremoved=0\nrom_ok=1\npatched=3\nready=1\nboot_failed=0"
                + "\ncurrent_version=" + current + "\nupdate_version=" + update, false));
    }

    @Test public void persistedOperationsReconcileAfterReboot() {
        DiagnosticReport report = new DiagnosticReport();
        report.leAudio = status(1, 0, 0, 1, 3, 1);
        assertEquals(OperationStateStore.State.APPLIED, OperationStateStore.reconcileState(
                OperationStateStore.OP_LE_AUDIO, report));
        report.leAudio = status(1, 0, 1, 1, 3, 1);
        assertEquals(OperationStateStore.State.REBOOT_REQUIRED, OperationStateStore.reconcileState(
                OperationStateStore.OP_LE_AUDIO_DISABLE, report));
        report.leAudio = status(1, 0, 1, 1, 0, 0);
        assertEquals(OperationStateStore.State.APPLIED, OperationStateStore.reconcileState(
                OperationStateStore.OP_LE_AUDIO_DISABLE, report));
    }

    private LeAudioStatus status(int present, int staged, int disabled, int rom,
                                 int patched, int ready) {
        return LeAudioStatus.parse(new CommandResult(0, "present=" + present + "\nstaged=" + staged
                + "\ndisabled=" + disabled + "\nremoved=0\nrom_ok=" + rom + "\npatched=" + patched
                + "\nready=" + ready + "\nboot_failed=0\ncurrent_version=" + (present == 1 ? "current" : "none")
                + "\nupdate_version=" + (staged == 1 ? "current" : "none"), false));
    }
}
