package com.codex.evoxquickfix;

import org.json.JSONArray;
import org.json.JSONObject;

/** Exact parsing for Vector's structured CLI responses. */
final class VectorStateParser {
    private VectorStateParser() {}

    static JSONArray successfulData(String output) {
        if (output == null || output.isBlank()) {
            return null;
        }
        try {
            JSONObject response = new JSONObject(output);
            if (!response.optBoolean("success", false)) {
                return null;
            }
            return response.optJSONArray("data");
        } catch (Exception ignored) {
            return null;
        }
    }

    static boolean moduleEnabled(JSONArray data, String packageName) {
        if (data == null || packageName == null) {
            return false;
        }
        for (int index = 0; index < data.length(); index++) {
            JSONObject module = data.optJSONObject(index);
            if (module != null && enabledModuleEntry(
                    module.optString("PACKAGE"), module.optString("STATUS"), packageName)) {
                return true;
            }
        }
        return false;
    }

    static boolean scopeContains(JSONArray data, String packageName, int userId) {
        if (data == null || packageName == null) {
            return false;
        }
        for (int index = 0; index < data.length(); index++) {
            JSONObject scope = data.optJSONObject(index);
            if (scope != null && scopeEntry(
                    scope.optString("APP_PACKAGE"), integerValue(scope.opt("USER_ID")),
                    packageName, userId)) {
                return true;
            }
        }
        return false;
    }

    static boolean hasAnyScope(JSONArray data) {
        if (data == null) {
            return false;
        }
        for (int index = 0; index < data.length(); index++) {
            JSONObject scope = data.optJSONObject(index);
            if (scope != null
                    && !scope.optString("APP_PACKAGE").isBlank()
                    && integerValue(scope.opt("USER_ID")) != Integer.MIN_VALUE) {
                return true;
            }
        }
        return false;
    }

    static boolean enabledModuleEntry(String entryPackage, String status,
                                      String expectedPackage) {
        return expectedPackage != null
                && expectedPackage.equals(entryPackage)
                && "enabled".equalsIgnoreCase(status);
    }

    static boolean scopeEntry(String entryPackage, int entryUserId,
                              String expectedPackage, int expectedUserId) {
        return expectedPackage != null
                && expectedPackage.equals(entryPackage)
                && expectedUserId == entryUserId;
    }

    private static int integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return Integer.MIN_VALUE;
        }
    }
}
