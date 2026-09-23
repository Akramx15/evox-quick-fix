package com.codex.evoxquickfix;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Independent feature: neither core Apply All nor core restore calls this controller. */
final class LeAudioManager {
    private final Context context;

    LeAudioManager(Context context) {
        this.context = context.getApplicationContext();
    }

    LeAudioStatus inspect(DiagnosticReport report) {
        if (!LeAudioPolicy.deviceAllowed(report)) return LeAudioStatus.unavailable();
        return LeAudioStatus.parse(RootShell.run(LeAudioPolicy.inspectScript()));
    }

    OperationResult apply() throws Exception {
        try (OperationCoordinator.Lease ignored = OperationCoordinator.acquire("LE Audio music")) {
            DiagnosticReport report = new DeviceDiagnostics(context).inspect();
            if (report.leAudio.state() == LeAudioStatus.State.ACTIVE) {
                return OperationResult.applied(context.getString(R.string.le_audio_active));
            }
            if (!report.leAudio.canApply()) throw blocked();
            File zip = stageZip();
            try {
                String digest = DeviceDiagnostics.sha256File(zip.getAbsolutePath());
                RootShell.run(applyScript(zip.getAbsolutePath(), digest), 90L)
                        .requireSuccess(context.getString(R.string.action_le_audio));
            } finally {
                // Only the private temporary archive; never deletes a device module.
                if (!zip.delete()) zip.deleteOnExit();
            }
            LeAudioStatus verified = inspect(report);
            if (verified.state() != LeAudioStatus.State.PENDING
                    && verified.state() != LeAudioStatus.State.ACTIVE
                    && verified.state() != LeAudioStatus.State.BOOT_FAILED) throw blocked();
            return OperationResult.reboot(context.getString(R.string.le_audio_pending));
        }
    }

    OperationResult disable() {
        try (OperationCoordinator.Lease ignored = OperationCoordinator.acquire("disable LE Audio")) {
            DiagnosticReport report = new DeviceDiagnostics(context).inspect();
            if (!report.leAudio.canDisable()) throw blocked();
            RootShell.run(disableScript()).requireSuccess(
                    context.getString(R.string.action_le_audio_disable));
            // Disable markers are authoritative even if a later ROM no longer matches.
            CommandResult verified = RootShell.run(LeAudioPolicy.preflight()
                    + "[ \"$present\" = 1 ] && [ \"$disabled\" = 1 ]\n");
            verified.requireSuccess(context.getString(R.string.action_le_audio_disable));
            return OperationResult.reboot(context.getString(R.string.le_audio_disable_pending));
        }
    }

    private IllegalStateException blocked() {
        return new IllegalStateException(context.getString(R.string.le_audio_blocked_operation));
    }

    static String applyScript(String sourceZip, String digest) {
        if (!digest.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("zip digest");
        return LeAudioPolicy.preflight() + LeAudioPolicy.romChecks() + """
                [ "$rom_ok" = 1 ] || fail rom_changed
                [ "$removed" = 0 ] || fail removal_pending
                if [ "$present" = 1 ]; then
                    # Exact standalone v1.1 is adopted without rewriting any payload or metadata.
                    ksud module enable s23_le_audio_fix || fail enable
                    for directory in "$current" "$update"; do
                        [ ! -d "$directory" ] || rm -f "$directory/disable" || fail enable_flag
                    done
                    exit 0
                fi
                state=/data/adb/evox-quick-fix
                mkdir -p "$state" && chown 0:0 "$state" && chmod 700 "$state" || fail staging_parent
                staging=$(mktemp -d "$state/le-audio.XXXXXXXX") || fail staging
                trap 'rm -f "$staging/module.zip"; rmdir "$staging"' EXIT
                """ + "cp " + ShellEscaper.quote(sourceZip) + " \"$staging/module.zip\" || fail copy\n"
                + "hash_is \"$staging/module.zip\" " + digest + " || fail archive_hash\n"
                + """
                chown 0:0 "$staging/module.zip" && chmod 600 "$staging/module.zip" || fail archive_permissions
                # Recheck absence immediately before handing the archive to KernelSU.
                for directory in "$current" "$update"; do
                    [ ! -e "$directory" ] && [ ! -L "$directory" ] || fail appeared_during_install
                done
                install_result=0
                ksud module install "$staging/module.zip" || install_result=$?
                """ + finishFreshInstallScript();
    }

    static String finishFreshInstallScript() {
        return LeAudioPolicy.moduleState() + """
                [ "$present" = 1 ] || fail install_missing
                if [ "$install_result" != 0 ]; then
                    # A complete, verified payload can safely be disabled after installer failure.
                    for directory in "$current" "$update"; do
                        [ ! -d "$directory" ] || [ -f "$directory/disable" ] || (set -C; : > "$directory/disable") || fail disable_failed_install
                    done
                    fail install_failed_disabled
                fi
                [ "$removed" = 0 ] || fail install_removing
                # The archive starts disabled; enable only after the entire payload was verified.
                ksud module enable s23_le_audio_fix || fail install_enable
                for directory in "$current" "$update"; do
                    [ ! -d "$directory" ] || rm -f "$directory/disable" || fail install_enable_flag
                done
                """;
    }

    static String disableScript() {
        // Recovery intentionally does not require current ROM hashes; ownership still does.
        return LeAudioPolicy.preflight() + """
                [ "$present" = 1 ] || fail module_missing
                for directory in "$current" "$update"; do
                    [ -d "$directory" ] || continue
                    if [ ! -f "$directory/disable" ]; then
                        (umask 022; set -C; : > "$directory/disable") || fail disable_flag
                    fi
                done
                """;
    }

    private File stageZip() throws Exception {
        File output = File.createTempFile("le-audio-", ".zip", context.getCacheDir());
        try (FileOutputStream raw = new FileOutputStream(output);
             ZipOutputStream zip = new ZipOutputStream(raw)) {
            writeArchive(zip, path -> {
                try (InputStream asset = context.getAssets().open("le_audio_module/" + path)) {
                    return asset.readAllBytes();
                }
            });
            zip.finish();
            zip.flush();
            raw.getFD().sync();
        } catch (Exception failure) {
            if (!output.delete()) output.deleteOnExit();
            throw failure;
        }
        return output;
    }

    interface AssetReader {
        byte[] read(String relativePath) throws Exception;
    }

    static void writeArchive(ZipOutputStream zip, AssetReader reader) throws Exception {
        // First extraction entry: interrupted installation must not boot partial scripts.
        putEntry(zip, "disable", new byte[0]);
        for (Map.Entry<String, String> file : LeAudioPolicy.FILES.entrySet()) {
            byte[] bytes = reader.read(file.getKey());
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!file.getValue().equals(digest)) {
                throw new IllegalStateException("LE Audio asset checksum mismatch: " + file.getKey());
            }
            putEntry(zip, file.getKey(), bytes);
        }
        putEntry(zip, LeAudioPolicy.OWNER,
                (LeAudioPolicy.OWNER_VALUE + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private static void putEntry(ZipOutputStream zip, String path, byte[] bytes) throws Exception {
        ZipEntry entry = new ZipEntry(path);
        entry.setTime(0L);
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }
}
