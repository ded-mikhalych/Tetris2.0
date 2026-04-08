package ru.danil.tetris.rmi.common;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface GameService extends Remote {
    String createSession(String nickname) throws RemoteException;

    GameSnapshot updatePlayerNickname(String sessionId, String nickname) throws RemoteException;

    GameSnapshot startNewGame(String sessionId, int width, int height) throws RemoteException;

    GameSnapshot getState(String sessionId) throws RemoteException;

    GameSnapshot moveLeft(String sessionId) throws RemoteException;

    GameSnapshot moveRight(String sessionId) throws RemoteException;

    GameSnapshot rotate(String sessionId) throws RemoteException;

    GameSnapshot moveDown(String sessionId) throws RemoteException;

    GameSnapshot dropFigure(String sessionId) throws RemoteException;

    GameSnapshot tick(String sessionId) throws RemoteException;

    GameSnapshot finishGame(String sessionId) throws RemoteException;
}



package ru.danil.tetris.rmi.common;

import java.io.Serializable;
import java.util.List;

public record GameSnapshot(
    int width,
    int height,
    List<String> boardRows,
    ActiveFigure activeFigure,
    boolean gameOver,
    int placedFigures,
    int occupiedCells,
    int holes,
    int score,
    String playerNickname,
    int bestScore,
    int gamesPlayed,
    List<PlayerScore> topPlayers,
    String statusMessage
) implements Serializable {
}



package ru.danil.tetris.rmi.common;

import java.io.Serializable;

public record PlayerScore(String nickname, int bestScore, int fieldWidth, int fieldHeight) implements Serializable {
}



package ru.danil.tetris.rmi.common;

import java.io.Serializable;

public enum FigureType implements Serializable {
    I('I', new boolean[][][] {
        {
            {true, true, true, true}
        },
        {
            {true},
            {true},
            {true},
            {true}
        }
    }),
    O('O', new boolean[][][] {
        {
            {true, true},
            {true, true}
        }
    }),
    T('T', new boolean[][][] {
        {
            {true, true, true},
            {false, true, false}
        },
        {
            {true, false},
            {true, true},
            {true, false}
        },
        {
            {false, true, false},
            {true, true, true}
        },
        {
            {false, true},
            {true, true},
            {false, true}
        }
    }),
    L('L', new boolean[][][] {
        {
            {true, true, true},
            {true, false, false}
        },
        {
            {true, true},
            {false, true},
            {false, true}
        },
        {
            {false, false, true},
            {true, true, true}
        },
        {
            {true, false},
            {true, false},
            {true, true}
        }
    }),
    J('J', new boolean[][][] {
        {
            {true, true, true},
            {false, false, true}
        },
        {
            {false, true},
            {false, true},
            {true, true}
        },
        {
            {true, false, false},
            {true, true, true}
        },
        {
            {true, true},
            {true, false},
            {true, false}
        }
    }),
    S('S', new boolean[][][] {
        {
            {false, true, true},
            {true, true, false}
        },
        {
            {true, false},
            {true, true},
            {false, true}
        }
    }),
    Z('Z', new boolean[][][] {
        {
            {true, true, false},
            {false, true, true}
        },
        {
            {false, true},
            {true, true},
            {true, false}
        }
    });

    private final char symbol;
    private final boolean[][][] rotations;

    FigureType(char symbol, boolean[][][] rotations) {
        this.symbol = symbol;
        this.rotations = rotations;
    }

    public char getSymbol() {
        return symbol;
    }

    public boolean[][] getRotation(int rotationIndex) {
        return rotations[Math.floorMod(rotationIndex, rotations.length)];
    }

    public int getRotationCount() {
        return rotations.length;
    }
}



package ru.danil.tetris.rmi.common;

import java.io.Serializable;

public record ActiveFigure(FigureType type, int rotationIndex, int x, int y) implements Serializable {
}



package ru.danil.tetris.rmi.server;

import ru.danil.tetris.rmi.common.GameService;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public final class ServerMain {
    private static final String BINDING_NAME = "TetrisGameService";

    private ServerMain() {
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : Registry.REGISTRY_PORT;
        Registry registry = LocateRegistry.createRegistry(port);
        GameService service = new GameServiceImpl();
        registry.rebind(BINDING_NAME, service);

        System.out.printf("RMI-СЃРµСЂРІРµСЂ РёРіСЂС‹ Tetris Р·Р°РїСѓС‰РµРЅ РЅР° РїРѕСЂС‚Сѓ %d.%n", port);
        System.out.printf("РРјСЏ СЃРµСЂРІРёСЃР°: %s%n", BINDING_NAME);
        System.out.println("РќР°Р¶РјРёС‚Рµ Ctrl+C РґР»СЏ РѕСЃС‚Р°РЅРѕРІРєРё СЃРµСЂРІРµСЂР°.");
    }
}



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



package ru.danil.tetris.rmi.server.storage;

import ru.danil.tetris.rmi.common.PlayerScore;

import java.util.List;

public record GameStatistics(
    String playerNickname,
    int gamesPlayed,
    int bestScore,
    List<PlayerScore> topPlayers
) {
    public static GameStatistics empty(String playerNickname) {
        return new GameStatistics(playerNickname, 0, 0, List.of());
    }
}



package ru.danil.tetris.rmi.server.storage;

