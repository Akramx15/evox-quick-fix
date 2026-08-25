package com.codex.evoxquickfix;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FeatureStatusTest {
    @Test
    public void hasStableArabicLabels() {
        assertEquals("جاهز", FeatureStatus.READY.arabicLabel);
        assertEquals("يحتاج إعادة تشغيل", FeatureStatus.REBOOT_REQUIRED.arabicLabel);
        assertEquals("محمي/غير متوافق", FeatureStatus.BLOCKED.arabicLabel);
    }
}
