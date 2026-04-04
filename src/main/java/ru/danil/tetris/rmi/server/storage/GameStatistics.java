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
