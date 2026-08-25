package com.codex.evoxquickfix;

import android.content.Context;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class DebloatProfileLoader {
    private DebloatProfileLoader() {}

    static DebloatProfile load(Context context) throws Exception {
        if (context == null) {
            throw new IllegalArgumentException("Context is required");
        }
        try (InputStream input = context.getAssets().open(DebloatProfile.ASSET_NAME)) {
            return DebloatProfile.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
