package com.codex.evoxquickfix;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class OperationCoordinatorTest {
    @Test
    public void lockIsReentrantForActivityAndBackendOnSameThread() {
        try (OperationCoordinator.Lease outer = OperationCoordinator.acquire("activity");
             OperationCoordinator.Lease inner = OperationCoordinator.acquire("backend")) {
            assertTrue(outer != inner);
        }
    }

    @Test
    @SuppressWarnings("try")
    public void concurrentMutationFailsClosed() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (OperationCoordinator.Lease ignored = OperationCoordinator.acquire("first")) {
            try {
                worker.submit(() -> {
                    try (OperationCoordinator.Lease unexpected =
                                 OperationCoordinator.acquire("second")) {
                        return unexpected;
                    }
                }).get();
                fail("concurrent operation unexpectedly acquired the mutation lock");
            } catch (ExecutionException expected) {
                assertTrue(expected.getCause() instanceof IllegalStateException);
            }
        } finally {
            worker.shutdownNow();
        }
    }
}
