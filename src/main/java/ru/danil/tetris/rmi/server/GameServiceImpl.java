package ru.danil.tetris.rmi.server;

import ru.danil.tetris.rmi.common.ActiveFigure;
import ru.danil.tetris.rmi.common.FigureType;
import ru.danil.tetris.rmi.common.GameService;
import ru.danil.tetris.rmi.common.GameSnapshot;
import ru.danil.tetris.rmi.server.storage.GameStatistics;
import ru.danil.tetris.rmi.server.storage.GameStatisticsStore;

import java.nio.file.Path;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class GameServiceImpl extends UnicastRemoteObject implements GameService {
    private static final int DEFAULT_WIDTH = 10;
    private static final int DEFAULT_HEIGHT = 20;
    private static final int MAX_WIDTH = 25;
    private static final int MAX_HEIGHT = 25;
    private static final String DEFAULT_NICKNAME = "\u0418\u0433\u0440\u043e\u043a";

    private final Random random;
    private final GameStatisticsStore statisticsStore;
    private final ConcurrentMap<String, GameSession> sessions;

    public GameServiceImpl() throws RemoteException {
        this(new Random(), new GameStatisticsStore(Path.of("data", "tetris.db")));
    }

    GameServiceImpl(Random random, GameStatisticsStore statisticsStore) throws RemoteException {
        super();
        this.random = Objects.requireNonNull(random, "random");
        this.statisticsStore = Objects.requireNonNull(statisticsStore, "statisticsStore");
        this.sessions = new ConcurrentHashMap<>();
    }

    @Override
    public String createSession(String nickname) {
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, new GameSession(sanitizeNickname(nickname)));
        return sessionId;
    }

    @Override
    public GameSnapshot updatePlayerNickname(String sessionId, String nickname) {
        return getSession(sessionId).updatePlayerNickname(nickname);
    }

    @Override
    public GameSnapshot startNewGame(String sessionId, int width, int height) {
        return getSession(sessionId).startNewGame(width, height);
    }

    @Override
    public GameSnapshot getState(String sessionId) {
        return getSession(sessionId).getState();
    }

    @Override
    public GameSnapshot moveLeft(String sessionId) {
        return getSession(sessionId).moveLeft();
    }

    @Override
    public GameSnapshot moveRight(String sessionId) {
        return getSession(sessionId).moveRight();
    }

    @Override
    public GameSnapshot rotate(String sessionId) {
        return getSession(sessionId).rotate();
    }

    @Override
    public GameSnapshot moveDown(String sessionId) {
        return getSession(sessionId).moveDown();
    }

    @Override
    public GameSnapshot dropFigure(String sessionId) {
        return getSession(sessionId).dropFigure();
    }

    @Override
    public GameSnapshot tick(String sessionId) {
        return getSession(sessionId).tick();
    }

    @Override
    public GameSnapshot finishGame(String sessionId) {
        return getSession(sessionId).finishGame();
    }

    private GameSession getSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("\u041d\u0435\u043a\u043e\u0440\u0440\u0435\u043a\u0442\u043d\u0430\u044f \u0441\u0435\u0441\u0441\u0438\u044f.");
        }

        GameSession session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalStateException("\u0421\u0435\u0441\u0441\u0438\u044f \u0438\u0433\u0440\u043e\u043a\u0430 \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d\u0430. \u041f\u0435\u0440\u0435\u0437\u0430\u043f\u0443\u0441\u0442\u0438\u0442\u0435 \u043a\u043b\u0438\u0435\u043d\u0442.");
        }
        return session;
    }

    private String sanitizeNickname(String nickname) {
        if (nickname == null) {
            return DEFAULT_NICKNAME;
        }

        String normalized = nickname.trim();
        return normalized.isEmpty() ? DEFAULT_NICKNAME : normalized;
    }

    private final class GameSession {
        private boolean[][] board;
        private ActiveFigure activeFigure;
        private boolean gameOver;
        private boolean resultSaved;
        private int placedFigures;
        private String currentPlayerNickname;
        private String statusMessage;

        private GameSession(String nickname) {
            this.currentPlayerNickname = sanitizeNickname(nickname);
            initialize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
            statusMessage = "\u0421\u0435\u0441\u0441\u0438\u044f \u0438\u0433\u0440\u043e\u043a\u0430 " + currentPlayerNickname + " \u0441\u043e\u0437\u0434\u0430\u043d\u0430.";
        }

        private synchronized GameSnapshot updatePlayerNickname(String nickname) {
            currentPlayerNickname = sanitizeNickname(nickname);
            statusMessage = "\u0418\u0433\u0440\u043e\u043a: " + currentPlayerNickname + ".";
            return buildSnapshot();
        }

        private synchronized GameSnapshot startNewGame(int width, int height) {
            initialize(width, height);
            return buildSnapshot();
        }

        private synchronized GameSnapshot getState() {
            return buildSnapshot();
        }

        private synchronized GameSnapshot moveLeft() {
            if (!ensureGameInProgress()) {
                return buildSnapshot();
            }

            ActiveFigure candidate = new ActiveFigure(
                activeFigure.type(),
                activeFigure.rotationIndex(),
                activeFigure.x() - 1,
                activeFigure.y()
            );

            if (canPlace(candidate)) {
                activeFigure = candidate;
                statusMessage = "\u0424\u0438\u0433\u0443\u0440\u0430 \u0441\u0434\u0432\u0438\u043d\u0443\u0442\u0430 \u0432\u043b\u0435\u0432\u043e.";
            } else {
                statusMessage = "\u0421\u0434\u0432\u0438\u0433 \u0432\u043b\u0435\u0432\u043e \u043d\u0435\u0432\u043e\u0437\u043c\u043e\u0436\u0435\u043d.";
            }
            return buildSnapshot();
        }

        private synchronized GameSnapshot moveRight() {
            if (!ensureGameInProgress()) {
                return buildSnapshot();
            }

            ActiveFigure candidate = new ActiveFigure(
                activeFigure.type(),
                activeFigure.rotationIndex(),
                activeFigure.x() + 1,
                activeFigure.y()
            );

            if (canPlace(candidate)) {
                activeFigure = candidate;
                statusMessage = "\u0424\u0438\u0433\u0443\u0440\u0430 \u0441\u0434\u0432\u0438\u043d\u0443\u0442\u0430 \u0432\u043f\u0440\u0430\u0432\u043e.";
            } else {
                statusMessage = "\u0421\u0434\u0432\u0438\u0433 \u0432\u043f\u0440\u0430\u0432\u043e \u043d\u0435\u0432\u043e\u0437\u043c\u043e\u0436\u0435\u043d.";
            }
            return buildSnapshot();
        }

        private synchronized GameSnapshot rotate() {
            if (!ensureGameInProgress()) {
                return buildSnapshot();
            }

            ActiveFigure rotated = new ActiveFigure(
                activeFigure.type(),
                (activeFigure.rotationIndex() + 1) % activeFigure.type().getRotationCount(),
                activeFigure.x(),
                activeFigure.y()
            );

            if (canPlace(rotated)) {
                activeFigure = rotated;
                statusMessage = "\u0424\u0438\u0433\u0443\u0440\u0430 \u043f\u043e\u0432\u0435\u0440\u043d\u0443\u0442\u0430.";
                return buildSnapshot();
            }

            int[] wallKickOffsets = {-1, 1, -2, 2};
            for (int offset : wallKickOffsets) {
                ActiveFigure shifted = new ActiveFigure(
                    rotated.type(),
                    rotated.rotationIndex(),
                    rotated.x() + offset,
                    rotated.y()
                );
                if (canPlace(shifted)) {
                    activeFigure = shifted;
                    statusMessage = "\u0424\u0438\u0433\u0443\u0440\u0430 \u043f\u043e\u0432\u0435\u0440\u043d\u0443\u0442\u0430 \u0441\u043e \u0441\u0434\u0432\u0438\u0433\u043e\u043c.";
                    return buildSnapshot();
                }
            }

            statusMessage = "\u041f\u043e\u0432\u043e\u0440\u043e\u0442 \u043d\u0435\u0432\u043e\u0437\u043c\u043e\u0436\u0435\u043d.";
            return buildSnapshot();
        }

        private synchronized GameSnapshot moveDown() {
            if (!ensureGameInProgress()) {
                return buildSnapshot();
            }
            return advanceDown(true);
        }

        private synchronized GameSnapshot dropFigure() {
            if (!ensureGameInProgress()) {
                return buildSnapshot();
            }

            while (!gameOver) {
                ActiveFigure next = new ActiveFigure(
                    activeFigure.type(),
                    activeFigure.rotationIndex(),
                    activeFigure.x(),
                    activeFigure.y() + 1
                );
                if (!canPlace(next)) {
                    lockCurrentFigure();
                    if (!gameOver && statusMessage.startsWith("\u0424\u0438\u0433\u0443\u0440\u0430 \u0437\u0430\u0444\u0438\u043a\u0441\u0438\u0440\u043e\u0432\u0430\u043d\u0430")) {
                        statusMessage = "\u0424\u0438\u0433\u0443\u0440\u0430 \u043c\u0433\u043d\u043e\u0432\u0435\u043d\u043d\u043e \u0440\u0430\u0437\u043c\u0435\u0449\u0435\u043d\u0430. \u0421\u0435\u0440\u0432\u0435\u0440 \u0432\u044b\u0434\u0430\u043b \u0441\u043b\u0435\u0434\u0443\u044e\u0449\u0443\u044e.";
                    }
                    return buildSnapshot();
                }
                activeFigure = next;
            }
            return buildSnapshot();
        }

        private synchronized GameSnapshot tick() {
            if (!ensureGameInProgress()) {
                return buildSnapshot();
            }
            return advanceDown(false);
        }

        private synchronized GameSnapshot finishGame() {
            finishCurrentGame("\u0418\u0433\u0440\u0430 \u0437\u0430\u0432\u0435\u0440\u0448\u0435\u043d\u0430 \u043f\u043e \u043a\u043e\u043c\u0430\u043d\u0434\u0435 \u043a\u043b\u0438\u0435\u043d\u0442\u0430.");
            return buildSnapshot();
        }

        private GameSnapshot advanceDown(boolean manualMove) {
            ActiveFigure next = new ActiveFigure(
                activeFigure.type(),
                activeFigure.rotationIndex(),
                activeFigure.x(),
                activeFigure.y() + 1
            );

            if (canPlace(next)) {
                activeFigure = next;
                statusMessage = manualMove
                    ? "\u0424\u0438\u0433\u0443\u0440\u0430 \u043e\u043f\u0443\u0449\u0435\u043d\u0430 \u043d\u0430 \u043e\u0434\u043d\u0443 \u043a\u043b\u0435\u0442\u043a\u0443."
                    : "\u0424\u0438\u0433\u0443\u0440\u0430 \u043f\u0430\u0434\u0430\u0435\u0442.";
                return buildSnapshot();
            }

            lockCurrentFigure();
            return buildSnapshot();
        }

        private void initialize(int width, int height) {
            if (width < 4 || height < 6) {
                throw new IllegalArgumentException("\u041c\u0438\u043d\u0438\u043c\u0430\u043b\u044c\u043d\u044b\u0439 \u0440\u0430\u0437\u043c\u0435\u0440 \u043f\u043e\u043b\u044f: 4x6.");
            }
            if (width > MAX_WIDTH || height > MAX_HEIGHT) {
                throw new IllegalArgumentException("\u041c\u0430\u043a\u0441\u0438\u043c\u0430\u043b\u044c\u043d\u044b\u0439 \u0440\u0430\u0437\u043c\u0435\u0440 \u043f\u043e\u043b\u044f: 25x25.");
            }

            board = new boolean[height][width];
            placedFigures = 0;
            gameOver = false;
            resultSaved = false;
            statusMessage = "\u041d\u043e\u0432\u0430\u044f \u0438\u0433\u0440\u0430 \u043d\u0430\u0447\u0430\u043b\u0430\u0441\u044c.";
            spawnNextFigure();
        }

        private void spawnNextFigure() {
            FigureType[] values = FigureType.values();
            FigureType type = values[random.nextInt(values.length)];
            boolean[][] shape = type.getRotation(0);
            int startX = Math.max(0, (board[0].length - shape[0].length) / 2);
            activeFigure = new ActiveFigure(type, 0, startX, 0);

            if (!canPlace(activeFigure)) {
                finishCurrentGame("\u041d\u043e\u0432\u0430\u044f \u0444\u0438\u0433\u0443\u0440\u0430 \u043d\u0435 \u043f\u043e\u043c\u0435\u0449\u0430\u0435\u0442\u0441\u044f. \u0418\u0433\u0440\u0430 \u0437\u0430\u0432\u0435\u0440\u0448\u0435\u043d\u0430.");
            }
        }

        private boolean canPlace(ActiveFigure figure) {
            boolean[][] shape = figure.type().getRotation(figure.rotationIndex());

            if (figure.x() < 0 || figure.y() < 0) {
                return false;
            }
            if (figure.y() + shape.length > board.length) {
                return false;
            }

            for (int row = 0; row < shape.length; row++) {
                for (int col = 0; col < shape[row].length; col++) {
                    if (!shape[row][col]) {
                        continue;
                    }

                    int boardX = figure.x() + col;
                    int boardY = figure.y() + row;
                    if (boardX < 0 || boardX >= board[0].length || boardY < 0 || boardY >= board.length) {
                        return false;
                    }
                    if (board[boardY][boardX]) {
                        return false;
                    }
                }
            }

            return true;
        }

        private void lockCurrentFigure() {
            boolean[][] shape = activeFigure.type().getRotation(activeFigure.rotationIndex());
            for (int row = 0; row < shape.length; row++) {
                for (int col = 0; col < shape[row].length; col++) {
                    if (shape[row][col]) {
                        board[activeFigure.y() + row][activeFigure.x() + col] = true;
                    }
                }
            }

            placedFigures++;
            if (isBoardFull()) {
                finishCurrentGame("\u041f\u043e\u043b\u0435 \u0437\u0430\u043f\u043e\u043b\u043d\u0435\u043d\u043e \u043f\u043e\u043b\u043d\u043e\u0441\u0442\u044c\u044e. \u0418\u0433\u0440\u0430 \u0437\u0430\u0432\u0435\u0440\u0448\u0435\u043d\u0430.");
                return;
            }

            spawnNextFigure();
            if (!gameOver) {
                statusMessage = "\u0424\u0438\u0433\u0443\u0440\u0430 \u0437\u0430\u0444\u0438\u043a\u0441\u0438\u0440\u043e\u0432\u0430\u043d\u0430. \u0421\u0435\u0440\u0432\u0435\u0440 \u0432\u044b\u0434\u0430\u043b \u0441\u043b\u0435\u0434\u0443\u044e\u0449\u0443\u044e.";
            }
        }

        private void finishCurrentGame(String message) {
            gameOver = true;
            statusMessage = message;
            if (!resultSaved) {
                statisticsStore.saveResult(currentPlayerNickname, calculateScore(), board[0].length, board.length);
                resultSaved = true;
            }
        }

        private boolean isBoardFull() {
            for (boolean[] row : board) {
                for (boolean cell : row) {
                    if (!cell) {
                        return false;
                    }
                }
            }
            return true;
        }

        private int countOccupiedCells() {
            int occupied = 0;
            for (boolean[] row : board) {
                for (boolean cell : row) {
                    if (cell) {
                        occupied++;
                    }
                }
            }
            return occupied;
        }

        private int countHoles() {
            int holes = 0;
            for (int col = 0; col < board[0].length; col++) {
                boolean filledSeen = false;
                for (boolean[] row : board) {
                    if (row[col]) {
                        filledSeen = true;
                    } else if (filledSeen) {
                        holes++;
                    }
                }
            }
            return holes;
        }

        private int calculateScore() {
            return Math.max(0, countOccupiedCells() * 10 - countHoles() * 2);
        }

        private List<String> renderBoardRows() {
            char[][] rendered = new char[board.length][board[0].length];
            for (int row = 0; row < board.length; row++) {
                for (int col = 0; col < board[row].length; col++) {
                    rendered[row][col] = board[row][col] ? '#' : '.';
                }
            }

            if (!gameOver && activeFigure != null && canPlace(activeFigure)) {
                boolean[][] shape = activeFigure.type().getRotation(activeFigure.rotationIndex());
                for (int row = 0; row < shape.length; row++) {
                    for (int col = 0; col < shape[row].length; col++) {
                        if (shape[row][col]) {
                            rendered[activeFigure.y() + row][activeFigure.x() + col] = '*';
                        }
                    }
                }
            }

            List<String> rows = new ArrayList<>(rendered.length);
            for (char[] row : rendered) {
                rows.add(new String(row));
            }
            return rows;
        }

        private GameSnapshot buildSnapshot() {
            GameStatistics statistics = statisticsStore.loadStatistics(currentPlayerNickname);
            return new GameSnapshot(
                board[0].length,
                board.length,
                renderBoardRows(),
                activeFigure,
                gameOver,
                placedFigures,
                countOccupiedCells(),
                countHoles(),
                calculateScore(),
                statistics.playerNickname(),
                statistics.bestScore(),
                statistics.gamesPlayed(),
                statistics.topPlayers(),
                statusMessage
            );
        }

        private boolean ensureGameInProgress() {
            if (gameOver) {
                statusMessage = "\u0418\u0433\u0440\u0430 \u0443\u0436\u0435 \u0437\u0430\u0432\u0435\u0440\u0448\u0435\u043d\u0430. \u041d\u0430\u0447\u043d\u0438\u0442\u0435 \u043d\u043e\u0432\u0443\u044e \u043f\u0430\u0440\u0442\u0438\u044e.";
                return false;
            }
            return true;
        }
    }
}
