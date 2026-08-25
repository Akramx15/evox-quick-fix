package com.codex.evoxquickfix.debloat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class DebloatLedgerStore {
    static final String LOCAL_LEDGER_NAME = "ledger-v1.json";
    static final String LOCAL_SCRIPT_NAME = "debloat-restore.sh";

    interface RootReplica {
        /** Returns null only when no root ledger exists. */
        String readLedger();

        void write(File localLedger, File localScript);
    }

    private final File localDirectory;
    private final File localLedger;
    private final File localScript;
    private final RootReplica root;
    private final DebloatLedgerCodec codec = new DebloatLedgerCodec();

    DebloatLedgerStore(File localDirectory, RootReplica root) {
        this.localDirectory = java.util.Objects.requireNonNull(localDirectory);
        this.localLedger = new File(localDirectory, LOCAL_LEDGER_NAME);
        this.localScript = new File(localDirectory, LOCAL_SCRIPT_NAME);
        this.root = java.util.Objects.requireNonNull(root);
    }

    synchronized DebloatLedger loadOrCreate(String expectedProfileHash) {
        DebloatLedger.requireProfileHash(expectedProfileHash);
        DebloatLedger selected = loadExistingForRestore();
        if (selected == null) {
            selected = new DebloatLedger(expectedProfileHash);
            save(selected);
            return selected;
        }
        requireProfile(selected, expectedProfileHash);
        return selected;
    }

    /**
     * Loads and reconciles an existing ownership ledger without coupling recovery to the current
     * profile. This method never creates a ledger and never relaxes schema/revision validation.
     */
    synchronized DebloatLedger loadExistingForRestore() {
        assertLocalSafe();
        DebloatLedger local = readLocal();
        DebloatLedger remote = decodeNullable(root.readLedger());

        DebloatLedger selected;
        if (local == null && remote == null) {
            return null;
        } else if (local == null) {
            selected = remote;
        } else if (remote == null) {
            selected = local;
        } else if (local.revision == remote.revision) {
            String localCanonical = codec.encode(local);
            String remoteCanonical = codec.encode(remote);
            if (!localCanonical.equals(remoteCanonical)) {
                throw new IllegalStateException(
                        "local/root debloat ledgers differ at the same revision");
            }
            return local;
        } else {
            // A crash can occur between the two atomic replica updates. The larger revision is
            // authoritative; schema validation and equal-revision conflict detection remain
            // strict, while the apply path validates the current profile separately.
            selected = local.revision > remote.revision ? local : remote;
        }
        writeReplicas(selected);
        return selected;
    }

    /** Starts a new profile only after every prior ledger-owned state has been restored. */
    synchronized void prepareProfile(String expectedProfileHash) {
        DebloatLedger.requireProfileHash(expectedProfileHash);
        DebloatLedger existing = loadExistingForRestore();
        if (existing == null || expectedProfileHash.equals(existing.profileHash)) {
            return;
        }
        if (existing.hasOwnership()) {
            throw new IllegalStateException(
                    "debloat profile changed while the previous profile still owns packages");
        }
        DebloatLedger replacement = new DebloatLedger(expectedProfileHash);
        // Never move the replica revision backwards; otherwise a stale peer could win recovery.
        replacement.revision = existing.revision;
        save(replacement);
    }

    synchronized void save(DebloatLedger ledger) {
        ledger.validate();
        if (ledger.revision == Long.MAX_VALUE) {
            throw new IllegalStateException("debloat ledger revision exhausted");
        }
        ledger.revision++;
        writeReplicas(ledger);
    }

    File localLedgerFileForTests() {
        return localLedger;
    }

    private void writeReplicas(DebloatLedger ledger) {
        ledger.validate();
        assertLocalSafe();
        writeLocalAtomically(localLedger, codec.encode(ledger));
        writeLocalAtomically(localScript, DebloatEmergencyScript.render(ledger));
        root.write(localLedger, localScript);
    }

    private DebloatLedger readLocal() {
        if (!localLedger.exists()) {
            return null;
        }
        if (Files.isSymbolicLink(localLedger.toPath()) || !localLedger.isFile()) {
            throw new SecurityException("unsafe local debloat ledger path");
        }
        try {
            return codec.decode(new String(Files.readAllBytes(localLedger.toPath()),
                    StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new IllegalStateException("cannot read local debloat ledger", failure);
        }
    }

    private DebloatLedger decodeNullable(String value) {
        return value == null ? null : codec.decode(value);
    }

    private static void requireProfile(DebloatLedger ledger, String expected) {
        if (ledger != null && !expected.equals(ledger.profileHash)) {
            throw new IllegalStateException("debloat profile hash mismatch");
        }
    }

    private void assertLocalSafe() {
        Path directory = localDirectory.toPath();
        try {
            if (Files.isSymbolicLink(directory)) {
                throw new SecurityException("local debloat directory is a symlink");
            }
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectories(directory);
            }
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(directory)) {
                throw new SecurityException("local debloat path is not a real directory");
            }
            assertNotSymlink(localLedger.toPath());
            assertNotSymlink(localScript.toPath());
            assertNotSymlink(new File(localLedger.getPath() + ".tmp").toPath());
            assertNotSymlink(new File(localScript.getPath() + ".tmp").toPath());
        } catch (IOException failure) {
            throw new IllegalStateException("cannot prepare local debloat directory", failure);
        }
    }

    private static void assertNotSymlink(Path path) {
        if (Files.isSymbolicLink(path)) {
            throw new SecurityException("unsafe symlink: " + path.getFileName());
        }
    }

    private static void writeLocalAtomically(File target, String value) {
        File temporary = new File(target.getPath() + ".tmp");
        if (Files.isSymbolicLink(temporary.toPath()) || Files.isSymbolicLink(target.toPath())) {
            throw new SecurityException("unsafe local atomic-write path");
        }
        try (FileOutputStream output = new FileOutputStream(temporary, false)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        } catch (IOException failure) {
            throw new IllegalStateException("cannot stage local debloat state", failure);
        }
        try {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException failure) {
            throw new IllegalStateException("local filesystem lacks atomic rename", failure);
        } catch (IOException failure) {
            throw new IllegalStateException("cannot commit local debloat state", failure);
        }
    }
}
