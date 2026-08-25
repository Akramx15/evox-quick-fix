package com.codex.evoxquickfix;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Process-wide mutation lock shared by independent feature controllers.
 *
 * <p>The lock is reentrant so an activity can cover its preflight/health snapshot and then call a
 * backend which acquires the same lock. Callers must keep the returned lease in a try-with-resources
 * block.</p>
 */
public final class OperationCoordinator {
    private static final ReentrantLock MUTATION_LOCK = new ReentrantLock();

    private OperationCoordinator() {}

    public static Lease acquire(String operation) {
        String label = operation == null || operation.isBlank() ? "operation" : operation;
        if (!MUTATION_LOCK.tryLock()) {
            throw new IllegalStateException(
                    "another EvoX Quick Fix mutation is already running: " + label);
        }
        return new Lease();
    }

    public static final class Lease implements AutoCloseable {
        private boolean closed;

        private Lease() {}

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                MUTATION_LOCK.unlock();
            }
        }
    }
}
