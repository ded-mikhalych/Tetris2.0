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
                player_nickname TEXT NOT NULL DEFAULT 'Игрок',
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
