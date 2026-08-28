package com.becker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataCollectorTest {

    @Test
    void readsCollectionSettings() {
        DataCollector.CollectionSettings settings = DataCollector.readSettings(
                new String[]{"100", "8", "8", "20"});

        assertEquals(100, settings.count);
        assertEquals(8, settings.searchDepth);
        assertEquals(8, settings.minOpeningPlies);
        assertEquals(20, settings.maxOpeningPlies);
        assertTrue(settings.threads >= 1);
    }

    @Test
    void readsCollectionSettingsWithExplicitThreads() {
        DataCollector.CollectionSettings settings = DataCollector.readSettings(
                new String[]{"200", "6", "4", "12", "4"});

        assertEquals(200, settings.count);
        assertEquals(6, settings.searchDepth);
        assertEquals(4, settings.minOpeningPlies);
        assertEquals(12, settings.maxOpeningPlies);
        assertEquals(4, settings.threads);
    }

    @Test
    void rejectsOpeningRangeInReverseOrder() {
        assertThrows(IllegalArgumentException.class, () -> DataCollector.readSettings(
                new String[]{"100", "8", "20", "8"}));
    }
}
