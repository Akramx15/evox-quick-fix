package com.codex.evoxquickfix.debloat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class DebloatBackendTest {
    private static final String HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void successAndRestoreUseExactStatesInReverseOrder() throws Exception {
        Fixture fixture = fixture();
        fixture.states.put("com.example.one", PackageEnabledState.DEFAULT);
        fixture.states.put("com.example.two", PackageEnabledState.ENABLED);

        DebloatResult applied = fixture.backend.apply(List.of(
                new DebloatTarget("com.example.one", false),
                new DebloatTarget("com.example.two", false)), null);

        assertEquals(List.of("com.example.one", "com.example.two"), applied.changed);
        assertEquals(PackageEnabledState.DISABLED_USER,
                fixture.states.get("com.example.one"));
        assertEquals(PackageEnabledState.DISABLED_USER,
                fixture.states.get("com.example.two"));

        DebloatResult restored = fixture.backend.restoreAll();
        assertEquals(List.of("com.example.two", "com.example.one"), restored.changed);
        assertEquals(PackageEnabledState.DEFAULT, fixture.states.get("com.example.one"));
        assertEquals(PackageEnabledState.ENABLED, fixture.states.get("com.example.two"));
        assertEquals(List.of(
                "pm disable-user --user 0 'com.example.one'",
                "pm disable-user --user 0 'com.example.two'",
                "pm enable --user 0 'com.example.two'",
                "pm default-state --user 0 'com.example.one'"), fixture.shell.mutations);
        assertTrue(fixture.backend.managedPackages().isEmpty());
    }

    @Test
    public void midBatchFailureRollsBackCompletedStepsInReverse() throws Exception {
        Fixture fixture = fixture();
        fixture.states.put("com.example.one", PackageEnabledState.DEFAULT);
        fixture.states.put("com.example.two", PackageEnabledState.ENABLED);
        fixture.shell.failCommand = "pm disable-user --user 0 'com.example.two'";

        assertThrows(IllegalStateException.class, () -> fixture.backend.apply(List.of(
                new DebloatTarget("com.example.one", false),
                new DebloatTarget("com.example.two", false)), null));

        assertEquals(PackageEnabledState.DEFAULT, fixture.states.get("com.example.one"));
        assertEquals(PackageEnabledState.ENABLED, fixture.states.get("com.example.two"));
        assertEquals(List.of(
                "pm disable-user --user 0 'com.example.one'",
                "pm disable-user --user 0 'com.example.two'",
                "pm default-state --user 0 'com.example.one'"), fixture.shell.mutations);
        assertTrue(fixture.backend.managedPackages().isEmpty());
    }

    @Test
    public void failedPostBatchHealthCheckRollsBackOnlyActiveBatchInReverse() throws Exception {
        Fixture fixture = fixture();
        fixture.states.put("com.example.one", PackageEnabledState.DEFAULT);
        fixture.states.put("com.example.two", PackageEnabledState.ENABLED);
        AtomicInteger checks = new AtomicInteger();

        assertThrows(IllegalStateException.class, () -> fixture.backend.apply(List.of(
                new DebloatTarget("com.example.one", false),
                new DebloatTarget("com.example.two", false)), null,
                () -> {
                    if (checks.incrementAndGet() == 3) {
                        throw new IllegalStateException("health invariant changed");
                    }
                }));

        assertEquals(PackageEnabledState.DEFAULT, fixture.states.get("com.example.one"));
        assertEquals(PackageEnabledState.ENABLED, fixture.states.get("com.example.two"));
        assertEquals(List.of(
                "pm disable-user --user 0 'com.example.one'",
                "pm disable-user --user 0 'com.example.two'",
                "pm enable --user 0 'com.example.two'",
                "pm default-state --user 0 'com.example.one'"), fixture.shell.mutations);
        assertTrue(fixture.backend.managedPackages().isEmpty());
        assertEquals(3, checks.get());
    }

    @Test
    public void perStepHealthFailureStopsBeforeNextMutationAndRollsBackReverse() throws Exception {
        Fixture fixture = fixture();
        fixture.states.put("com.example.one", PackageEnabledState.DEFAULT);
        fixture.states.put("com.example.two", PackageEnabledState.ENABLED);
        fixture.states.put("com.example.three", PackageEnabledState.DEFAULT);
        AtomicInteger checks = new AtomicInteger();

        assertThrows(IllegalStateException.class, () -> fixture.backend.apply(List.of(
                new DebloatTarget("com.example.one", false),
                new DebloatTarget("com.example.two", false),
                new DebloatTarget("com.example.three", false)), null,
                () -> {
                    if (checks.incrementAndGet() == 2) {
                        throw new IllegalStateException("holder became unavailable");
                    }
                }));

        assertEquals(2, checks.get());
        assertEquals(List.of(
                "pm disable-user --user 0 'com.example.one'",
                "pm disable-user --user 0 'com.example.two'",
                "pm enable --user 0 'com.example.two'",
                "pm default-state --user 0 'com.example.one'"), fixture.shell.mutations);
        assertEquals(PackageEnabledState.DEFAULT, fixture.states.get("com.example.one"));
        assertEquals(PackageEnabledState.ENABLED, fixture.states.get("com.example.two"));
        assertEquals(PackageEnabledState.DEFAULT, fixture.states.get("com.example.three"));
        assertTrue(fixture.backend.managedPackages().isEmpty());
    }

    @Test
    public void interruptedPendingJournalIsRecoveredInReverse() throws Exception {
        Fixture fixture = fixture();
        fixture.states.put("com.example.one", PackageEnabledState.DISABLED_USER);
        fixture.states.put("com.example.two", PackageEnabledState.DISABLED_USER);
        DebloatLedger ledger = fixture.store.loadOrCreate(HASH);
        DebloatLedger.Entry one = ledger.createEntry(
                "com.example.one", PackageEnabledState.DEFAULT);
        one.managed = true;
        one.sequence = 1L;
        DebloatLedger.Entry two = ledger.createEntry(
                "com.example.two", PackageEnabledState.ENABLED);
        two.pending = true;
        two.sequence = 2L;
        ledger.nextSequence = 3L;
        ledger.phase = DebloatLedger.Phase.APPLYING;
        ledger.activeOrder.add("com.example.one");
        ledger.activeOrder.add("com.example.two");
        fixture.store.save(ledger);

        DebloatResult result = fixture.backend.recoverIfNeeded();

        assertEquals(List.of("com.example.two", "com.example.one"), result.changed);
        assertEquals(List.of(
                "pm enable --user 0 'com.example.two'",
                "pm default-state --user 0 'com.example.one'"), fixture.shell.mutations);
        assertEquals(PackageEnabledState.DEFAULT, fixture.states.get("com.example.one"));
        assertEquals(PackageEnabledState.ENABLED, fixture.states.get("com.example.two"));
        assertTrue(fixture.backend.managedPackages().isEmpty());
    }

    @Test
    public void applyRecoversBeforeRefreshingDynamicProtection() throws Exception {
        Fixture fixture = fixture();
        fixture.states.put("com.example.one", PackageEnabledState.DISABLED_USER);
        fixture.states.put("com.example.two", PackageEnabledState.DEFAULT);
        DebloatLedger ledger = fixture.store.loadOrCreate(HASH);
        DebloatLedger.Entry interrupted = ledger.createEntry(
                "com.example.one", PackageEnabledState.DEFAULT);
        interrupted.pending = true;
        interrupted.sequence = 1L;
        ledger.nextSequence = 2L;
        ledger.phase = DebloatLedger.Phase.APPLYING;
        ledger.activeOrder.add("com.example.one");
        fixture.store.save(ledger);

        DebloatProtectionPolicy base = DebloatProtectionPolicy.fromSnapshot(
                Set.of(), Map.of(), Set.of());
        AtomicBoolean capturedAfterRecovery = new AtomicBoolean();
        DebloatBackend backend = new DebloatBackend(HASH, fixture.store, fixture.shell,
                fixture.states::get, base, () -> {
                    assertEquals(PackageEnabledState.DEFAULT,
                            fixture.states.get("com.example.one"));
                    capturedAfterRecovery.set(true);
                    return base;
                }, false);

        backend.apply(List.of(new DebloatTarget("com.example.two", false)), null);

        assertTrue(capturedAfterRecovery.get());
        assertEquals(List.of(
                "pm default-state --user 0 'com.example.one'",
                "pm disable-user --user 0 'com.example.two'"), fixture.shell.mutations);
    }

    @Test
    public void crashAfterOneRollbackStepReplaysJournalIdempotently() throws Exception {
        Fixture fixture = fixture();
        fixture.states.put("com.example.one", PackageEnabledState.DISABLED_USER);
        fixture.states.put("com.example.two", PackageEnabledState.ENABLED);
        DebloatLedger ledger = fixture.store.loadOrCreate(HASH);
        DebloatLedger.Entry one = ledger.createEntry(
                "com.example.one", PackageEnabledState.DEFAULT);
        one.managed = true;
        one.sequence = 1L;
        DebloatLedger.Entry two = ledger.createEntry(
                "com.example.two", PackageEnabledState.ENABLED);
        // Package two was restored immediately before the simulated process death. Its sequence
        // remains until the transaction's final durable commit.
        two.sequence = 2L;
        ledger.nextSequence = 3L;
        ledger.phase = DebloatLedger.Phase.ROLLING_BACK;
        ledger.activeOrder.add("com.example.one");
        ledger.activeOrder.add("com.example.two");
        fixture.store.save(ledger);

        fixture.backend.recoverIfNeeded();

        assertEquals(List.of("pm default-state --user 0 'com.example.one'"),
                fixture.shell.mutations);
        assertEquals(PackageEnabledState.DEFAULT, fixture.states.get("com.example.one"));
        assertEquals(PackageEnabledState.ENABLED, fixture.states.get("com.example.two"));
        assertTrue(fixture.backend.managedPackages().isEmpty());
    }

    @Test
    public void externallyDisabledPackageIsRecordedButNeverOwnedOrEnabled() throws Exception {
        Fixture fixture = fixture();
        fixture.states.put("com.example.external", PackageEnabledState.DISABLED_USER);

        DebloatResult result = fixture.backend.apply(
                List.of(new DebloatTarget("com.example.external", false)), null);

        assertEquals(List.of("com.example.external"), result.external);
        assertTrue(fixture.shell.mutations.isEmpty());
        fixture.backend.restoreAll();
        assertTrue(fixture.shell.mutations.isEmpty());
        DebloatLedger ledger = fixture.store.loadOrCreate(HASH);
        DebloatLedger.Entry entry = ledger.entry("com.example.external");
        assertEquals(PackageEnabledState.DISABLED_USER, entry.baselineState);
        assertFalse(entry.managed);
        assertFalse(entry.pending);
    }

    @Test
    public void restoreIgnoresChangedProfileAndDegradedProtectionDiscovery() throws Exception {
        Fixture fixture = fixture();
        fixture.states.put("com.example.one", PackageEnabledState.DEFAULT);
        fixture.backend.apply(List.of(new DebloatTarget("com.example.one", false)), null);
        DebloatProtectionPolicy base = DebloatProtectionPolicy.fromSnapshot(
                Set.of(), Map.of(), Set.of());
        DebloatBackend restoreOnly = new DebloatBackend(
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                fixture.store, fixture.shell, fixture.states::get, base,
                () -> { throw new IllegalStateException("RoleManager is degraded"); }, true);

        DebloatResult restored = restoreOnly.restoreAll();

        assertEquals(List.of("com.example.one"), restored.changed);
        assertEquals(PackageEnabledState.DEFAULT, fixture.states.get("com.example.one"));
        assertTrue(restoreOnly.managedPackages().isEmpty());

        fixture.states.put("com.example.two", PackageEnabledState.DEFAULT);
        DebloatBackend nextProfile = new DebloatBackend(
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                fixture.store, fixture.shell, fixture.states::get, base);
        DebloatResult reapplied = nextProfile.apply(
                List.of(new DebloatTarget("com.example.two", false)), null);
        assertEquals(List.of("com.example.two"), reapplied.changed);
    }

    @Test
    public void dangerousTargetMustBeSingleAndTypedExactly() throws Exception {
        Fixture fixture = fixture();
        fixture.states.put("com.example.danger", PackageEnabledState.DEFAULT);

        assertThrows(SecurityException.class, () -> fixture.backend.apply(
                List.of(new DebloatTarget("com.example.danger", true)), "wrong.id"));
        assertThrows(SecurityException.class, () -> fixture.backend.apply(List.of(
                new DebloatTarget("com.example.danger", true),
                new DebloatTarget("com.example.other", false)), "com.example.danger"));

        DebloatResult result = fixture.backend.apply(
                List.of(new DebloatTarget("com.example.danger", true)),
                "com.example.danger");
        assertEquals(List.of("com.example.danger"), result.changed);
    }

    @Test
    public void batchIsLimitedToFiveAndHardProtectionIsFailClosed() throws Exception {
        Fixture fixture = fixture();
        List<DebloatTarget> six = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            six.add(new DebloatTarget("com.example.p" + index, false));
        }
        assertThrows(IllegalArgumentException.class, () -> fixture.backend.apply(six, null));
        assertThrows(SecurityException.class, () -> fixture.backend.apply(
                List.of(new DebloatTarget("com.google.android.googlequicksearchbox", false)),
                null));
        assertTrue(fixture.shell.mutations.isEmpty());
    }

    private Fixture fixture() throws Exception {
        File directory = temporary.newFolder();
        MemoryRoot root = new MemoryRoot();
        DebloatLedgerStore store = new DebloatLedgerStore(directory, root);
        Map<String, PackageEnabledState> states = new HashMap<>();
        FakeMutationShell shell = new FakeMutationShell(states);
        DebloatProtectionPolicy policy = DebloatProtectionPolicy.fromSnapshot(
                Collections.emptySet(), Collections.emptyMap(), Collections.emptySet());
        DebloatBackend backend = new DebloatBackend(HASH, store, shell, states::get, policy);
        return new Fixture(backend, store, states, shell, root);
    }

    private static final class Fixture {
        final DebloatBackend backend;
        final DebloatLedgerStore store;
        final Map<String, PackageEnabledState> states;
        final FakeMutationShell shell;
        final MemoryRoot root;

        Fixture(DebloatBackend backend, DebloatLedgerStore store,
                Map<String, PackageEnabledState> states, FakeMutationShell shell,
                MemoryRoot root) {
            this.backend = backend;
            this.store = store;
            this.states = states;
            this.shell = shell;
            this.root = root;
        }
    }

    static final class MemoryRoot implements DebloatLedgerStore.RootReplica {
        String ledger;
        String script;

        @Override public String readLedger() {
            return ledger;
        }

        @Override public void write(File localLedger, File localScript) {
            try {
                ledger = new String(Files.readAllBytes(localLedger.toPath()),
                        StandardCharsets.UTF_8);
                script = new String(Files.readAllBytes(localScript.toPath()),
                        StandardCharsets.UTF_8);
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        }
    }

    static final class FakeMutationShell implements DebloatShell {
        final Map<String, PackageEnabledState> states;
        final List<String> mutations = new ArrayList<>();
        String failCommand;

        FakeMutationShell(Map<String, PackageEnabledState> states) {
            this.states = states;
        }

        @Override public ShellResult run(String command) {
            mutations.add(command);
            if (command.equals(failCommand)) {
                return new ShellResult(9, "injected failure", false);
            }
            String packageName = command.substring(command.lastIndexOf(' ') + 1)
                    .replace("'", "");
            if (command.startsWith("pm disable-user")) {
                states.put(packageName, PackageEnabledState.DISABLED_USER);
            } else if (command.startsWith("pm default-state")) {
                states.put(packageName, PackageEnabledState.DEFAULT);
            } else if (command.startsWith("pm enable")) {
                states.put(packageName, PackageEnabledState.ENABLED);
            } else if (command.startsWith("pm disable-until-used")) {
                states.put(packageName, PackageEnabledState.DISABLED_UNTIL_USED);
            } else if (command.startsWith("pm disable")) {
                states.put(packageName, PackageEnabledState.DISABLED);
            }
            return new ShellResult(0, "ok", false);
        }
    }
}