import ru.danil.tetris.rmi.common.PlayerScore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class GameStatisticsStore {
    private static final String DEFAULT_NICKNAME = "\u0418\u0433\u0440\u043e\u043a";

    private final String jdbcUrl;

    public GameStatisticsStore(Path databaseFile) {
        try {
            Path parent = databaseFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u043f\u043e\u0434\u0433\u043e\u0442\u043e\u0432\u0438\u0442\u044c \u043a\u0430\u0442\u0430\u043b\u043e\u0433 \u0431\u0430\u0437\u044b \u0434\u0430\u043d\u043d\u044b\u0445.", exception);
        }

        jdbcUrl = "jdbc:sqlite:" + databaseFile.toAbsolutePath();
        initializeSchema();
    }

    public synchronized void saveResult(String nickname, int score, int fieldWidth, int fieldHeight) {
        String normalizedNickname = sanitizeNickname(nickname);
        try (Connection connection = openConnection()) {
            if (hasColumn(connection, "game_results", "player_id")) {
                saveResultWithPlayerId(connection, normalizedNickname, score, fieldWidth, fieldHeight);
            } else {
                saveResultWithoutPlayerId(connection, normalizedNickname, score, fieldWidth, fieldHeight);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u0441\u043e\u0445\u0440\u0430\u043d\u0438\u0442\u044c \u0440\u0435\u0437\u0443\u043b\u044c\u0442\u0430\u0442 \u0438\u0433\u0440\u044b \u0432 SQLite.", exception);
        }
    }

    public synchronized GameStatistics loadStatistics(String nickname) {
        String normalizedNickname = sanitizeNickname(nickname);
        try (Connection connection = openConnection()) {
            String nicknameExpression = resolvedNicknameExpression(connection);
            String fromClause = statisticsFromClause(connection);
            String sql = """
                SELECT COUNT(*) AS games_played,
                       COALESCE(MAX(game_results.score), 0) AS best_score
                FROM %s
                WHERE %s = ?
                """.formatted(fromClause, nicknameExpression);

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, normalizedNickname);

                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return GameStatistics.empty(normalizedNickname);
                    }

                    return new GameStatistics(
                        normalizedNickname,
                        resultSet.getInt("games_played"),
                        resultSet.getInt("best_score"),
                        loadTopPlayers(connection, 10)
                    );
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044c \u0441\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043a\u0443 \u0438\u0433\u0440 \u0438\u0437 SQLite.", exception);
        }
    }

    private List<PlayerScore> loadTopPlayers(Connection connection, int limit) throws SQLException {
        String nicknameExpression = resolvedNicknameExpression(connection);
        String fromClause = statisticsFromClause(connection);
        String sql = """
            SELECT player_nickname, best_score, field_width, field_height
            FROM (
                SELECT %s AS player_nickname,
                       game_results.score AS best_score,
                       game_results.field_width,
                       game_results.field_height,
                       ROW_NUMBER() OVER (
                           PARTITION BY %s
                           ORDER BY game_results.score DESC, game_results.played_at ASC, game_results.id ASC
                       ) AS row_num
                FROM %s
            ) ranked_results
            WHERE row_num = 1
            ORDER BY best_score DESC, player_nickname ASC
            LIMIT ?
            """.formatted(nicknameExpression, nicknameExpression, fromClause);

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);

            try (ResultSet resultSet = statement.executeQuery()) {
                List<PlayerScore> topPlayers = new ArrayList<>();
                while (resultSet.next()) {
                    topPlayers.add(new PlayerScore(
                        resultSet.getString("player_nickname"),
                        resultSet.getInt("best_score"),
                        resultSet.getInt("field_width"),
                        resultSet.getInt("field_height")
                    ));
                }
                return topPlayers;
            }
        }
    }

    private void initializeSchema() {
        String createTableSql = """
            CREATE TABLE IF NOT EXISTS game_results (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                player_nickname TEXT NOT NULL DEFAULT 'РРіСЂРѕРє',
                score INTEGER NOT NULL,
                field_width INTEGER NOT NULL DEFAULT 10,
                field_height INTEGER NOT NULL DEFAULT 20,
                played_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """;

        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(createTableSql);
            ensureNicknameColumnExists(connection);
            ensureFieldSizeColumnsExist(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u0441\u043e\u0437\u0434\u0430\u0442\u044c \u0442\u0430\u0431\u043b\u0438\u0446\u0443 \u0441\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043a\u0438 \u0432 SQLite.", exception);
        }
    }

    private void ensureNicknameColumnExists(Connection connection) throws SQLException {
        if (hasColumn(connection, "game_results", "player_nickname")) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                "ALTER TABLE game_results ADD COLUMN player_nickname TEXT NOT NULL DEFAULT '" + DEFAULT_NICKNAME + "'"
            );
        }
    }

    private void ensureFieldSizeColumnsExist(Connection connection) throws SQLException {
        if (!hasColumn(connection, "game_results", "field_width")) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE game_results ADD COLUMN field_width INTEGER NOT NULL DEFAULT 10");
            }
        }

        if (!hasColumn(connection, "game_results", "field_height")) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE game_results ADD COLUMN field_height INTEGER NOT NULL DEFAULT 20");
            }
        }
    }

    private void saveResultWithPlayerId(Connection connection, String nickname, int score, int fieldWidth, int fieldHeight) throws SQLException {
        int playerId = ensurePlayerExists(connection, nickname);
        String sql = "INSERT INTO game_results(player_id, player_nickname, score, field_width, field_height) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, playerId);
            statement.setString(2, nickname);
            statement.setInt(3, score);
            statement.setInt(4, fieldWidth);
            statement.setInt(5, fieldHeight);
            statement.executeUpdate();
        }
    }

    private void saveResultWithoutPlayerId(Connection connection, String nickname, int score, int fieldWidth, int fieldHeight) throws SQLException {
        String sql = "INSERT INTO game_results(player_nickname, score, field_width, field_height) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, nickname);
            statement.setInt(2, score);
            statement.setInt(3, fieldWidth);
            statement.setInt(4, fieldHeight);
            statement.executeUpdate();
        }
    }

    private int ensurePlayerExists(Connection connection, String nickname) throws SQLException {
        String selectSql = "SELECT id FROM players WHERE name = ?";
        try (PreparedStatement statement = connection.prepareStatement(selectSql)) {
            statement.setString(1, nickname);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("id");
                }
            }
        }

        String insertSql = "INSERT INTO players(name) VALUES (?)";
        try (PreparedStatement statement = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, nickname);
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
            }
        }

        throw new SQLException("Failed to create player row.");
    }

    private boolean hasColumn(Connection connection, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet columns = metaData.getColumns(null, null, tableName, columnName)) {
            return columns.next();
        }
    }

    private String resolvedNicknameExpression(Connection connection) throws SQLException {
        if (hasColumn(connection, "game_results", "player_id")) {
            return "COALESCE(NULLIF(game_results.player_nickname, ''), players.name, '" + DEFAULT_NICKNAME + "')";
        }
        return "COALESCE(NULLIF(game_results.player_nickname, ''), '" + DEFAULT_NICKNAME + "')";
    }

    private String statisticsFromClause(Connection connection) throws SQLException {
        if (hasColumn(connection, "game_results", "player_id")) {
            return "game_results LEFT JOIN players ON players.id = game_results.player_id";
        }
        return "game_results";
    }

    private String sanitizeNickname(String nickname) {
        if (nickname == null) {
            return DEFAULT_NICKNAME;
        }

        String normalized = nickname.trim();
        return normalized.isEmpty() ? DEFAULT_NICKNAME : normalized;
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }
}



