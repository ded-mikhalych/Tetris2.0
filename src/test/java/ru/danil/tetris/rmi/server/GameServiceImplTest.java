package ru.danil.tetris.rmi.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.danil.tetris.rmi.common.GameSnapshot;
import ru.danil.tetris.rmi.server.storage.GameStatisticsStore;

import java.nio.file.Path;
import java.rmi.RemoteException;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameServiceImplTest {
    @TempDir
    Path tempDir;

    @Test
    void countsOnlyCompletedGamesInStatistics() throws RemoteException {
        GameServiceImpl service = createService();
        String aliceSessionId = service.createSession("Alice");

        GameSnapshot initial = service.updatePlayerNickname(aliceSessionId, "Alice");
        assertEquals(0, initial.gamesPlayed());
        assertEquals(0, initial.bestScore());
        assertEquals("Alice", initial.playerNickname());

        GameSnapshot finished = service.finishGame(aliceSessionId);
        assertEquals(1, finished.gamesPlayed());
        assertEquals(0, finished.bestScore());

        GameSnapshot duplicateFinish = service.finishGame(aliceSessionId);
        assertEquals(1, duplicateFinish.gamesPlayed());

        service.startNewGame(aliceSessionId, 10, 20);
        GameSnapshot secondFinished = service.finishGame(aliceSessionId);
        assertEquals(2, secondFinished.gamesPlayed());

        String bobSessionId = service.createSession("Bob");
        GameSnapshot bobState = service.updatePlayerNickname(bobSessionId, "Bob");
        assertEquals("Bob", bobState.playerNickname());
        assertEquals(0, bobState.gamesPlayed());
        assertEquals("Alice", bobState.topPlayers().get(0).nickname());
        assertEquals(10, bobState.topPlayers().get(0).fieldWidth());
        assertEquals(20, bobState.topPlayers().get(0).fieldHeight());
    }

    private GameServiceImpl createService() throws RemoteException {
        return new GameServiceImpl(
            new Random(1L),
            new GameStatisticsStore(tempDir.resolve("service-stats.db"))
        );
    }
}
