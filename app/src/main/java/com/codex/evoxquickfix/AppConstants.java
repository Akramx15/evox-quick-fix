package com.codex.evoxquickfix;

final class AppConstants {
    static final String APP_PACKAGE = "com.codex.evoxquickfix";
    static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    static final String QUICK_SEARCH_PACKAGE = "com.tk.quicksearch";
    static final String QUICK_SEARCH_HOME = "com.tk.quicksearch.app.HomeActivity";
    static final long QUICK_SEARCH_VERSION = 78L;
    static final String QUICK_SEARCH_APK_SHA256 =
            "673da366c7b3eaeca8f959bc80b757bb428a500597bb54f51c2ecd6a46567ff2";
    static final String QUICK_SEARCH_CERT_SHA256 =
            "2149d96b24b9f51467893c9bbc859d6c1833607e0a326e431af8e023ae075b8a";
    static final String DOCUMENTS_UI_PACKAGE = "com.android.documentsui";
    static final String DOCUMENTS_UI_FILES_ACTIVITY =
            "com.android.documentsui.files.FilesActivity";
    static final String EXTERNAL_STORAGE_DOCUMENTS_AUTHORITY =
            "com.android.externalstorage.documents";
    static final String DIRECTORY_MIME = "vnd.android.document/directory";

    static final String LAUNCHER_PACKAGE = "com.android.launcher3";
    static final String GOOGLE_PACKAGE = "com.google.android.googlequicksearchbox";
    static final String CONTEXTUAL_ACTION =
            "android.app.contextualsearch.action.LAUNCH_CONTEXTUAL_SEARCH";
    static final String CONTEXTUAL_FEATURE = "com.google.android.feature.CONTEXTUAL_SEARCH";

    static final String DARK_OVERLAY = "com.android.shell:CodexTransparentOverviewDark";
    static final String LIGHT_OVERLAY = "com.android.shell:CodexTransparentOverviewLight";
    static final String DARK_OVERLAY_NAME = "CodexTransparentOverviewDark";
    static final String LIGHT_OVERLAY_NAME = "CodexTransparentOverviewLight";
    static final String DARK_RESOURCE = "com.android.launcher3:color/overview_scrim_dark";
    static final String LIGHT_RESOURCE = "com.android.launcher3:color/overview_scrim";

    static final String VECTOR_CLI = "/data/adb/lspd/cli";
    static final String KSU_CLI = "ksud";
    static final String CIRCLE_MODULE_ID = "evox_contextual_search_fix";
    static final String CIRCLE_MODULE_DIR = "/data/adb/modules/" + CIRCLE_MODULE_ID;
    static final String CIRCLE_MODULE_UPDATE_DIR =
            "/data/adb/modules_update/" + CIRCLE_MODULE_ID;
    static final String CIRCLE_OWNER_MARKER = ".evox-quick-fix-owner";
    static final String CIRCLE_OWNER_VALUE =
            "com.codex.evoxquickfix:evox_contextual_search_fix:v1";
    static final String CIRCLE_OWNER_SHA256 =
            "bb67e276fbf2ce6890832afebb24d95c974bf84597498bb94114249a19706977";
    static final String CIRCLE_MODULE_PROP_SHA256 =
            "ac825bc4ce16a7f4efe9401e0f70b38472621cc553cfa447119e674737fb70aa";
    static final String CIRCLE_XML_RELATIVE =
            "/system/etc/permissions/evox_contextual_search.xml";

    static final String OVERVIEW_MODULE_ID = "evox_overview_transparency";
    static final String OVERVIEW_MODULE_DIR = "/data/adb/modules/" + OVERVIEW_MODULE_ID;
    static final String OVERVIEW_MODULE_UPDATE_DIR =
            "/data/adb/modules_update/" + OVERVIEW_MODULE_ID;
    static final String OVERVIEW_OWNER_MARKER = ".evox-quick-fix-owner";
    static final String OVERVIEW_OWNER_VALUE =
            "com.codex.evoxquickfix:evox_overview_transparency:v1";
    static final String OVERVIEW_OWNER_SHA256 =
            "af5ea92e15b215dbed52323f7256c59b086dec98acdfd0bcbce8b19623252540";
    static final String OVERVIEW_MODULE_PROP_SHA256 =
            "293775185445d59b5a46ea0a1aff2f2d4ca9385dc77ac7e705171fe06cccd981";
    static final String OVERVIEW_BOOT_SCRIPT = "/boot-completed.sh";
    static final String OVERVIEW_BOOT_SCRIPT_SHA256 =
            "e064328344644304dee9d13711941d7a2dbebd692ab0bd653d61a6c92c48983c";

    static final String ROOT_STATE_DIR = "/data/adb/evox-quick-fix";
    static final String ROOT_SNAPSHOT = ROOT_STATE_DIR + "/snapshot.json";

    private AppConstants() {}
}
