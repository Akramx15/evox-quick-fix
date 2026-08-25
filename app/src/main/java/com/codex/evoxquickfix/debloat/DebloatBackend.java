package com.codex.evoxquickfix.debloat;

import com.codex.evoxquickfix.OperationCoordinator;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Transactional user-0 package disable/restore backend. */
public final class DebloatBackend {
    public static final int MAX_BATCH_SIZE = 5;
    private static final Object TRANSACTION_LOCK = new Object();

    @FunctionalInterface
    public interface HealthVerifier {
        void verify();
    }

    @FunctionalInterface
    interface ProtectionProvider {
        DebloatProtectionPolicy capture();
    }

    private final String profileHash;
    private final DebloatLedgerStore store;
    private final DebloatShell shell;
    private final PackageStateSource states;
    private final ProtectionProvider protectionProvider;
    private final boolean restoreOnly;
    private volatile DebloatProtectionPolicy protection;

    /**
     * Opens the production backend and captures current role/contextual-provider protection.
     * The supplied directory must be private to this application (for example noBackupFilesDir).
     */
    @SuppressWarnings("try")
    public static DebloatBackend open(File appPrivateDirectory, String profileHash,
                                       Set<String> additionalHardProtection) {
        java.util.Objects.requireNonNull(appPrivateDirectory);
        DebloatShell shell = new ProcessDebloatShell();
        Set<String> additional = additionalHardProtection == null
                ? Collections.emptySet() : Set.copyOf(additionalHardProtection);
        DebloatProtectionPolicy baseProtection = DebloatProtectionPolicy.fromSnapshot(
                additional, Collections.emptyMap(), Collections.emptySet());
        File localDirectory = new File(appPrivateDirectory, "debloat");
        DebloatBackend backend = new DebloatBackend(profileHash,
                new DebloatLedgerStore(localDirectory, new RootDebloatReplica(shell)),
                shell, new ShellPackageStateSource(shell), baseProtection,
                () -> DebloatProtectionPolicy.runtime(shell, additional), false);
        try (OperationCoordinator.Lease ignored =
                     OperationCoordinator.acquire("open debloat backend")) {
            // Recovery must not depend on services which the interrupted batch may have broken.
            backend.recoverIfNeeded();
            backend.refreshProtection();
        }
        return backend;
    }

    /** Opens a restore-only backend without current-profile or live-role dependencies. */
    public static DebloatBackend openForRestore(File appPrivateDirectory) {
        java.util.Objects.requireNonNull(appPrivateDirectory);
        DebloatShell shell = new ProcessDebloatShell();
        DebloatProtectionPolicy baseProtection = DebloatProtectionPolicy.fromSnapshot(
                Collections.emptySet(), Collections.emptyMap(), Collections.emptySet());
        File localDirectory = new File(appPrivateDirectory, "debloat");
        DebloatBackend backend = new DebloatBackend(null,
                new DebloatLedgerStore(localDirectory, new RootDebloatReplica(shell)),
                shell, new ShellPackageStateSource(shell), baseProtection,
                () -> baseProtection, true);
        backend.recoverIfNeeded();
        return backend;
    }

    DebloatBackend(String profileHash, DebloatLedgerStore store, DebloatShell shell,
                    PackageStateSource states, DebloatProtectionPolicy protection) {
        this(profileHash, store, shell, states, protection, () -> protection, false);
    }

    DebloatBackend(String profileHash, DebloatLedgerStore store, DebloatShell shell,
                    PackageStateSource states, DebloatProtectionPolicy protection,
                    ProtectionProvider protectionProvider, boolean restoreOnly) {
        this.profileHash = restoreOnly ? profileHash : DebloatLedger.requireProfileHash(profileHash);
        this.store = java.util.Objects.requireNonNull(store);
        this.shell = java.util.Objects.requireNonNull(shell);
        this.states = java.util.Objects.requireNonNull(states);
        this.protection = java.util.Objects.requireNonNull(protection);
        this.protectionProvider = java.util.Objects.requireNonNull(protectionProvider);
        this.restoreOnly = restoreOnly;
    }

    public DebloatResult apply(List<DebloatTarget> targets, String typedDangerousPackageId) {
        return apply(targets, typedDangerousPackageId, () -> {});
    }

    /**
     * Applies a batch and runs the caller's invariant checks before the journal is finalized.
     * A verifier failure rolls back only this active batch in reverse order.
     */
    @SuppressWarnings("try")
    public DebloatResult apply(List<DebloatTarget> targets,
                               String typedDangerousPackageId,
                               HealthVerifier verifier) {
        try (OperationCoordinator.Lease ignored =
                     OperationCoordinator.acquire("debloat apply")) {
            synchronized (TRANSACTION_LOCK) {
                return applyTransaction(targets, typedDangerousPackageId, verifier);
            }
        }
    }

