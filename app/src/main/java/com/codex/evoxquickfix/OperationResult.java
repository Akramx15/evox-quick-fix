package com.codex.evoxquickfix;

final class OperationResult {
    final FeatureStatus status;
    final String message;

    OperationResult(FeatureStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    static OperationResult applied(String message) {
        return new OperationResult(FeatureStatus.APPLIED, message);
    }

    static OperationResult reboot(String message) {
        return new OperationResult(FeatureStatus.REBOOT_REQUIRED, message);
    }
}