package ru.danil.tetris.rmi.client;

import ru.danil.tetris.rmi.common.GameService;
import ru.danil.tetris.rmi.common.GameSnapshot;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;

public final class TetrisSwingClient {
    private static final String BINDING_NAME = "TetrisGameService";
    private static final String MENU_CARD = "menu";
    private static final String SETTINGS_CARD = "settings";
    private static final String GAME_CARD = "game";
    private static final int CELL_SIZE = 28;
    private static final int FALL_DELAY_MS = 550;
    private static final int MIN_WIDTH = 4;
    private static final int MIN_HEIGHT = 6;
    private static final int MAX_WIDTH = 25;
    private static final int MAX_HEIGHT = 25;
    private static final int MAX_NICKNAME_LENGTH = 20;
    private static final int GAME_SIDE_PANEL_WIDTH = 280;

    private static final Color BACKGROUND = new Color(5, 14, 8);
    private static final Color PANEL_BG = new Color(8, 24, 12);
    private static final Color PANEL_BG_ALT = new Color(10, 32, 16);
    private static final Color BORDER = new Color(50, 255, 120);
    private static final Color TEXT = new Color(165, 255, 190);
    private static final Color MUTED_TEXT = new Color(105, 180, 120);
    private static final Color BOARD_BG = new Color(3, 10, 6);
    private static final Color EMPTY_CELL = new Color(9, 28, 14);
    private static final Color LOCKED_CELL = new Color(28, 180, 82);
    private static final Color ACTIVE_CELL = new Color(130, 255, 170);
    private static final Color GLOW = new Color(34, 100, 52, 120);

    private final GameService service;
    private final JFrame frame;
    private final CardLayout cardLayout;
    private final JPanel rootPanel;
    private final BoardPanel boardPanel;
    private final JLabel scoreLabel;
    private final JLabel recordLabel;
    private final JLabel gamesLabel;
    private final JLabel menuPlayerLabel;
    private final JLabel menuRecordLabel;
    private final JLabel menuGamesLabel;
    private final JLabel menuTopLabel;
    private final JLabel placedLabel;
    private final JLabel holesLabel;
    private final JLabel gameOverLabel;
    private final JTextArea settingsValidationArea;
    private final JTextField nicknameField;
    private final JTextField widthField;
    private final JTextField heightField;
    private final JTextArea helpArea;
    private final JTextArea leaderboardArea;
    private final Timer fallTimer;
    private JButton saveSettingsButton;

    private String sessionId;
    private GameSnapshot snapshot;

