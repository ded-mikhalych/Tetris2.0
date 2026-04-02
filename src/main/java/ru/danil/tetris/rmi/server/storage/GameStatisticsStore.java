package ru.danil.tetris.rmi.server.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class GameStatisticsStore {
    private final String jdbcUrl;

    public GameStatisticsStore(Path databaseFile) {
        try {
            Path parent = databaseFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Не удалось подготовить каталог базы данных.", exception);
        }

        jdbcUrl = "jdbc:sqlite:" + databaseFile.toAbsolutePath();
        initializeSchema();
    }

    public synchronized void saveResult(int score) {
        String sql = "INSERT INTO game_results(score) VALUES (?)";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, score);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Не удалось сохранить результат игры в SQLite.", exception);
        }
    }

    public synchronized GameStatistics loadStatistics() {
        String sql = "SELECT COUNT(*) AS games_played, COALESCE(MAX(score), 0) AS best_score FROM game_results";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return GameStatistics.empty();
            }

            return new GameStatistics(
                resultSet.getInt("games_played"),
                resultSet.getInt("best_score")
            );
        } catch (SQLException exception) {
            throw new IllegalStateException("Не удалось загрузить статистику игр из SQLite.", exception);
        }
    }

    private void initializeSchema() {
        String sql = """
            CREATE TABLE IF NOT EXISTS game_results (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                score INTEGER NOT NULL,
                played_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """;

        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException exception) {
            throw new IllegalStateException("Не удалось создать таблицу статистики в SQLite.", exception);
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }
}
