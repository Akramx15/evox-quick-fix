package com.codex.evoxquickfix;

import java.util.List;

final class BackGuardPolicy {
    private BackGuardPolicy() {}

    static boolean shouldBlock(String activityClass, List<?> arguments, boolean homeRoleHeld) {
        return homeRoleHeld
                && AppConstants.QUICK_SEARCH_HOME.equals(activityClass)
                && arguments != null
                && !arguments.isEmpty()
                && Boolean.TRUE.equals(arguments.get(0));
    }
}
