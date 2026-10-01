package com.codex.evoxquickfix;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class DeviceModelGateTest {
    @Test
    public void acceptsEveryS23FamilySuffix() {
        assertTrue(DeviceDiagnostics.isSupportedS23Model("SM-S911B"));
        assertTrue(DeviceDiagnostics.isSupportedS23Model("SM-S911U1"));
        assertTrue(DeviceDiagnostics.isSupportedS23Model("SM-S916N"));
        assertTrue(DeviceDiagnostics.isSupportedS23Model("SM-S9180"));
        assertTrue(DeviceDiagnostics.isSupportedS23Model("SM-S918"));
        assertTrue(DeviceDiagnostics.isSupportedS23Model("sm-s918b"));
    }

    @Test
    public void rejectsS23FeAndOtherModels() {
        assertFalse(DeviceDiagnostics.isSupportedS23Model("SM-S711B"));
        assertFalse(DeviceDiagnostics.isSupportedS23Model("SM-S921B"));
        assertFalse(DeviceDiagnostics.isSupportedS23Model("SM-S919B"));
        assertFalse(DeviceDiagnostics.isSupportedS23Model(""));
        assertFalse(DeviceDiagnostics.isSupportedS23Model(null));
    }

    @Test
    public void nativeCircleDoesNotRequireMagicMount() {
        DiagnosticReport report = circleBase();
        report.contextualFeature = true;
        assertTrue(DeviceDiagnostics.supportsCircle(report));
    }

    @Test
    public void missingCircleFeatureRequiresMagicMount() {
        DiagnosticReport report = circleBase();
        assertFalse(DeviceDiagnostics.supportsCircle(report));
        report.magicMountReady = true;
        assertTrue(DeviceDiagnostics.supportsCircle(report));
    }

    @Test
    public void magiskAndNonKernelSuAreRejected() {
        DiagnosticReport report = circleBase();
        report.contextualFeature = true;
        report.magiskPresent = true;
        assertFalse(DeviceDiagnostics.supportsCircle(report));
        assertFalse(DeviceDiagnostics.supportsDebloat(report));
        report.magiskPresent = false;
        report.kernelSuReady = false;
        assertFalse(DeviceDiagnostics.supportsCircle(report));
        assertFalse(DeviceDiagnostics.supportsDebloat(report));
    }

    @Test
    public void oneBackUsesIndependentRuntimeAndObservedVectorRequirements() {
        DiagnosticReport report = new DiagnosticReport();
        report.android16 = true;
        report.root = true;
        report.systemUser = true;
        report.vectorReady = true;
        report.vectorModulesObserved = true;
        report.vectorScopeObserved = true;
        report.heliBoardPresent = true;

        // Deliberately leave the model, KernelSU, Quick Search and default-IME gates false.
        assertTrue(DeviceDiagnostics.supportsOneBack(report));

        report.heliBoardPresent = false;
        assertFalse(DeviceDiagnostics.supportsOneBack(report));
        report.heliBoardPresent = true;
        report.vectorReady = false;
        assertFalse(DeviceDiagnostics.supportsOneBack(report));
        report.vectorReady = true;
        report.vectorScopeObserved = false;
        assertFalse(DeviceDiagnostics.supportsOneBack(report));
        report.vectorScopeObserved = true;
        report.vectorModulesObserved = false;
        assertFalse(DeviceDiagnostics.supportsOneBack(report));
        report.vectorModulesObserved = true;
        report.systemUser = false;
        assertFalse(DeviceDiagnostics.supportsOneBack(report));
        report.systemUser = true;
        report.root = false;
        assertFalse(DeviceDiagnostics.supportsOneBack(report));
        report.root = true;
        report.android16 = false;
        assertFalse(DeviceDiagnostics.supportsOneBack(report));
    }

    @Test
    public void defaultImeDetectionMatchesOnlyTheComponentPackage() {
        assertTrue(DeviceDiagnostics.isPackageDefaultIme(
                "helium314.keyboard/.latin.LatinIME", AppConstants.HELIBOARD_PACKAGE));
        assertFalse(DeviceDiagnostics.isPackageDefaultIme(
                "com.google.android.inputmethod.latin/.LatinIME",
                AppConstants.HELIBOARD_PACKAGE));
        assertFalse(DeviceDiagnostics.isPackageDefaultIme(
                "helium314.keyboard.fake/.Ime", AppConstants.HELIBOARD_PACKAGE));
        assertFalse(DeviceDiagnostics.isPackageDefaultIme(null,
                AppConstants.HELIBOARD_PACKAGE));
    }

    private static DiagnosticReport circleBase() {
        DiagnosticReport report = new DiagnosticReport();
        report.root = true;
        report.deviceGate = true;
        report.kernelSuReady = true;
        report.launcherPresent = true;
        report.googleProvider = true;
        report.contextualService = true;
        return report;
    }
}
