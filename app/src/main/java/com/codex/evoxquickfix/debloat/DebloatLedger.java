package com.codex.evoxquickfix.debloat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DebloatLedger {
    static final int SCHEMA = 1;

    enum Phase {
        IDLE,
        APPLYING,
        ROLLING_BACK
    }

    int schema = SCHEMA;
    long revision;
    String profileHash;
    Phase phase = Phase.IDLE;
    long nextSequence = 1L;
    final List<String> activeOrder = new ArrayList<>();
    final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();

    DebloatLedger(String profileHash) {
        this.profileHash = requireProfileHash(profileHash);
    }

    Entry entry(String packageName) {
        return entries.get(packageName);
    }

    Entry createEntry(String packageName, PackageEnabledState baseline) {
        PackageId.requireValid(packageName);
        if (entries.containsKey(packageName)) {
            throw new IllegalStateException("duplicate ledger package: " + packageName);
        }
        Entry entry = new Entry(packageName, baseline);
        entries.put(packageName, entry);
        return entry;
    }

    boolean hasOwnership() {
        if (phase != Phase.IDLE || !activeOrder.isEmpty()) {
            return true;
        }
        for (Entry entry : entries.values()) {
            if (entry.managed || entry.pending) {
                return true;
            }
        }
        return false;
    }

    void validate() {
        if (schema != SCHEMA) {
            throw new IllegalStateException("unsupported debloat ledger schema: " + schema);
        }
        requireProfileHash(profileHash);
        if (revision < 0L || nextSequence < 1L || phase == null) {
            throw new IllegalStateException("invalid debloat ledger counters");
        }
        for (Map.Entry<String, Entry> item : entries.entrySet()) {
            if (!item.getKey().equals(item.getValue().packageName)) {
                throw new IllegalStateException("ledger package key mismatch");
            }
            item.getValue().validate();
            if (item.getValue().sequence >= nextSequence) {
                throw new IllegalStateException("ledger sequence is not monotonic");
            }
        }
        java.util.HashSet<String> active = new java.util.HashSet<>();
        for (String packageName : activeOrder) {
            PackageId.requireValid(packageName);
            if (!entries.containsKey(packageName) || !active.add(packageName)) {
                throw new IllegalStateException("invalid active package: " + packageName);
            }
        }
        if (phase == Phase.IDLE && !activeOrder.isEmpty()) {
            throw new IllegalStateException("idle ledger has an active transaction");
        }
        if (phase != Phase.IDLE && activeOrder.isEmpty()) {
            throw new IllegalStateException("active ledger has no package journal");
        }
    }

    static String requireProfileHash(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("profileHash must be lowercase SHA-256");
        }
        return value;
    }

    static final class Entry {
        final String packageName;
        PackageEnabledState baselineState;
        boolean managed;
        boolean pending;
        long sequence;

        Entry(String packageName, PackageEnabledState baselineState) {
            this.packageName = PackageId.requireValid(packageName);
            this.baselineState = java.util.Objects.requireNonNull(baselineState);
        }

        void validate() {
            PackageId.requireValid(packageName);
            if (baselineState == null || sequence < 0L) {
                throw new IllegalStateException("invalid ledger entry for " + packageName);
            }
            if ((managed || pending) && sequence <= 0L) {
                throw new IllegalStateException("owned ledger entry lacks sequence: " + packageName);
            }
        }
    }
}
