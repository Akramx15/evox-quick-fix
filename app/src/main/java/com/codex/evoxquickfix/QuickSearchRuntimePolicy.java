package com.codex.evoxquickfix;

final class QuickSearchRuntimePolicy {
    private static final String SUPPORTED_MODEL = "SM-S918B";
    private static final int SUPPORTED_SDK = 36;
    private static final int PER_USER_RANGE = 100_000;

    private QuickSearchRuntimePolicy() {}

    static boolean isSupported(String model, int sdk, int uid) {
        return SUPPORTED_MODEL.equalsIgnoreCase(model)
                && sdk == SUPPORTED_SDK
                && uid >= 0
                && uid / PER_USER_RANGE == 0;
    }
}
