package ru.danil.tetris.rmi.server.storage;

public record GameStatistics(int gamesPlayed, int bestScore) {
    public static GameStatistics empty() {
        return new GameStatistics(0, 0);
    }
}
