package ru.danil.tetris.rmi.common;

import java.io.Serializable;

public record PlayerScore(String nickname, int bestScore, int fieldWidth, int fieldHeight) implements Serializable {
}
