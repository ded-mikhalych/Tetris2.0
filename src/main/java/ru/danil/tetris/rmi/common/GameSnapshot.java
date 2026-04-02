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
    int bestScore,
    int gamesPlayed,
    String statusMessage
) implements Serializable {
}
