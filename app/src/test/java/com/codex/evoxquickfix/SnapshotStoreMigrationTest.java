package com.codex.evoxquickfix;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

public class SnapshotStoreMigrationTest {
    @Test
    public void schema4MigrationMarksAmbiguousQueriesUnknown() {
        Map<String, Object> migrated = SnapshotSchema.migrateNestedSchema4(nestedSchema4());

        assertNotNull(migrated);
        assertTrue(Integer.valueOf(4).equals(migrated.get("schema")));
        assertTrue(SnapshotSchema.validSchema4(migrated));
        assertFalse(section(migrated, "transparency", "stateCaptured"));
        assertFalse(section(migrated, "back", "stateCaptured"));
        assertTrue(section(migrated, "circle", "stateCaptured"));
        assertTrue(object(migrated, "featureBaselines").isEmpty());
    }

    @Test
    public void schema3MigrationPreservesStrictBaseline() {
        Map<String, Object> migrated = SnapshotSchema.migrateFlat(flatSchema3());

        assertNotNull(migrated);
        assertTrue(Integer.valueOf(4).equals(migrated.get("schema")));
        assertTrue(SnapshotSchema.validSchema4(migrated));
        assertTrue(section(migrated, "transparency", "stateCaptured"));
        assertTrue(section(migrated, "back", "stateCaptured"));
        assertTrue(section(migrated, "back", "vectorAvailable"));
        assertTrue(section(migrated, "circle", "stateCaptured"));
    }

    @Test
    public void schema2MigrationAddsSafeAbsentOverviewState() {
        Map<String, Object> source = flatSchema3();
        source.put("schema", 2);
        source.remove("overviewModuleExisted");
        source.remove("overviewCurrentExisted");
        source.remove("overviewUpdateExisted");
        source.remove("overviewRemoveMarked");
        source.remove("overviewDisableMarked");

        Map<String, Object> migrated = SnapshotSchema.migrateFlat(source);

        assertNotNull(migrated);
        assertTrue(Integer.valueOf(4).equals(migrated.get("schema")));
        assertTrue(SnapshotSchema.validSchema4(migrated));
        assertFalse(section(migrated, "transparency", "overviewModuleExisted"));
    }

    @Test
    public void strictShapeRejectsStringBoolean() {
        Map<String, Object> migrated = SnapshotSchema.migrateFlat(flatSchema3());
        assertNotNull(migrated);

        object(migrated, "back").put("vectorModuleEnabled", "false");

        assertFalse(SnapshotSchema.validSchema4(migrated));
    }

    @Test
    public void featureBaselineMustBeTrustworthy() {
        Map<String, Object> migrated = SnapshotSchema.migrateFlat(flatSchema3());
        assertNotNull(migrated);
        Map<String, Object> baseline = new LinkedHashMap<>(object(migrated, "back"));
        object(migrated, "featureBaselines").put("back", baseline);
        assertTrue(SnapshotSchema.validSchema4(migrated));

        baseline.put("stateCaptured", false);

        assertFalse(SnapshotSchema.validSchema4(migrated));
    }

    @Test
    public void unknownFeatureBaselineIsRejected() {
        Map<String, Object> migrated = SnapshotSchema.migrateFlat(flatSchema3());
        assertNotNull(migrated);
        object(migrated, "featureBaselines").put("future", new LinkedHashMap<>());
        assertFalse(SnapshotSchema.validSchema4(migrated));
    }

    @Test
    public void malformedSchema4IsNotMigrated() {
        Map<String, Object> source = nestedSchema4();
        object(source, "back").put("vectorAvailable", "true");
        assertNull(SnapshotSchema.migrateNestedSchema4(source));
    }

    private static Map<String, Object> nestedSchema4() {
        Map<String, Object> root = base(4);
        root.put("transparency", mapOf(
                "darkOverlayEnabled", false,
                "lightOverlayEnabled", false,
                "overviewModuleExisted", false,
                "overviewCurrentExisted", false,
                "overviewUpdateExisted", false,
                "overviewRemoveMarked", false,
                "overviewDisableMarked", false));
        root.put("back", mapOf(
                "vectorAvailable", true,
                "vectorModuleEnabled", false,
                "vectorScopeHadQuickSearch", false));
        root.put("circle", mapOf(
                "searchAllEntrypoints", null,
                "navbarLongPress", "0",
                "circleModuleExisted", false,
                "circleCurrentExisted", false,
                "circleUpdateExisted", false,
                "circleRemoveMarked", false,
                "circleDisableMarked", false));
        return root;
    }

    private static Map<String, Object> flatSchema3() {
        Map<String, Object> root = base(3);
        root.putAll(mapOf(
                "searchAllEntrypoints", null,
                "navbarLongPress", "0",
                "darkOverlayEnabled", false,
                "lightOverlayEnabled", true,
                "overviewModuleExisted", false,
                "overviewCurrentExisted", false,
                "overviewUpdateExisted", false,
                "overviewRemoveMarked", false,
                "overviewDisableMarked", false,
                "vectorModuleEnabled", false,
                "vectorScopeHadQuickSearch", false,
                "circleModuleExisted", false,
                "circleCurrentExisted", false,
                "circleUpdateExisted", false,
                "circleRemoveMarked", false,
                "circleDisableMarked", false));
        return root;
    }

    private static Map<String, Object> base(int schema) {
        return mapOf(
                "schema", schema,
                "createdAt", "2026-08-25T00:00:00Z",
                "buildFingerprint", "test/fingerprint",
                "model", "SM-S918B",
                "sdk", 36,
                "systemUser", true,
                "disabledPackagesSha256", "a".repeat(64));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Map<String, Object> map, String key) {
        return (Map<String, Object>) map.get(key);
    }

    private static boolean section(Map<String, Object> root, String section, String key) {
        return Boolean.TRUE.equals(object(root, section).get(key));
    }

    private static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            map.put((String) values[index], values[index + 1]);
        }
        return map;
    }
}