    private TetrisSwingClient(GameService service) {
        this.service = service;
        this.frame = new JFrame("Tetris RMI");
        this.cardLayout = new CardLayout();
        this.rootPanel = new JPanel(cardLayout);
        this.boardPanel = new BoardPanel();
        this.scoreLabel = createInfoLabel();
        this.recordLabel = createInfoLabel();
        this.gamesLabel = createInfoLabel();
        this.menuPlayerLabel = createInfoLabel();
        this.menuRecordLabel = createInfoLabel();
        this.menuGamesLabel = createInfoLabel();
        this.menuTopLabel = createInfoLabel();
        this.placedLabel = createInfoLabel();
        this.holesLabel = createInfoLabel();
        this.gameOverLabel = createInfoLabel();
        this.settingsValidationArea = createValidationArea();
        this.nicknameField = createTextField("\u0418\u0433\u0440\u043e\u043a");
        this.widthField = createTextField("10");
        this.heightField = createTextField("20");
        this.helpArea = createHelpArea();
        this.leaderboardArea = createLeaderboardArea();
        this.fallTimer = new Timer(FALL_DELAY_MS, this::handleTick);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                String host = args.length > 0 ? args[0] : "localhost";
                int port = args.length > 1 ? Integer.parseInt(args[1]) : Registry.REGISTRY_PORT;

                Registry registry = LocateRegistry.getRegistry(host, port);
                GameService service = (GameService) registry.lookup(BINDING_NAME);

                TetrisSwingClient client = new TetrisSwingClient(service);
                client.initUi();
            } catch (Exception exception) {
                JOptionPane.showMessageDialog(
                    null,
                    "\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u043f\u043e\u0434\u043a\u043b\u044e\u0447\u0438\u0442\u044c\u0441\u044f \u043a \u0441\u0435\u0440\u0432\u0435\u0440\u0443 RMI:\n" + exception.getMessage(),
                    "\u041e\u0448\u0438\u0431\u043a\u0430 \u043f\u043e\u0434\u043a\u043b\u044e\u0447\u0435\u043d\u0438\u044f",
                    JOptionPane.ERROR_MESSAGE
                );
            }
        });
    }

    private void initUi() {
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.getContentPane().setBackground(BACKGROUND);

        rootPanel.setBackground(BACKGROUND);
        rootPanel.add(createMenuPanel(), MENU_CARD);
        rootPanel.add(createSettingsPanel(), SETTINGS_CARD);
        rootPanel.add(createGamePanel(), GAME_CARD);

        frame.add(rootPanel, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        installKeyBindings();
        sessionId = executeRemoteCall(() -> service.createSession(getNickname()));
        showCard(MENU_CARD);
        executeRemote(() -> service.updatePlayerNickname(sessionId, getNickname()), false);
    }

    private JPanel createMenuPanel() {
        JPanel panel = baseScreenPanel();

        JPanel content = new JPanel(new BorderLayout(24, 0));
        content.setOpaque(false);

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("TETRIS RMI");
        title.setFont(new Font(Font.MONOSPACED, Font.BOLD, 40));
        title.setForeground(BORDER);
        title.setAlignmentX(JComponent.CENTER_ALIGNMENT);

        JLabel subtitle = new JLabel("by \u041c\u0438\u0445\u0430\u043b\u044c\u0447\u0443\u043a \u0414.\u0410.");
        subtitle.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 16));
        subtitle.setForeground(MUTED_TEXT);
        subtitle.setAlignmentX(JComponent.CENTER_ALIGNMENT);

        JLabel deco = new JLabel("01001000 01000101 01011000");
        deco.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        deco.setForeground(new Color(70, 120, 80));
        deco.setAlignmentX(JComponent.CENTER_ALIGNMENT);

        JButton startButton = createPrimaryButton("\u041d\u0430\u0447\u0430\u0442\u044c \u0438\u0433\u0440\u0443");
        startButton.addActionListener(event -> startGame());

        JButton settingsButton = createPrimaryButton("\u0418\u0437\u043c\u0435\u043d\u0438\u0442\u044c \u043d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438 \u0438\u0433\u0440\u044b");
        settingsButton.addActionListener(event -> showCard(SETTINGS_CARD));

        center.add(Box.createVerticalGlue());
        center.add(title);
        center.add(Box.createVerticalStrut(10));
        center.add(subtitle);
        center.add(Box.createVerticalStrut(10));
        center.add(deco);
        center.add(Box.createVerticalStrut(34));
        center.add(startButton);
        center.add(Box.createVerticalStrut(14));
        center.add(settingsButton);
        center.add(Box.createVerticalGlue());

        JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.add(Box.createVerticalGlue());
        wrapper.add(center);
        wrapper.add(Box.createVerticalGlue());

        content.add(wrapper, BorderLayout.CENTER);
        content.add(createMenuStatsPanel(), BorderLayout.EAST);
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createMenuStatsPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setPreferredSize(new Dimension(360, 420));

        JPanel playerCard = createCardPanel("\u0418\u0433\u0440\u043e\u043a");
        playerCard.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        playerCard.setMaximumSize(new Dimension(360, 150));

        JPanel playerStats = new JPanel();
        playerStats.setOpaque(false);
        playerStats.setLayout(new BoxLayout(playerStats, BoxLayout.Y_AXIS));
        playerStats.add(menuPlayerLabel);
        playerStats.add(Box.createVerticalStrut(8));
        playerStats.add(menuRecordLabel);
        playerStats.add(Box.createVerticalStrut(6));
        playerStats.add(menuGamesLabel);
        playerCard.add(playerStats, BorderLayout.CENTER);

        JPanel topCard = createCardPanel("\u0422\u043e\u043f-10 \u0438\u0433\u0440\u043e\u043a\u043e\u0432");
        topCard.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        topCard.setMaximumSize(new Dimension(360, 250));
        topCard.add(leaderboardArea, BorderLayout.CENTER);

        panel.add(playerCard);
        panel.add(Box.createVerticalStrut(16));
        panel.add(topCard);
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private JPanel createSettingsPanel() {
        JPanel panel = baseScreenPanel();

        JPanel card = createCardPanel("\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438 \u0438\u0433\u0440\u044b");
        card.setPreferredSize(new Dimension(640, 440));
        card.setMaximumSize(new Dimension(640, 440));

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JPanel nicknameRow = createSettingRow("\u041d\u0438\u043a \u0438\u0433\u0440\u043e\u043a\u0430", nicknameField);
        JPanel widthRow = createSettingRow("\u0428\u0438\u0440\u0438\u043d\u0430 \u043f\u043e\u043b\u044f", widthField);
        JPanel heightRow = createSettingRow("\u0412\u044b\u0441\u043e\u0442\u0430 \u043f\u043e\u043b\u044f", heightField);

        saveSettingsButton = createPrimaryButton("\u0421\u043e\u0445\u0440\u0430\u043d\u0438\u0442\u044c \u0438 \u0432 \u043c\u0435\u043d\u044e");
        saveSettingsButton.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        saveSettingsButton.setMaximumSize(saveSettingsButton.getPreferredSize());
        saveSettingsButton.addActionListener(event -> {
            if (validateSettingsInputs()) {
                executeRemote(() -> service.updatePlayerNickname(sessionId, getNickname()), false);
                showCard(MENU_CARD);
            }
        });

        JTextArea note = new JTextArea(
            "\u0417\u0434\u0435\u0441\u044c \u043c\u043e\u0436\u043d\u043e \u0437\u0430\u0434\u0430\u0442\u044c \u043d\u0438\u043a \u0438 \u0440\u0430\u0437\u043c\u0435\u0440 \u043f\u043e\u043b\u044f. \u041f\u043e\u0441\u043b\u0435 \u0441\u043e\u0445\u0440\u0430\u043d\u0435\u043d\u0438\u044f \u0432\u0435\u0440\u043d\u0438\u0442\u0435\u0441\u044c \u0432 \u043c\u0435\u043d\u044e \u0438 \u043d\u0430\u0447\u043d\u0438\u0442\u0435 \u043d\u043e\u0432\u0443\u044e \u0438\u0433\u0440\u0443."
        );
        note.setEditable(false);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        note.setOpaque(false);
        note.setForeground(MUTED_TEXT);
        note.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        note.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(28, 86, 42), 1),
            BorderFactory.createEmptyBorder(12, 12, 12, 12)
        ));
        note.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        note.setMaximumSize(new Dimension(560, 76));

        settingsValidationArea.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        settingsValidationArea.setForeground(new Color(255, 120, 120));
        settingsValidationArea.setText(" ");

        content.add(nicknameRow);
        content.add(Box.createVerticalStrut(14));
        content.add(widthRow);
        content.add(Box.createVerticalStrut(14));
        content.add(heightRow);
        content.add(Box.createVerticalStrut(22));
        content.add(saveSettingsButton);
        content.add(Box.createVerticalStrut(10));
        content.add(settingsValidationArea);
        content.add(Box.createVerticalStrut(18));
        content.add(note);

        card.add(content, BorderLayout.CENTER);

        JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        card.setAlignmentX(JComponent.CENTER_ALIGNMENT);
        wrapper.add(Box.createVerticalGlue());
        wrapper.add(card);
        wrapper.add(Box.createVerticalGlue());

        panel.add(wrapper, BorderLayout.CENTER);
        installSettingsValidation();
        validateSettingsInputs();
        return panel;
    }

    private JPanel createSettingRow(String labelText, JTextField field) {
        JPanel row = new JPanel(new BorderLayout(18, 0));
        row.setOpaque(false);
        row.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(560, 46));

        JLabel label = createFieldLabel(labelText);
        label.setPreferredSize(new Dimension(180, 46));

        field.setPreferredSize(new Dimension(220, 46));
        field.setMaximumSize(new Dimension(220, 46));

        row.add(label, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        return row;
    }

    private JPanel createGamePanel() {
        JPanel panel = new JPanel(new BorderLayout(16, 16));
        panel.setPreferredSize(new Dimension(1060, 760));
        panel.setBackground(BACKGROUND);
        panel.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

        boardPanel.setBackground(BOARD_BG);
        boardPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 2),
            BorderFactory.createEmptyBorder(6, 6, 6, 6)
        ));

        JPanel side = new JPanel();
        side.setOpaque(false);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.add(createStatsPanel());
        side.add(Box.createVerticalStrut(14));
        side.add(createMenuButtonPanel());
        side.add(Box.createVerticalStrut(14));
        side.add(createHelpPanel());

        panel.add(boardPanel, BorderLayout.CENTER);
        panel.add(side, BorderLayout.EAST);
        return panel;
    }

    private JPanel createStatsPanel() {
        JPanel panel = createCardPanel("\u0421\u043e\u0441\u0442\u043e\u044f\u043d\u0438\u0435");
        panel.setPreferredSize(new Dimension(GAME_SIDE_PANEL_WIDTH, 178));
        panel.setMinimumSize(new Dimension(GAME_SIDE_PANEL_WIDTH, 178));
        panel.setMaximumSize(new Dimension(GAME_SIDE_PANEL_WIDTH, 178));
        JPanel stats = new JPanel();
        stats.setOpaque(false);
        stats.setLayout(new BoxLayout(stats, BoxLayout.Y_AXIS));
        stats.add(scoreLabel);
        stats.add(Box.createVerticalStrut(6));
        stats.add(placedLabel);
        stats.add(Box.createVerticalStrut(6));
        stats.add(holesLabel);
        stats.add(Box.createVerticalStrut(6));
        stats.add(gameOverLabel);
        panel.add(stats, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createHelpPanel() {
        JPanel panel = createCardPanel("\u041a\u043b\u0430\u0432\u0438\u0448\u0438");
        panel.setPreferredSize(new Dimension(GAME_SIDE_PANEL_WIDTH, 165));
        panel.setMinimumSize(new Dimension(GAME_SIDE_PANEL_WIDTH, 165));
        panel.setMaximumSize(new Dimension(GAME_SIDE_PANEL_WIDTH, 165));
        helpArea.setText("""
            Left / Right  - \u0434\u0432\u0438\u0436\u0435\u043d\u0438\u0435
            Up            - \u043f\u043e\u0432\u043e\u0440\u043e\u0442
            Down          - \u0432\u043d\u0438\u0437
            Space         - \u0441\u0431\u0440\u043e\u0441\u0438\u0442\u044c \u0432\u043d\u0438\u0437
            Enter         - \u043d\u0430\u0447\u0430\u0442\u044c \u0438\u0433\u0440\u0443 \u0438\u0437 \u043c\u0435\u043d\u044e
            Esc           - \u0433\u043b\u0430\u0432\u043d\u043e\u0435 \u043c\u0435\u043d\u044e
            """);
        panel.add(helpArea, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createMenuButtonPanel() {
        JPanel panel = createCardPanel("\u041d\u0430\u0432\u0438\u0433\u0430\u0446\u0438\u044f");
        panel.setPreferredSize(new Dimension(GAME_SIDE_PANEL_WIDTH, 108));
        panel.setMinimumSize(new Dimension(GAME_SIDE_PANEL_WIDTH, 108));
        panel.setMaximumSize(new Dimension(GAME_SIDE_PANEL_WIDTH, 108));
        JButton menuButton = createPrimaryButton("\u0413\u043b\u0430\u0432\u043d\u043e\u0435 \u043c\u0435\u043d\u044e");
        menuButton.addActionListener(event -> {
            stopTimer();
            showCard(MENU_CARD);
        });
        panel.add(menuButton, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createCardPanel(String title) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(PANEL_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1),
            BorderFactory.createEmptyBorder(14, 14, 14, 14)
        ));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 16));
        titleLabel.setForeground(BORDER);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
        panel.add(titleLabel, BorderLayout.NORTH);
        return panel;
    }

    private JPanel baseScreenPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setPreferredSize(new Dimension(980, 720));
        panel.setBackground(BACKGROUND);
        panel.setBorder(BorderFactory.createEmptyBorder(42, 52, 42, 52));
        return panel;
    }

    private JButton createPrimaryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font(Font.MONOSPACED, Font.BOLD, 16));
        button.setForeground(BACKGROUND);
        button.setBackground(BORDER);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(160, 255, 190), 1),
            BorderFactory.createEmptyBorder(10, 18, 10, 18)
        ));
        button.setAlignmentX(JComponent.CENTER_ALIGNMENT);
        return button;
    }

    private JLabel createFieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT);
        label.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        return label;
    }

    private JTextField createTextField(String value) {
        JTextField field = new JTextField(value, 5);
        field.setBackground(PANEL_BG_ALT);
        field.setForeground(TEXT);
        field.setCaretColor(BORDER);
        field.setFont(new Font(Font.MONOSPACED, Font.BOLD, 15));
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(55, 190, 95), 1),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        return field;
    }

    private JTextArea createHelpArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setForeground(MUTED_TEXT);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        return area;
    }

    private JTextArea createLeaderboardArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setOpaque(false);
        area.setLineWrap(false);
        area.setWrapStyleWord(false);
        area.setForeground(TEXT);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(28, 86, 42), 1),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        area.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        area.setMaximumSize(new Dimension(330, 220));
        return area;
    }

    private JTextArea createValidationArea() {
        JTextArea area = new JTextArea(" ");
        area.setEditable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setForeground(new Color(255, 120, 120));
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        area.setMaximumSize(new Dimension(560, 54));
        return area;
    }

    private JLabel createSectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(BORDER);
        label.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        label.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return label;
    }

    private void installSettingsValidation() {
        DocumentListener listener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                validateSettingsInputs();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                validateSettingsInputs();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                validateSettingsInputs();
            }
        };

        nicknameField.getDocument().addDocumentListener(listener);
        widthField.getDocument().addDocumentListener(listener);
        heightField.getDocument().addDocumentListener(listener);
    }

    private boolean validateSettingsInputs() {
        String nicknameText = getNickname();
        String widthText = widthField.getText().trim();
        String heightText = heightField.getText().trim();
        settingsValidationArea.setForeground(new Color(255, 120, 120));

        if (nicknameText.isEmpty()) {
            settingsValidationArea.setText("\u0412\u0432\u0435\u0434\u0438\u0442\u0435 \u043d\u0438\u043a \u0438\u0433\u0440\u043e\u043a\u0430.");
            saveSettingsButton.setEnabled(false);
            return false;
        }

        if (nicknameText.length() > MAX_NICKNAME_LENGTH) {
            settingsValidationArea.setText("\u041d\u0438\u043a \u0434\u043e\u043b\u0436\u0435\u043d \u0431\u044b\u0442\u044c \u0434\u043e " + MAX_NICKNAME_LENGTH + " \u0441\u0438\u043c\u0432\u043e\u043b\u043e\u0432.");
            saveSettingsButton.setEnabled(false);
            return false;
        }

        if (widthText.isEmpty() || heightText.isEmpty()) {
            settingsValidationArea.setText("\u0412\u0432\u0435\u0434\u0438\u0442\u0435 \u043d\u0438\u043a, \u0448\u0438\u0440\u0438\u043d\u0443 \u0438 \u0432\u044b\u0441\u043e\u0442\u0443 \u043f\u043e\u043b\u044f.");
            saveSettingsButton.setEnabled(false);
            return false;
        }

        int width;
        int height;
        try {
            width = Integer.parseInt(widthText);
            height = Integer.parseInt(heightText);
        } catch (NumberFormatException exception) {
            settingsValidationArea.setText("\u0414\u043e\u043f\u0443\u0441\u0442\u0438\u043c\u044b \u0442\u043e\u043b\u044c\u043a\u043e \u0446\u0435\u043b\u044b\u0435 \u0447\u0438\u0441\u043b\u0430.");
            saveSettingsButton.setEnabled(false);
            return false;
        }

        if (width < MIN_WIDTH || height < MIN_HEIGHT) {
            settingsValidationArea.setText("\u041c\u0438\u043d\u0438\u043c\u0443\u043c: \u0448\u0438\u0440\u0438\u043d\u0430 4, \u0432\u044b\u0441\u043e\u0442\u0430 6.");
            saveSettingsButton.setEnabled(false);
            return false;
        }

        if (width > MAX_WIDTH || height > MAX_HEIGHT) {
            settingsValidationArea.setText("\u041c\u0430\u043a\u0441\u0438\u043c\u0443\u043c: \u0448\u0438\u0440\u0438\u043d\u0430 25, \u0432\u044b\u0441\u043e\u0442\u0430 25.");
            saveSettingsButton.setEnabled(false);
            return false;
        }

        settingsValidationArea.setText("\u0417\u043d\u0430\u0447\u0435\u043d\u0438\u044f \u043a\u043e\u0440\u0440\u0435\u043a\u0442\u043d\u044b.");
        settingsValidationArea.setForeground(new Color(110, 220, 140));
        saveSettingsButton.setEnabled(true);
        return true;
    }

    private JLabel createInfoLabel() {
        JLabel label = new JLabel(" ");
        label.setVerticalAlignment(SwingConstants.TOP);
        label.setForeground(TEXT);
        label.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        label.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        label.setPreferredSize(new Dimension(GAME_SIDE_PANEL_WIDTH - 32, 22));
        label.setMinimumSize(new Dimension(GAME_SIDE_PANEL_WIDTH - 32, 22));
        label.setMaximumSize(new Dimension(GAME_SIDE_PANEL_WIDTH - 32, 22));
        return label;
    }

    private void installKeyBindings() {
        bindKey("LEFT", "moveLeft", event -> executeRemote(() -> service.moveLeft(sessionId), false));
        bindKey("RIGHT", "moveRight", event -> executeRemote(() -> service.moveRight(sessionId), false));
        bindKey("UP", "rotate", event -> executeRemote(() -> service.rotate(sessionId), false));
        bindKey("DOWN", "moveDown", event -> executeRemote(() -> service.moveDown(sessionId), false));
        bindKey("SPACE", "drop", event -> executeRemote(() -> service.dropFigure(sessionId), false));
        bindKey("ENTER", "startFromMenu", event -> {
            if (isCurrentCard(MENU_CARD)) {
                startGame();
            }
        });
        bindKey("ESCAPE", "backToMenu", event -> {
            stopTimer();
            showCard(MENU_CARD);
        });
    }

    private void bindKey(String key, String actionName, java.awt.event.ActionListener listener) {
        JComponent root = frame.getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), actionName);
        root.getActionMap().put(actionName, new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                listener.actionPerformed(event);
            }
        });
    }

    private void startGame() {
        int width;
        int height;
        try {
            width = Integer.parseInt(widthField.getText().trim());
            height = Integer.parseInt(heightField.getText().trim());
        } catch (NumberFormatException exception) {
            JOptionPane.showMessageDialog(
                frame,
                "\u0420\u0430\u0437\u043c\u0435\u0440\u044b \u043f\u043e\u043b\u044f \u0434\u043e\u043b\u0436\u043d\u044b \u0431\u044b\u0442\u044c \u0446\u0435\u043b\u044b\u043c\u0438 \u0447\u0438\u0441\u043b\u0430\u043c\u0438.",
                "\u041d\u0435\u043a\u043e\u0440\u0440\u0435\u043a\u0442\u043d\u044b\u0439 \u0432\u0432\u043e\u0434",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        if (!validateSettingsInputs()) {
            showCard(SETTINGS_CARD);
            return;
        }

        int fieldWidth = width;
        int fieldHeight = height;
        executeRemote(() -> {
            service.updatePlayerNickname(sessionId, getNickname());
            return service.startNewGame(sessionId, fieldWidth, fieldHeight);
        }, true);
        if (snapshot != null) {
            showCard(GAME_CARD);
            restartTimer();
        }
    }

    private void handleTick(ActionEvent event) {
        if (!isCurrentCard(GAME_CARD) || snapshot == null || snapshot.gameOver()) {
            stopTimer();
            return;
        }
        executeRemote(() -> service.tick(sessionId), false);
    }

    private void executeRemote(RemoteAction action, boolean keepTimerRunning) {
        try {
            snapshot = action.execute();
            updateUi();
            if (snapshot.gameOver()) {
                stopTimer();
            } else if (keepTimerRunning && isCurrentCard(GAME_CARD)) {
                restartTimer();
            }
        } catch (Exception exception) {
            stopTimer();
            JOptionPane.showMessageDialog(
                frame,
                "\u041e\u0448\u0438\u0431\u043a\u0430 RMI:\n" + exception.getMessage(),
                "\u041e\u0448\u0438\u0431\u043a\u0430",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private <T> T executeRemoteCall(RemoteValueAction<T> action) {
        try {
            return action.execute();
        } catch (Exception exception) {
            stopTimer();
            JOptionPane.showMessageDialog(
                frame,
                "\u041e\u0448\u0438\u0431\u043a\u0430 RMI:\n" + exception.getMessage(),
                "\u041e\u0448\u0438\u0431\u043a\u0430",
                JOptionPane.ERROR_MESSAGE
            );
            throw new IllegalStateException("Remote call failed", exception);
        }
    }

    private void updateUi() {
        if (snapshot == null) {
            return;
        }

        boardPanel.setSnapshot(snapshot);
        if (!snapshot.playerNickname().equals(getNickname())) {
            nicknameField.setText(snapshot.playerNickname());
        }
        scoreLabel.setText("\u041e\u0447\u043a\u0438: " + snapshot.score() + " | \u0417\u0430\u043d\u044f\u0442\u043e: " + snapshot.occupiedCells());
        menuPlayerLabel.setText("\u0418\u0433\u0440\u043e\u043a: " + snapshot.playerNickname());
        menuRecordLabel.setText("\u0420\u0435\u043a\u043e\u0440\u0434: " + snapshot.bestScore());
        menuGamesLabel.setText("\u0421\u044b\u0433\u0440\u0430\u043d\u043e \u043f\u0430\u0440\u0442\u0438\u0439: " + snapshot.gamesPlayed());
        menuTopLabel.setText(" ");
        leaderboardArea.setText(formatTopPlayers());
        placedLabel.setText("\u0420\u0430\u0437\u043c\u0435\u0449\u0435\u043d\u043e \u0444\u0438\u0433\u0443\u0440: " + snapshot.placedFigures());
        holesLabel.setText("\u041f\u0443\u0441\u0442\u043e\u0442\u044b: " + snapshot.holes());
        gameOverLabel.setText(snapshot.gameOver() ? "\u041a\u041e\u041d\u0415\u0426 \u0418\u0413\u0420\u042b" : " ");

        frame.pack();
        frame.repaint();
    }

    private String formatTopPlayers() {
        if (snapshot == null || snapshot.topPlayers().isEmpty()) {
            return "\u041f\u043e\u043a\u0430 \u043d\u0435\u0442 \u0440\u0435\u0437\u0443\u043b\u044c\u0442\u0430\u0442\u043e\u0432.";
        }

        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < snapshot.topPlayers().size(); index++) {
            if (index > 0) {
                builder.append('\n');
            }

            var player = snapshot.topPlayers().get(index);
            builder
                .append(index + 1)
                .append(". ")
                .append(player.nickname())
                .append(" - ")
                .append(player.bestScore())
                .append(" - ")
                .append(player.fieldWidth())
                .append("x")
                .append(player.fieldHeight());
        }
        return builder.toString();
    }

    private void restartTimer() {
        fallTimer.restart();
    }

    private void stopTimer() {
        fallTimer.stop();
    }

    private void showCard(String cardName) {
        cardLayout.show(rootPanel, cardName);
        rootPanel.putClientProperty("activeCard", cardName);
    }

    private boolean isCurrentCard(String cardName) {
        Object value = rootPanel.getClientProperty("activeCard");
        return cardName.equals(value);
    }

    private String getNickname() {
        return nicknameField.getText().trim();
    }

    @FunctionalInterface
    private interface RemoteAction {
        GameSnapshot execute() throws Exception;
    }

    @FunctionalInterface
    private interface RemoteValueAction<T> {
        T execute() throws Exception;
    }

    private static final class BoardPanel extends JPanel {
        private GameSnapshot snapshot;

        private BoardPanel() {
            setPreferredSize(new Dimension(10 * CELL_SIZE + 12, 20 * CELL_SIZE + 12));
        }

        private void setSnapshot(GameSnapshot snapshot) {
            this.snapshot = snapshot;
            setPreferredSize(new Dimension(snapshot.width() * CELL_SIZE + 12, snapshot.height() * CELL_SIZE + 12));
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);

            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(BOARD_BG);
            g2.fillRect(0, 0, getWidth(), getHeight());

            if (snapshot != null) {
                List<String> rows = snapshot.boardRows();
                for (int row = 0; row < rows.size(); row++) {
                    String line = rows.get(row);
                    for (int col = 0; col < line.length(); col++) {
                        int x = 6 + col * CELL_SIZE;
                        int y = 6 + row * CELL_SIZE;
                        char cell = line.charAt(col);

                        g2.setColor(resolveGlow(cell));
                        g2.fillRoundRect(x + 2, y + 2, CELL_SIZE - 6, CELL_SIZE - 6, 10, 10);

                        g2.setColor(resolveColor(cell));
                        g2.fillRoundRect(x, y, CELL_SIZE - 4, CELL_SIZE - 4, 10, 10);

                        g2.setColor(new Color(12, 50, 22));
                        g2.setStroke(new BasicStroke(1f));
                        g2.drawRoundRect(x, y, CELL_SIZE - 4, CELL_SIZE - 4, 10, 10);
                    }
                }
            }

            g2.dispose();
        }

        private Color resolveColor(char cell) {
            return switch (cell) {
                case '#' -> LOCKED_CELL;
                case '*' -> ACTIVE_CELL;
                default -> EMPTY_CELL;
            };
        }

        private Color resolveGlow(char cell) {
            return switch (cell) {
                case '#', '*' -> GLOW;
                default -> new Color(0, 0, 0, 0);
            };
        }
    }
}


