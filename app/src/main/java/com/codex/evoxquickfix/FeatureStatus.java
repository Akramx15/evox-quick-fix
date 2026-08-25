package com.codex.evoxquickfix;

enum FeatureStatus {
    READY(R.string.status_ready),
    APPLIED(R.string.status_applied),
    REBOOT_REQUIRED(R.string.status_reboot_required),
    BLOCKED(R.string.status_blocked),
    FAILED(R.string.status_failed);

    final int labelRes;

    FeatureStatus(int labelRes) {
        this.labelRes = labelRes;
    }
}
