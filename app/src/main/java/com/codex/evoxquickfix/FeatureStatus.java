package com.codex.evoxquickfix;

enum FeatureStatus {
    READY("جاهز"),
    APPLIED("مفعّل"),
    REBOOT_REQUIRED("يحتاج إعادة تشغيل"),
    BLOCKED("محمي/غير متوافق"),
    FAILED("فشل");

    final String arabicLabel;

    FeatureStatus(String arabicLabel) {
        this.arabicLabel = arabicLabel;
    }
}
