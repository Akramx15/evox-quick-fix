package com.codex.evoxquickfix.debloat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.Assume;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class DebloatLedgerStoreTest {
    private static final String HASH_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String HASH_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
            + "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void revisionMismatchSelectsNewerReplicaAndRepairsStaleRoot() throws Exception {
        File directory = temporary.newFolder();
        DebloatBackendTest.MemoryRoot root = new DebloatBackendTest.MemoryRoot();
        DebloatLedgerStore store = new DebloatLedgerStore(directory, root);
        DebloatLedger ledger = store.loadOrCreate(HASH_A);
        String staleRoot = root.ledger;
        ledger.createEntry("com.example.one", PackageEnabledState.DEFAULT);
        store.save(ledger);
        long newestRevision = ledger.revision;
        root.ledger = staleRoot;

        DebloatLedger recovered = new DebloatLedgerStore(directory, root)
                .loadOrCreate(HASH_A);

        assertEquals(newestRevision, recovered.revision);
        assertTrue(root.ledger.contains("\"revision\": " + newestRevision));
        assertTrue(root.ledger.contains("com.example.one"));
    }

    @Test
    public void profileHashMismatchIsRejectedWithoutAdoption() throws Exception {
        File directory = temporary.newFolder();
        DebloatBackendTest.MemoryRoot root = new DebloatBackendTest.MemoryRoot();
        DebloatLedgerStore store = new DebloatLedgerStore(directory, root);
        store.loadOrCreate(HASH_A);

        assertThrows(IllegalStateException.class, () ->
                new DebloatLedgerStore(directory, root).loadOrCreate(HASH_B));
    }

    @Test
    public void localLedgerSymlinkIsRejected() throws Exception {
        File directory = temporary.newFolder();
        File target = temporary.newFile();
        Files.deleteIfExists(new File(directory, DebloatLedgerStore.LOCAL_LEDGER_NAME).toPath());
        try {
            Files.createSymbolicLink(
                    new File(directory, DebloatLedgerStore.LOCAL_LEDGER_NAME).toPath(),
                    target.toPath());
        } catch (Exception unsupportedOnHost) {
            // Windows CI often lacks SeCreateSymbolicLinkPrivilege. Root-path rejection is still
            // exercised below without relying on host filesystem privileges.
            Assume.assumeNoException(unsupportedOnHost);
        }

        DebloatLedgerStore store = new DebloatLedgerStore(
                directory, new DebloatBackendTest.MemoryRoot());
        assertThrows(SecurityException.class, () -> store.loadOrCreate(HASH_A));
    }

    @Test
    public void rootSymlinkExitIsRejected() {
        DebloatShell shell = command -> new DebloatShell.ShellResult(45, "", false);
        assertThrows(SecurityException.class, () -> new RootDebloatReplica(shell).readLedger());
    }
}
