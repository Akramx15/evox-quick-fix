package com.codex.evoxquickfix;

final class QuickSearchFolderPolicy {
    private static final int FLAG_GRANT_READ_URI_PERMISSION = 0x00000001;
    private static final int FLAG_GRANT_WRITE_URI_PERMISSION = 0x00000002;
    private static final int FLAG_GRANT_PERSISTABLE_URI_PERMISSION = 0x00000040;
    private static final int FLAG_GRANT_PREFIX_URI_PERMISSION = 0x00000080;
    private static final int FLAG_ACTIVITY_NEW_TASK = 0x10000000;
    private static final int URI_GRANT_FLAGS = FLAG_GRANT_READ_URI_PERMISSION
            | FLAG_GRANT_WRITE_URI_PERMISSION
            | FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            | FLAG_GRANT_PREFIX_URI_PERMISSION;

    private QuickSearchFolderPolicy() {}

    static boolean shouldRoute(String action, String mimeType, String scheme,
                               String authority, String path, int flags,
                               boolean implicitTarget) {
        return "android.intent.action.VIEW".equals(action)
                && AppConstants.DIRECTORY_MIME.equals(mimeType)
                && "content".equals(scheme)
                && AppConstants.EXTERNAL_STORAGE_DOCUMENTS_AUTHORITY.equals(authority)
                && path != null
                && path.startsWith("/document/")
                && implicitTarget
                && (flags & URI_GRANT_FLAGS) == FLAG_GRANT_READ_URI_PERMISSION
                && (flags & FLAG_ACTIVITY_NEW_TASK) != 0;
    }
}
