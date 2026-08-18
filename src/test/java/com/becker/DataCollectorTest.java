package com.becker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataCollectorTest {

    @Test
    void readsCollectionSettings() {
        DataCollector.CollectionSettings settings = DataCollector.readSettings(
                new String[]{"100", "8", "8", "20"});

        assertEquals(100, settings.count);
        assertEquals(8, settings.searchDepth);
        assertEquals(8, settings.minOpeningPlies);
        assertEquals(20, settings.maxOpeningPlies);
    }

    @Test
    void rejectsOpeningRangeInReverseOrder() {
        assertThrows(IllegalArgumentException.class, () -> DataCollector.readSettings(
                new String[]{"100", "8", "20", "8"}));
    }
}
