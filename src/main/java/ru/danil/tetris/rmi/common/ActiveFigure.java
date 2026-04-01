package ru.danil.tetris.rmi.common;

import java.io.Serializable;

public record ActiveFigure(FigureType type, int rotationIndex, int x, int y) implements Serializable {
}