    private DebloatResult applyTransaction(List<DebloatTarget> targets,
                                            String typedDangerousPackageId,
                                            HealthVerifier verifier) {
        if (restoreOnly) {
            throw new SecurityException("restore-only backend cannot apply a debloat profile");
        }
        java.util.Objects.requireNonNull(verifier);
        // Restore any interrupted transaction before discovering live holders. An interrupted
        // disable may itself have degraded RoleManager, the IME, or contextual search.
        recoverIfNeededLocked();
        refreshProtection();
        validateRequest(targets, typedDangerousPackageId);
        store.prepareProfile(profileHash);
        DebloatLedger ledger = store.loadOrCreate(profileHash);

        List<String> changed = new ArrayList<>();
        List<String> absent = new ArrayList<>();
        List<String> external = new ArrayList<>();
        List<String> alreadyManaged = new ArrayList<>();
        try {
            for (DebloatTarget target : targets) {
                String packageName = target.packageName;
                PackageEnabledState current = states.stateForUserZero(packageName);
                if (current == null) {
                    absent.add(packageName);
                    continue;
                }

                DebloatLedger.Entry entry = ledger.entry(packageName);
                if (entry != null && entry.managed) {
                    if (current == PackageEnabledState.DISABLED_USER) {
                        alreadyManaged.add(packageName);
                        continue;
                    }
                    if (current == entry.baselineState) {
                        // The emergency script (or an external actor) already restored our exact
                        // baseline. Relinquish ownership before starting a new transaction.
                        entry.managed = false;
                        entry.pending = false;
                        entry.sequence = 0L;
                        store.save(ledger);
                    } else {
                        external.add(packageName);
                        continue;
                    }
                }

                if (entry == null) {
                    entry = ledger.createEntry(packageName, current);
                    store.save(ledger);
                } else if (entry.baselineState != current) {
                    // Never adopt or overwrite a state changed outside this backend.
                    external.add(packageName);
                    continue;
                }

                if (current.isExternallyDisabledBaseline()) {
                    external.add(packageName);
                    continue;
                }

                beginStep(ledger, entry);
                runMutation(PackageEnabledState.disableUserCommand(packageName),
                        "disable " + packageName);
                requireState(packageName, PackageEnabledState.DISABLED_USER);
                // Stop before touching the next package if this mutation changed a protected
                // role/provider/IME or another caller-provided health invariant.
                verifier.verify();
                entry.pending = false;
                entry.managed = true;
                store.save(ledger);
                changed.add(packageName);
            }
            // Close the small window between the last per-step check and the final journal writes.
            verifier.verify();
            finishTransaction(ledger);
            return new DebloatResult(changed, absent, external, alreadyManaged);
        } catch (Throwable failure) {
            Throwable rollback = rollbackActive(ledger);
            if (rollback != null) {
                failure.addSuppressed(rollback);
            }
            throw rethrow("debloat apply failed", failure);
        }
    }

    /** Restores every package owned by this backend, in reverse application order. */
    @SuppressWarnings("try")
    public DebloatResult restoreAll() {
        try (OperationCoordinator.Lease ignored =
                     OperationCoordinator.acquire("debloat restore")) {
            synchronized (TRANSACTION_LOCK) {
                return restoreAllLocked();
            }
        }
    }

