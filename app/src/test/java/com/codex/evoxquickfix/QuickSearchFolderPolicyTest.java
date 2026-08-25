package com.codex.evoxquickfix;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class QuickSearchFolderPolicyTest {
    @Test
    public void routesOnlyExternalStorageDocumentDirectories() {
        assertTrue(QuickSearchFolderPolicy.shouldRoute(
                "android.intent.action.VIEW",
                "vnd.android.document/directory",
                "content",
                "com.android.externalstorage.documents",
                "/document/primary:Download",
                0x10000001,
                true));
    }

    @Test
    public void rejectsFilesOtherProvidersAndOtherActions() {
        assertFalse(QuickSearchFolderPolicy.shouldRoute(
                "android.intent.action.VIEW",
                "application/vnd.android.package-archive",
                "content",
                "com.android.externalstorage.documents",
                "/document/primary:Download/app.apk",
                0x10000001,
                true));
        assertFalse(QuickSearchFolderPolicy.shouldRoute(
                "android.intent.action.VIEW",
                "vnd.android.document/directory",
                "content",
                "com.example.provider",
                "/document/primary:Download",
                0x10000001,
                true));
        assertFalse(QuickSearchFolderPolicy.shouldRoute(
                "android.intent.action.SEND",
                "vnd.android.document/directory",
                "content",
                "com.android.externalstorage.documents",
                "/document/primary:Download",
                0x10000001,
                true));
        assertFalse(QuickSearchFolderPolicy.shouldRoute(
                "android.intent.action.VIEW",
                "vnd.android.document/directory",
                "file",
                "com.android.externalstorage.documents",
                "/document/primary:Download",
                0x10000001,
                true));
        assertFalse(QuickSearchFolderPolicy.shouldRoute(
                "android.intent.action.VIEW",
                "vnd.android.document/directory",
                "content",
                "com.android.externalstorage.documents",
                "/document/primary:Download",
                0x10000000,
                true));
        assertFalse(QuickSearchFolderPolicy.shouldRoute(
                "android.intent.action.VIEW",
                "vnd.android.document/directory",
                "content",
                "com.android.externalstorage.documents",
                "/document/primary:Download",
                0x00000001,
                true));
        assertFalse(QuickSearchFolderPolicy.shouldRoute(
                "android.intent.action.VIEW",
                "vnd.android.document/directory",
                "content",
                "com.android.externalstorage.documents",
                null,
                0x10000001,
                true));
        assertFalse(QuickSearchFolderPolicy.shouldRoute(
                "android.intent.action.VIEW",
                "vnd.android.document/directory",
                "content",
                "com.android.externalstorage.documents",
                "/document/primary:Download",
                0x10000001,
                false));
        assertFalse(QuickSearchFolderPolicy.shouldRoute(
                "android.intent.action.VIEW",
                "vnd.android.document/directory",
                "content",
                "com.android.externalstorage.documents",
                "/document/primary:Download",
                0x10000003,
                true));
        assertFalse(QuickSearchFolderPolicy.shouldRoute(
                "android.intent.action.VIEW",
                "vnd.android.document/directory",
                "content",
                "com.android.externalstorage.documents",
                "/tree/primary:Download",
                0x10000001,
                true));
    }
}
