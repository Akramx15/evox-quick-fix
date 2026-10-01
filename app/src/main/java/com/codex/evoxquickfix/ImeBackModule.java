package com.codex.evoxquickfix;

import android.annotation.SuppressLint;
import android.inputmethodservice.InputMethodService;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.InputMethodSession;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;

/**
 * HeliBoard-only Back pass-through. Hooks are installed inside the IME process, never in
 * system_server, and every discovery/runtime failure keeps Android's original behavior.
 */
public final class ImeBackModule extends XposedModule {
    private static final String TAG = "EvoXQuickFix/OneBack";
    private static final String SESSION_CLASS =
            "android.inputmethodservice.AbstractInputMethodService$AbstractInputMethodSessionImpl";

    private final AtomicBoolean installationAttempted = new AtomicBoolean(false);
    private final Set<String> reportedErrors = ConcurrentHashMap.newKeySet();

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        log(Log.INFO, TAG, "IME Back module loaded in " + param.getProcessName());
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        if (!param.isFirstPackage()
                || !ImeBackPolicy.supportsPackage(param.getPackageName())
                || !installationAttempted.compareAndSet(false, true)) {
            return;
        }

        log(Log.INFO, TAG, "Installing HeliBoard Back pass-through for "
                + param.getPackageName());
        installLegacyBackPassThrough(param.getDefaultClassLoader());
        // minSdk is Android 16, so predictive Back is always part of this build.
        installPredictiveRegistrationSuppression();
        installShowWindowCleanup();
    }

    @SuppressLint("BlockedPrivateApi") // Deliberate framework hook inside HeliBoard via libxposed.
    private void installLegacyBackPassThrough(ClassLoader classLoader) {
        try {
            Class<?> sessionClass = Class.forName(SESSION_CLASS, false, classLoader);
            Method dispatchKeyEvent = sessionClass.getDeclaredMethod(
                    "dispatchKeyEvent",
                    int.class,
                    KeyEvent.class,
                    InputMethodSession.EventCallback.class);
            Field ownerField = sessionClass.getDeclaredField("this$0");
            dispatchKeyEvent.setAccessible(true);
            ownerField.setAccessible(true);

            hook(dispatchKeyEvent)
                    .setPriority(PRIORITY_HIGHEST)
                    .intercept(chain -> {
                        final Object eventValue;
                        final Object callbackValue;
                        try {
                            eventValue = chain.getArg(1);
                            callbackValue = chain.getArg(2);
                        } catch (Throwable failure) {
                            logErrorOnce("legacy-arguments", failure);
                            return chain.proceed();
                        }
                        if (!(eventValue instanceof KeyEvent event)
                                || !(callbackValue
                                        instanceof InputMethodSession.EventCallback callback)) {
                            return chain.proceed();
                        }

                        final InputMethodService service;
                        final boolean inputViewShown;
                        try {
                            Object owner = ownerField.get(chain.getThisObject());
                            if (!(owner instanceof InputMethodService inputMethodService)) {
                                return chain.proceed();
                            }
                            service = inputMethodService;
                            inputViewShown = service.isInputViewShown();
                        } catch (Throwable failure) {
                            logErrorOnce("legacy-state", failure);
                            return chain.proceed();
                        }

                        boolean passToApp = ImeBackPolicy.shouldPassToApp(
                                event.getKeyCode() == KeyEvent.KEYCODE_BACK,
                                inputViewShown);
                        if (!passToApp) {
                            return chain.proceed();
                        }

                        final Object[] replacementArgs;
                        try {
                            ImeBackPolicy.CallbackDelivery delivery =
                                    new ImeBackPolicy.CallbackDelivery();
                            ImeBackPolicy.EventCompletion originalCompletion =
                                    callback::finishedEvent;
                            InputMethodSession.EventCallback wrappedCallback =
                                    (sequenceNumber, imeHandled) -> delivery.finishOnce(
                                            sequenceNumber,
                                            imeHandled,
                                            originalCompletion);
                            List<?> originalArgs = chain.getArgs();
                            replacementArgs = originalArgs.toArray();
                            replacementArgs[2] = wrappedCallback;
                        } catch (Throwable failure) {
                            logErrorOnce("legacy-callback", failure);
                            return chain.proceed();
                        }
                        return chain.proceed(replacementArgs);
                    });
            log(Log.INFO, TAG, "Installed legacy KEYCODE_BACK pass-through");
        } catch (Throwable failure) {
            logErrorOnce("install-legacy", failure);
        }
    }

    private void installPredictiveRegistrationSuppression() {
        try {
            Method registration = firstDeclaredNoArgMethod(
                    InputMethodService.class,
                    "registerDefaultOnBackInvokedCallback",
                    "registerCompatOnBackInvokedCallback");
            if (registration == null) {
                log(Log.WARN, TAG,
                        "Predictive Back registration method is absent; legacy hook stays active");
                return;
            }
            registration.setAccessible(true);
            hook(registration)
                    .setPriority(PRIORITY_HIGHEST)
                    .intercept(chain -> {
                        final Object receiver;
                        try {
                            receiver = chain.getThisObject();
                        } catch (Throwable failure) {
                            logErrorOnce("predictive-registration-state", failure);
                            return chain.proceed();
                        }
                        if (!(receiver instanceof InputMethodService)) {
                            return chain.proceed();
                        }
                        // Do not register the framework callback that consumes Back to hide the IME.
                        return null;
                    });
            log(Log.INFO, TAG, "Disabled InputMethodService."
                    + registration.getName() + "()");
        } catch (Throwable failure) {
            logErrorOnce("install-predictive-registration", failure);
        }
    }

    private void installShowWindowCleanup() {
        try {
            Method unregistration = firstDeclaredNoArgMethod(
                    InputMethodService.class,
                    "unregisterDefaultOnBackInvokedCallback",
                    "unregisterCompatOnBackInvokedCallback");
            if (unregistration == null) {
                log(Log.WARN, TAG,
                        "Predictive Back unregistration method is absent; cleanup is unavailable");
                return;
            }
            Method showWindow = InputMethodService.class.getDeclaredMethod(
                    "showWindow", boolean.class);
            unregistration.setAccessible(true);
            showWindow.setAccessible(true);

            hook(showWindow)
                    .setPriority(PRIORITY_HIGHEST)
                    .intercept(chain -> {
                        try {
                            return chain.proceed();
                        } finally {
                            try {
                                Object receiver = chain.getThisObject();
                                if (receiver instanceof InputMethodService) {
                                    // Invoke the private superclass method directly. A name lookup on
                                    // HeliBoard's subclass cannot see it.
                                    unregistration.invoke(receiver);
                                }
                            } catch (Throwable failure) {
                                logErrorOnce("show-window-cleanup", failure);
                            }
                        }
                    });
            log(Log.INFO, TAG, "Installed showWindow() predictive Back cleanup");
        } catch (Throwable failure) {
            logErrorOnce("install-show-window-cleanup", failure);
        }
    }

    private static Method firstDeclaredNoArgMethod(Class<?> type, String... candidates) {
        for (String candidate : candidates) {
            try {
                return type.getDeclaredMethod(candidate);
            } catch (NoSuchMethodException ignored) {
                // Android releases use one of the candidate names.
            }
        }
        return null;
    }

    private void logErrorOnce(String operation, Throwable failure) {
        if (reportedErrors.add(operation)) {
            log(Log.ERROR, TAG, operation + " failed open", failure);
        }
    }
}
