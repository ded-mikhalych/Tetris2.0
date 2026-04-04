package ru.danil.tetris.rmi.server.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameStatisticsStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsAggregateStatistics() {
        GameStatisticsStore store = new GameStatisticsStore(tempDir.resolve("stats.db"));

        assertEquals(GameStatistics.empty("Alice"), store.loadStatistics("Alice"));

        store.saveResult("Alice", 120, 10, 20);
        store.saveResult("Alice", 80, 8, 16);
        store.saveResult("Bob", 150, 12, 24);

        GameStatistics statistics = store.loadStatistics("Alice");
        assertEquals(2, statistics.gamesPlayed());
        assertEquals(120, statistics.bestScore());
        assertEquals("Alice", statistics.playerNickname());
        assertEquals(2, statistics.topPlayers().size());
        assertEquals("Bob", statistics.topPlayers().get(0).nickname());
        assertEquals(150, statistics.topPlayers().get(0).bestScore());
        assertEquals(12, statistics.topPlayers().get(0).fieldWidth());
        assertEquals(24, statistics.topPlayers().get(0).fieldHeight());
    }
}
