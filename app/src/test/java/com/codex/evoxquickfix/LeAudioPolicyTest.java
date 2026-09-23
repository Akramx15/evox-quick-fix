package com.codex.evoxquickfix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class LeAudioPolicyTest {
    private static final Path ASSETS = Path.of("src/main/assets/le_audio_module");
    private static final Path LEGACY = Path.of("src/test/resources/le_audio_v1_1");
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void allShippedBytesAndManifestTargetsArePinned() throws Exception {
        try (var paths = Files.walk(ASSETS)) {
            assertEquals(17, paths.filter(Files::isRegularFile).count());
        }
        assertEquals(17, LeAudioPolicy.FILES.size());
        for (Map.Entry<String, String> entry : LeAudioPolicy.FILES.entrySet()) {
            assertEquals(entry.getKey(), entry.getValue(), HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(Files.readAllBytes(ASSETS.resolve(entry.getKey())))));
        }
        String[] rows = readText(ASSETS.resolve("manifest.txt")).lines()
                .filter(line -> !line.startsWith("#") && !line.isBlank()).toArray(String[]::new);
        assertEquals(3, rows.length);
        for (int index = 0; index < rows.length; index++) {
            String[] columns = rows[index].split("\\|");
            assertEquals(LeAudioPolicy.ROM[index][0], columns[4]);
            assertEquals(LeAudioPolicy.ROM[index][1], columns[1]);
            assertEquals(LeAudioPolicy.ROM[index][2], columns[2]);
            assertEquals(columns[2], LeAudioPolicy.FILES.get(columns[3]));
        }
        assertEquals("duplex\n", readText(ASSETS.resolve("mode.txt")));
    }

    @Test public void exactStandaloneIsAdoptableWithOnlyKnownRuntimeFiles() throws Exception {
        Path module = copyModule();
        assertEquals(0, verify(module));
        writeText(module.resolve("boot.log"), "runtime log\n");
        writeText(module.resolve("status.txt"), "boot=example\nstate=ready\n");
        Files.createFile(module.resolve("disable"));
        assertEquals(0, verify(module));
        writeText(module.resolve(LeAudioPolicy.OWNER), LeAudioPolicy.OWNER_VALUE + "\n");
        assertEquals(0, verify(module));
        writeText(module.resolve(LeAudioPolicy.OWNER), "other-owner\n");
        assertFalse(verify(module) == 0);
    }

    @Test public void markerNeverAllowsModifiedOrMissingRequiredStaticFiles() throws Exception {
        Path module = copyModule();
        writeText(module.resolve(LeAudioPolicy.OWNER), LeAudioPolicy.OWNER_VALUE + "\n");
        for (String file : LeAudioPolicy.FILES.keySet()) {
            Path target = module.resolve(file);
            byte[] bytes = Files.readAllBytes(target);
            writeText(target, "changed\n");
            assertFalse(file, verify(module) == 0);
            Files.delete(target);
            if (!LeAudioPolicy.INSTALLER_REMOVED_FILE.equals(file)) {
                assertFalse(file, verify(module) == 0);
            }
            Files.write(target, bytes);
        }
        assertEquals(0, verify(module));
    }

    @Test public void kernelSuMayRemoveOnlyItsVerifiedInstallHook() throws Exception {
        Path module = copyModule();
        Files.delete(module.resolve("customize.sh"));
        assertEquals(0, verify(module));
        writeText(module.resolve("customize.sh"), "foreign installer\n");
        assertFalse(verify(module) == 0);
        Files.delete(module.resolve("customize.sh"));
        Files.delete(module.resolve("post-fs-data.sh"));
        assertFalse(verify(module) == 0);
    }

    @Test public void onlyExactHelperGeneratedMusicManifestIsAlsoRecognized() throws Exception {
        Path module = copyModule(true);
        Path manifest = module.resolve("manifest.txt");
        String canonical = readText(manifest);
        String music = canonical.replace(
                "|files/bluetooth_policy.xml|/vendor/etc/bluetooth_audio_policy_configuration.xml|",
                "|profiles/music.xml|/vendor/etc/bluetooth_audio_policy_configuration.xml|");
        assertFalse(canonical.equals(music));
        assertEquals(LeAudioPolicy.HELPER_MUSIC_MANIFEST_SHA256, HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(music.getBytes(
                        java.nio.charset.StandardCharsets.UTF_8))));
        writeText(manifest, music);
        assertEquals(0, verify(module));
        writeText(module.resolve("mode.txt"), "duplex\n");
        assertFalse(verify(module) == 0);
        writeText(module.resolve("mode.txt"), "music\n");
        writeText(manifest, music.replace("profiles/music.xml", "profiles/duplex.xml"));
        assertFalse(verify(module) == 0);
        writeText(manifest, music + "# external change\n");
        assertFalse(verify(module) == 0);
        writeText(manifest, canonical);
        assertEquals(0, verify(module));
    }

    @Test public void bothVersionsRequireCompleteBundlesAndMatchingOwners() throws Exception {
        Path legacy = copyModule(true);
        Path current = copyModule();
        assertEquals(0, verify(legacy));
        writeText(legacy.resolve(LeAudioPolicy.OWNER), LeAudioPolicy.LEGACY_OWNER_VALUE + "\n");
        assertEquals(0, verify(legacy));
        writeText(legacy.resolve(LeAudioPolicy.OWNER), LeAudioPolicy.OWNER_VALUE + "\n");
        assertFalse(verify(legacy) == 0);
        Files.delete(legacy.resolve(LeAudioPolicy.OWNER));
        writeText(current.resolve(LeAudioPolicy.OWNER), LeAudioPolicy.LEGACY_OWNER_VALUE + "\n");
        assertFalse(verify(current) == 0);
        Files.delete(current.resolve(LeAudioPolicy.OWNER));
        for (String file : LeAudioPolicy.FILES.keySet()) {
            if (LeAudioPolicy.FILES.get(file).equals(LeAudioPolicy.LEGACY_FILES.get(file))) continue;
            byte[] old = Files.readAllBytes(legacy.resolve(file));
            Files.write(legacy.resolve(file), Files.readAllBytes(current.resolve(file)));
            assertFalse("mixed legacy: " + file, verify(legacy) == 0);
            Files.write(legacy.resolve(file), old);
            byte[] newer = Files.readAllBytes(current.resolve(file));
            Files.write(current.resolve(file), old);
            assertFalse("mixed current: " + file, verify(current) == 0);
            Files.write(current.resolve(file), newer);
        }
    }

    @Test public void currentDuplexHelperManifestRemainsAnExactBundle() throws Exception {
        Path current = copyModule();
        Path manifest = current.resolve("manifest.txt");
        String canonical = readText(manifest);
        writeText(manifest, canonical.replace("|files/bluetooth_policy.xml|", "|profiles/duplex.xml|"));
        assertEquals(0, verify(current));
        writeText(current.resolve("mode.txt"), "music\n");
        assertFalse(verify(current) == 0);
    }

    @Test public void kernelSuMetadataCopyNeedsCompleteCurrentUpdateAndMarker() throws Exception {
        Path current = copyModule(true);
        Path update = copyModule();
        Files.copy(update.resolve("module.prop"), current.resolve("module.prop"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        assertFalse(verify(current) == 0);
        String state = LeAudioPolicy.verificationFunctions() + "fail() { exit 41; }\ncurrent="
                + ShellEscaper.quote(current.toString()) + "\nupdate="
                + ShellEscaper.quote(update.toString()) + "\n" + LeAudioPolicy.moduleState();
        assertFalse(run(state) == 0);
        Files.createFile(current.resolve("update"));
        assertEquals(0, run(state + "[ \"$current_version\" = legacy ] && [ \"$update_version\" = current ]\n"));
        writeText(update.resolve("service.sh"), "changed\n");
        assertFalse(run(state) == 0);
        Files.copy(ASSETS.resolve("service.sh"), update.resolve("service.sh"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        writeText(current.resolve("mode.txt"), "duplex\n");
        assertFalse(run(state) == 0);
        writeText(current.resolve("mode.txt"), "music\n");
        assertEquals(0, run(state));
        String absent = state.replace("update=" + ShellEscaper.quote(update.toString()),
                "update=" + ShellEscaper.quote(update.resolve("missing").toString()));
        assertFalse(run(absent) == 0);
    }

    @Test public void freshArchiveStartsDisabledAndContainsAllSeventeenPinnedFiles() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            LeAudioManager.writeArchive(zip, path -> Files.readAllBytes(ASSETS.resolve(path)));
        }
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertEquals("disable", zip.getNextEntry().getName());
            assertEquals(0, zip.readAllBytes().length);
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        assertEquals(18, entries.size());
        for (Map.Entry<String, String> file : LeAudioPolicy.FILES.entrySet()) {
            assertEquals(file.getValue(), HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(entries.get(file.getKey()))));
        }
        assertTrue(entries.containsKey(LeAudioPolicy.OWNER));
    }

    @Test public void failedOrModifiedFreshInstallStaysDisabledUntilFullyVerified() throws Exception {
        for (int scenario = 0; scenario < 3; scenario++) {
            Path current = temporary.newFolder().toPath();
            Files.copy(ASSETS.resolve("module.prop"), current.resolve("module.prop"));
            Files.createFile(current.resolve("update"));
            Path update = copyModule();
            Files.delete(update.resolve("customize.sh"));
            Files.createFile(update.resolve("disable"));
            if (scenario == 1) writeText(update.resolve("service.sh"), "modified\n");
            Path enabled = temporary.getRoot().toPath().resolve("enabled-" + scenario);
            String script = LeAudioPolicy.verificationFunctions()
                    + "fail() { exit 41; }\nksud() { : > " + ShellEscaper.quote(enabled.toString())
                    + "; }\ncurrent=" + ShellEscaper.quote(current.toString())
                    + "\nupdate=" + ShellEscaper.quote(update.toString())
                    + "\ninstall_result=" + (scenario == 0 ? 12 : 0) + "\n"
                    + LeAudioManager.finishFreshInstallScript();
            int result = run(script);
            if (scenario < 2) {
                assertFalse(result == 0);
                assertTrue(Files.isRegularFile(update.resolve("disable")));
                assertFalse(Files.exists(enabled));
            } else {
                assertEquals(0, result);
                assertFalse(Files.exists(update.resolve("disable")));
                assertTrue(Files.isRegularFile(enabled));
            }
        }
    }

    @Test public void verifiedMigrationEnablesStagedBundleWithoutRewritingCurrentPayload() throws Exception {
        Path current = copyModule(true);
        Path update = copyModule();
        Files.delete(update.resolve("customize.sh"));
        Files.createFile(update.resolve("disable"));
        Files.createFile(current.resolve("update"));
        Files.copy(update.resolve("module.prop"), current.resolve("module.prop"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Map<String, byte[]> before = new HashMap<>();
        for (String file : LeAudioPolicy.LEGACY_FILES.keySet())
            before.put(file, Files.readAllBytes(current.resolve(file)));
        Path enabled = temporary.getRoot().toPath().resolve("migration-enabled");
        String script = LeAudioPolicy.verificationFunctions()
                + "fail() { exit 41; }\nksud() { : > " + ShellEscaper.quote(enabled.toString())
                + "; }\ncurrent=" + ShellEscaper.quote(current.toString())
                + "\nupdate=" + ShellEscaper.quote(update.toString()) + "\ninstall_result=0\n"
                + LeAudioManager.finishFreshInstallScript();
        assertEquals(0, run(script));
        assertTrue(Files.isRegularFile(enabled));
        assertFalse(Files.exists(update.resolve("disable")));
        for (Map.Entry<String, byte[]> entry : before.entrySet())
            org.junit.Assert.assertArrayEquals(entry.getKey(), entry.getValue(),
                    Files.readAllBytes(current.resolve(entry.getKey())));
    }

    @Test public void unknownFilesAndSymlinksAreRejectedWithoutWriting() throws Exception {
        Path module = copyModule();
        writeText(module.resolve("extra-service.sh"), "exit 0\n");
        assertFalse(verify(module) == 0);
        Files.delete(module.resolve("extra-service.sh"));
        writeText(module.resolve("extra\nfile"), "keep\n");
        assertFalse(verify(module) == 0);
        Files.delete(module.resolve("extra\nfile"));
        Path external = temporary.newFile("external").toPath();
        writeText(external, "keep me\n");
        Files.createSymbolicLink(module.resolve("status.txt"), external);
        assertFalse(verify(module) == 0);
        Files.delete(module.resolve("status.txt"));
        Files.createSymbolicLink(module.resolve("disable"), external);
        assertFalse(verify(module) == 0);
        Files.delete(module.resolve("disable"));
        Path link = module.resolveSibling("module-link");
        Files.createSymbolicLink(link, module);
        assertFalse(verify(link) == 0);
        assertEquals("keep me\n", readText(external));
        assertEquals(0, verify(module));
    }

    @Test public void stagedStubRequiresExactCompleteUpdateAndPinnedMetadata() throws Exception {
        Path current = temporary.newFolder("current").toPath();
        Files.copy(ASSETS.resolve("module.prop"), current.resolve("module.prop"));
        Files.createFile(current.resolve("update"));
        Path update = copyModule();
        Files.delete(update.resolve("customize.sh"));
        String script = "fail() { exit 41; }\ncurrent=" + ShellEscaper.quote(current.toString())
                + "\nupdate=" + ShellEscaper.quote(update.toString()) + "\n"
                + LeAudioPolicy.moduleState();
        assertEquals(0, run(LeAudioPolicy.verificationFunctions() + script));
        assertEquals(0, run(LeAudioPolicy.verificationFunctions() + script
                + "[ \"$disabled\" = 0 ]\n"));
        Files.createFile(current.resolve("disable"));
        assertEquals(0, run(LeAudioPolicy.verificationFunctions() + script
                + "[ \"$disabled\" = 1 ]\n"));
        Files.createFile(update.resolve("disable"));
        assertEquals(0, run(LeAudioPolicy.verificationFunctions() + script
                + "[ \"$disabled\" = 1 ]\n"));
        Files.delete(current.resolve("disable"));
        assertEquals(0, run(LeAudioPolicy.verificationFunctions() + script
                + "[ \"$disabled\" = 1 ]\n"));
        writeText(update.resolve("service.sh"), "changed\n");
        assertFalse(run(LeAudioPolicy.verificationFunctions() + script) == 0);
        Files.copy(ASSETS.resolve("service.sh"), update.resolve("service.sh"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        writeText(current.resolve("module.prop"), "id=s23_le_audio_fix\n");
        assertFalse(run(LeAudioPolicy.verificationFunctions() + script) == 0);
    }

    @Test public void deviceGateIsIndependentButRemainsExact() {
        DiagnosticReport report = new DiagnosticReport();
        report.root = report.kernelSuReady = report.exactEnvironmentGate = true;
        assertTrue(LeAudioPolicy.deviceAllowed(report));
        assertFalse(report.quickSearchCompatible);
        assertFalse(report.vectorReady);
        assertFalse(report.magicMountReady);
        report.exactEnvironmentGate = false;
        assertFalse(LeAudioPolicy.deviceAllowed(report));
        report.exactEnvironmentGate = true;
        report.kernelSuReady = false;
        assertFalse(LeAudioPolicy.deviceAllowed(report));
    }

    @Test public void integrationDoesNotAddMusicToCoreApplyOrRestore() throws Exception {
        String core = readText(Path.of("src/main/java/com/codex/evoxquickfix/FixManager.java"));
        assertFalse(core.contains("LeAudio"));
        String apply = LeAudioManager.applyScript("/app/cache/module.zip", "0".repeat(64));
        assertTrue(apply.contains("[ \"$rom_ok\" = 1 ] || fail rom_changed"));
        assertTrue(apply.contains("appeared_during_install"));
        assertFalse(apply.contains("set-mode.sh "));
        assertFalse(apply.contains("reboot\n"));
        String disable = LeAudioManager.disableScript();
        assertFalse(disable.contains("module uninstall"));
        assertFalse(disable.contains("rm -rf"));
        assertFalse(disable.contains("rom_ok"));
        assertTrue(disable.contains("verify_module"));
    }

    private static String readText(Path path) throws Exception {
        return new String(Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void writeText(Path path, String text) throws Exception {
        Files.write(path, text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private Path copyModule() throws Exception {
        return copyModule(false);
    }

    private Path copyModule(boolean legacy) throws Exception {
        Path module = temporary.newFolder().toPath();
        for (String file : LeAudioPolicy.FILES.keySet()) {
            Path target = module.resolve(file);
            Files.createDirectories(target.getParent());
            Path source = legacy && Files.exists(LEGACY.resolve(file)) ? LEGACY.resolve(file) : ASSETS.resolve(file);
            Files.copy(source, target);
        }
        return module;
    }

    private int verify(Path module) throws Exception {
        return run(LeAudioPolicy.verificationFunctions() + "verify_module "
                + ShellEscaper.quote(module.toString()));
    }

    private int run(String script) throws Exception {
        Process process = new ProcessBuilder("sh", "-c", script).redirectErrorStream(true).start();
        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        if (!finished) process.destroyForcibly();
        assertTrue("guard timed out", finished);
        return process.exitValue();
    }
}
