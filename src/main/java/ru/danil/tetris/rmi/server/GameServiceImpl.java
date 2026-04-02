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
import java.util.Random;

public class GameServiceImpl extends UnicastRemoteObject implements GameService {
    private static final int DEFAULT_WIDTH = 10;
    private static final int DEFAULT_HEIGHT = 20;
    private static final int MAX_WIDTH = 25;
    private static final int MAX_HEIGHT = 25;

    private final Random random;
    private final GameStatisticsStore statisticsStore;
    private boolean[][] board;
    private ActiveFigure activeFigure;
    private boolean gameOver;
    private boolean resultSaved;
    private int placedFigures;
    private String statusMessage;

    public GameServiceImpl() throws RemoteException {
        super();
        random = new Random();
        statisticsStore = new GameStatisticsStore(Path.of("data", "tetris.db"));
        initialize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    @Override
    public synchronized GameSnapshot startNewGame(int width, int height) {
        initialize(width, height);
        return buildSnapshot();
    }

    @Override
    public synchronized GameSnapshot getState() {
        return buildSnapshot();
    }

    @Override
    public synchronized GameSnapshot moveLeft() {
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
            statusMessage = "Фигура сдвинута влево.";
        } else {
            statusMessage = "Сдвиг влево невозможен.";
        }
        return buildSnapshot();
    }

    @Override
    public synchronized GameSnapshot moveRight() {
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
            statusMessage = "Фигура сдвинута вправо.";
        } else {
            statusMessage = "Сдвиг вправо невозможен.";
        }
        return buildSnapshot();
    }

    @Override
    public synchronized GameSnapshot rotate() {
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
            statusMessage = "Фигура повернута.";
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
                statusMessage = "Фигура повернута со сдвигом.";
                return buildSnapshot();
            }
        }

        statusMessage = "Поворот невозможен.";
        return buildSnapshot();
    }

    @Override
    public synchronized GameSnapshot moveDown() {
        if (!ensureGameInProgress()) {
            return buildSnapshot();
        }
        return advanceDown(true);
    }

    @Override
    public synchronized GameSnapshot dropFigure() {
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
                if (!gameOver && statusMessage.startsWith("Фигура зафиксирована")) {
                    statusMessage = "Фигура мгновенно размещена. Сервер выдал следующую.";
                }
                return buildSnapshot();
            }
            activeFigure = next;
        }
        return buildSnapshot();
    }

    @Override
    public synchronized GameSnapshot tick() {
        if (!ensureGameInProgress()) {
            return buildSnapshot();
        }
        return advanceDown(false);
    }

    @Override
    public synchronized GameSnapshot finishGame() {
        finishCurrentGame("Игра завершена по команде клиента.");
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
            statusMessage = manualMove ? "Фигура опущена на одну клетку." : "Фигура падает.";
            return buildSnapshot();
        }

        lockCurrentFigure();
        return buildSnapshot();
    }

    private void initialize(int width, int height) {
        if (width < 4 || height < 6) {
            throw new IllegalArgumentException("Минимальный размер поля: 4x6.");
        }
        if (width > MAX_WIDTH || height > MAX_HEIGHT) {
            throw new IllegalArgumentException("Максимальный размер поля: 25x25.");
        }

        board = new boolean[height][width];
        placedFigures = 0;
        gameOver = false;
        resultSaved = false;
        statusMessage = "Новая игра началась.";
        spawnNextFigure();
    }

    private void spawnNextFigure() {
        FigureType[] values = FigureType.values();
        FigureType type = values[random.nextInt(values.length)];
        boolean[][] shape = type.getRotation(0);
        int startX = Math.max(0, (board[0].length - shape[0].length) / 2);
        activeFigure = new ActiveFigure(type, 0, startX, 0);

        if (!canPlace(activeFigure)) {
            finishCurrentGame("Новая фигура не помещается. Игра завершена.");
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
            finishCurrentGame("Поле заполнено полностью. Игра завершена.");
            return;
        }

        spawnNextFigure();
        if (!gameOver) {
            statusMessage = "Фигура зафиксирована. Сервер выдал следующую.";
        }
    }

    private void finishCurrentGame(String message) {
        gameOver = true;
        statusMessage = message;
        if (!resultSaved) {
            statisticsStore.saveResult(calculateScore());
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
        GameStatistics statistics = statisticsStore.loadStatistics();
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
            statistics.bestScore(),
            statistics.gamesPlayed(),
            statusMessage
        );
    }

    private boolean ensureGameInProgress() {
        if (gameOver) {
            statusMessage = "Игра уже завершена. Начните новую партию.";
            return false;
        }
        return true;
    }
}
