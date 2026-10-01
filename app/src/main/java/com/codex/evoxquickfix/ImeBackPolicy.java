package com.codex.evoxquickfix;

import java.util.concurrent.atomic.AtomicBoolean;

/** Pure policy for the HeliBoard Back pass-through hook. */
final class ImeBackPolicy {
    static final String HELIBOARD_PACKAGE = AppConstants.HELIBOARD_PACKAGE;

    private ImeBackPolicy() {}

    static boolean supportsPackage(String packageName) {
        return HELIBOARD_PACKAGE.equals(packageName);
    }

    static boolean shouldPassToApp(boolean isBackKey, boolean inputViewShown) {
        return isBackKey && inputViewShown;
    }

    static boolean reportedHandled(boolean passToApp, boolean imeHandled) {
        return passToApp ? false : imeHandled;
    }

    interface EventCompletion {
        void finishedEvent(int sequenceNumber, boolean handled);
    }

    /** Relays the first completion as unhandled and drops duplicate IME completions. */
    static final class CallbackDelivery {
        private final AtomicBoolean delivered = new AtomicBoolean(false);

        void finishOnce(int sequenceNumber, boolean imeHandled, EventCompletion original) {
            if (delivered.compareAndSet(false, true)) {
                original.finishedEvent(
                        sequenceNumber,
                        reportedHandled(true, imeHandled));
            }
        }
    }
}