    private DebloatResult restoreAllLocked() {
        DebloatLedger ledger = store.loadExistingForRestore();
        if (ledger == null) {
            return emptyResult();
        }
        recoverActiveTransaction(ledger);
        List<DebloatLedger.Entry> managed = new ArrayList<>();
        for (DebloatLedger.Entry entry : ledger.entries.values()) {
            if (entry.managed) {
                managed.add(entry);
            }
        }
        managed.sort(Comparator.comparingLong(item -> item.sequence));
        if (managed.isEmpty()) {
            return emptyResult();
        }
        ledger.phase = DebloatLedger.Phase.ROLLING_BACK;
        ledger.activeOrder.clear();
        for (DebloatLedger.Entry entry : managed) {
            ledger.activeOrder.add(entry.packageName);
        }
        store.save(ledger);
        Throwable failure = rollbackActive(ledger);
        if (failure != null) {
            throw rethrow("debloat restore failed", failure);
        }
        List<String> restored = new ArrayList<>();
        for (int index = managed.size() - 1; index >= 0; index--) {
            restored.add(managed.get(index).packageName);
        }
        return new DebloatResult(restored, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
    }

    /** Rolls back an interrupted apply/restore and reconciles a manually-run emergency script. */
    @SuppressWarnings("try")
    public DebloatResult recoverIfNeeded() {
        try (OperationCoordinator.Lease ignored =
                     OperationCoordinator.acquire("debloat recovery")) {
            synchronized (TRANSACTION_LOCK) {
                return recoverIfNeededLocked();
            }
        }
    }

    private DebloatResult recoverIfNeededLocked() {
        DebloatLedger ledger = store.loadExistingForRestore();
        if (ledger == null) {
            return emptyResult();
        }
        List<String> restored = new ArrayList<>();
        if (ledger.phase != DebloatLedger.Phase.IDLE || !ledger.activeOrder.isEmpty()
                || hasPending(ledger)) {
            List<String> reverse = new ArrayList<>(ledger.activeOrder);
            Collections.reverse(reverse);
            Throwable failure = rollbackActive(ledger);
            if (failure != null) {
                throw rethrow("debloat crash recovery failed", failure);
            }
            restored.addAll(reverse);
        }

        boolean changed = false;
        for (DebloatLedger.Entry entry : ledger.entries.values()) {
            if (!entry.managed) continue;
            PackageEnabledState current = states.stateForUserZero(entry.packageName);
            if (current == entry.baselineState) {
                entry.managed = false;
                entry.pending = false;
                entry.sequence = 0L;
                changed = true;
                restored.add(entry.packageName);
            }
        }
        if (changed) {
            store.save(ledger);
        }
        return new DebloatResult(restored, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
    }

    public List<String> managedPackages() {
        synchronized (TRANSACTION_LOCK) {
            return managedPackagesLocked();
        }
    }

    private List<String> managedPackagesLocked() {
        DebloatLedger ledger = store.loadExistingForRestore();
        if (ledger == null) {
            return Collections.emptyList();
        }
        List<DebloatLedger.Entry> entries = new ArrayList<>();
        for (DebloatLedger.Entry entry : ledger.entries.values()) {
            if (entry.managed || entry.pending) entries.add(entry);
        }
        entries.sort(Comparator.comparingLong(item -> item.sequence));
        List<String> result = new ArrayList<>();
        for (DebloatLedger.Entry entry : entries) result.add(entry.packageName);
        return Collections.unmodifiableList(result);
    }

    public PackageEnabledState currentState(String packageName) {
        synchronized (TRANSACTION_LOCK) {
            return states.stateForUserZero(PackageId.requireValid(packageName));
        }
    }

    public boolean isProtected(String packageName) {
        return protection.isProtected(PackageId.requireValid(packageName));
    }

    public String protectionReason(String packageName) {
        return protection.reason(PackageId.requireValid(packageName));
    }

    public Set<String> protectedPackages() {
        return protection.protectedPackages();
    }

    public static String emergencyRestoreScriptPath() {
        return DebloatEmergencyScript.ROOT_PATH;
    }

    private void refreshProtection() {
        if (!restoreOnly) {
            protection = java.util.Objects.requireNonNull(protectionProvider.capture());
        }
    }

    private void validateRequest(List<DebloatTarget> targets, String typedId) {
        if (targets == null || targets.isEmpty() || targets.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("debloat batch must contain 1..5 packages");
        }
        Set<String> unique = new HashSet<>();
        int dangerous = 0;
        DebloatTarget dangerousTarget = null;
        for (DebloatTarget target : targets) {
            if (target == null || !unique.add(target.packageName)) {
                throw new IllegalArgumentException("duplicate/null debloat target");
            }
            if (protection.isProtected(target.packageName)) {
                throw new SecurityException("protected package " + target.packageName + ": "
                        + protection.reason(target.packageName));
            }
            if (target.dangerous) {
                dangerous++;
                dangerousTarget = target;
            }
        }
        if (dangerous > 0 && (targets.size() != 1 || dangerous != 1
                || !dangerousTarget.packageName.equals(typedId))) {
            throw new SecurityException(
                    "a dangerous package requires a single-item request and typed package id");
        }
    }

    private void beginStep(DebloatLedger ledger, DebloatLedger.Entry entry) {
        if (ledger.phase == DebloatLedger.Phase.IDLE) {
            ledger.phase = DebloatLedger.Phase.APPLYING;
            ledger.activeOrder.clear();
        } else if (ledger.phase != DebloatLedger.Phase.APPLYING) {
            throw new IllegalStateException("debloat ledger is not ready for apply");
        }
        entry.sequence = ledger.nextSequence++;
        entry.pending = true;
        ledger.activeOrder.add(entry.packageName);
        store.save(ledger);
    }

    private void finishTransaction(DebloatLedger ledger) {
        if (ledger.phase == DebloatLedger.Phase.IDLE) {
            return;
        }
        DebloatLedger.Phase previousPhase = ledger.phase;
        List<String> previousOrder = new ArrayList<>(ledger.activeOrder);
        ledger.phase = DebloatLedger.Phase.IDLE;
        ledger.activeOrder.clear();
        try {
            store.save(ledger);
        } catch (Throwable failure) {
            // Keep the in-memory journal recoverable so the caller's catch block rolls back all
            // packages even if the final stable-marker replica write failed.
            ledger.phase = previousPhase;
            ledger.activeOrder.addAll(previousOrder);
            throw failure;
        }
    }

    private void recoverActiveTransaction(DebloatLedger ledger) {
        if (ledger.phase == DebloatLedger.Phase.IDLE && ledger.activeOrder.isEmpty()
                && !hasPending(ledger)) {
            return;
        }
        Throwable rollback = rollbackActive(ledger);
        if (rollback != null) {
            throw rethrow("unfinished debloat transaction could not be recovered", rollback);
        }
    }

    /** Returns a combined failure after attempting every reverse-order restore. */
    private Throwable rollbackActive(DebloatLedger ledger) {
        if (ledger.activeOrder.isEmpty()) {
            if (ledger.phase != DebloatLedger.Phase.IDLE || hasPending(ledger)) {
                return new IllegalStateException("corrupt debloat transaction journal");
            }
            return null;
        }
        ledger.phase = DebloatLedger.Phase.ROLLING_BACK;
        try {
            store.save(ledger);
        } catch (Throwable persistenceFailure) {
            return new IllegalStateException("cannot mark rollback pending: "
                    + message(persistenceFailure), persistenceFailure);
        }
        List<String> originalOrder = new ArrayList<>(ledger.activeOrder);
        List<String> failures = new ArrayList<>();
        List<String> failedPackages = new ArrayList<>();
        for (int index = ledger.activeOrder.size() - 1; index >= 0; index--) {
            String packageName = ledger.activeOrder.get(index);
            DebloatLedger.Entry entry = ledger.entry(packageName);
            try {
                entry.pending = true;
                store.save(ledger);
                PackageEnabledState current = states.stateForUserZero(packageName);
                if (current == null) {
                    throw new IllegalStateException("package disappeared");
                }
                if (current != entry.baselineState) {
                    if (current != PackageEnabledState.DISABLED_USER) {
                        throw new IllegalStateException("external enabled-state conflict: "
                                + current.value);
                    }
                    runMutation(entry.baselineState.exactRestoreCommand(packageName),
                            "restore " + packageName);
                    requireState(packageName, entry.baselineState);
                }
                entry.pending = false;
                entry.managed = false;
                // Keep the sequence until the whole rollback is durably finalized. If the
                // process dies here, the next recovery pass can safely replay this entry.
                store.save(ledger);
            } catch (Throwable failure) {
                failedPackages.add(0, packageName);
                failures.add(packageName + ": " + message(failure));
            }
        }
        ledger.activeOrder.clear();
        ledger.activeOrder.addAll(failedPackages);
        if (failedPackages.isEmpty()) {
            ledger.phase = DebloatLedger.Phase.IDLE;
            for (String packageName : originalOrder) {
                ledger.entry(packageName).sequence = 0L;
            }
        } else {
            Set<String> failed = new HashSet<>(failedPackages);
            for (String packageName : originalOrder) {
                if (!failed.contains(packageName)) {
                    ledger.entry(packageName).sequence = 0L;
                }
            }
        }
        try {
            store.save(ledger);
        } catch (Throwable persistenceFailure) {
            failures.add("finalize journal: " + message(persistenceFailure));
        }
        return failures.isEmpty() ? null
                : new IllegalStateException(String.join("; ", failures));
    }

    private void requireState(String packageName, PackageEnabledState expected) {
        PackageEnabledState actual = states.stateForUserZero(packageName);
        if (actual != expected) {
            throw new IllegalStateException("state verification failed for " + packageName
                    + ": expected=" + expected.value + ", actual="
                    + (actual == null ? "absent" : actual.value));
        }
    }

    private void runMutation(String command, String description) {
        shell.run(command).requireSuccess(description);
    }

    private static boolean hasPending(DebloatLedger ledger) {
        for (DebloatLedger.Entry entry : ledger.entries.values()) {
            if (entry.pending) return true;
        }
        return false;
    }

    private static DebloatResult emptyResult() {
        return new DebloatResult(Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
    }

    private static RuntimeException rethrow(String description, Throwable failure) {
        if (failure instanceof SecurityException) return (SecurityException) failure;
        return new IllegalStateException(description + ": " + message(failure), failure);
    }

    private static String message(Throwable failure) {
        return failure.getMessage() == null
                ? failure.getClass().getSimpleName() : failure.getMessage();
    }
}
