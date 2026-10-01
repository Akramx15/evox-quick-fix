package com.codex.evoxquickfix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public final class ImeBackPolicyTest {
    @Test
    public void supportsOnlyHeliBoard() {
        assertTrue(ImeBackPolicy.supportsPackage("helium314.keyboard"));
        assertFalse(ImeBackPolicy.supportsPackage("com.google.android.inputmethod.latin"));
        assertFalse(ImeBackPolicy.supportsPackage("android"));
        assertFalse(ImeBackPolicy.supportsPackage(null));
    }

    @Test
    public void visibleKeyboardBackPassesToApp() {
        assertTrue(ImeBackPolicy.shouldPassToApp(true, true));
    }

    @Test
    public void nonBackOrHiddenKeyboardKeepsNormalDispatch() {
        assertFalse(ImeBackPolicy.shouldPassToApp(false, true));
        assertFalse(ImeBackPolicy.shouldPassToApp(true, false));
        assertFalse(ImeBackPolicy.shouldPassToApp(false, false));
    }

    @Test
    public void passThroughAlwaysReportsUnhandled() {
        assertFalse(ImeBackPolicy.reportedHandled(true, true));
        assertFalse(ImeBackPolicy.reportedHandled(true, false));
    }

    @Test
    public void normalDispatchPreservesImeResult() {
        assertTrue(ImeBackPolicy.reportedHandled(false, true));
        assertFalse(ImeBackPolicy.reportedHandled(false, false));
    }

    @Test
    public void callbackDeliveryInvokesOriginalOnceAsUnhandled() {
        ImeBackPolicy.CallbackDelivery delivery =
                new ImeBackPolicy.CallbackDelivery();
        AtomicInteger callCount = new AtomicInteger();
        AtomicInteger deliveredSequence = new AtomicInteger();
        AtomicBoolean deliveredHandled = new AtomicBoolean(true);
        ImeBackPolicy.EventCompletion original = (sequenceNumber, handled) -> {
            callCount.incrementAndGet();
            deliveredSequence.set(sequenceNumber);
            deliveredHandled.set(handled);
        };

        delivery.finishOnce(41, true, original);
        delivery.finishOnce(42, false, original);

        assertEquals(1, callCount.get());
        assertEquals(41, deliveredSequence.get());
        assertFalse(deliveredHandled.get());
    }
}
