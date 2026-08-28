package com.becker;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class TrainingDatabase {

    private TrainingDatabase() {
    }

    public static Connection open(String databasePath) throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
        configurePragmas(connection);
        createSchema(connection);
        return connection;
    }

    public static void configurePragmas(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA synchronous = NORMAL");
            statement.execute("PRAGMA busy_timeout = 30000");
            statement.execute("PRAGMA temp_store = MEMORY");
        }
    }

    public static void createSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("CREATE TABLE IF NOT EXISTS games ("
                    + "id INTEGER PRIMARY KEY,"
                    + "initial_fen TEXT NOT NULL,"
                    + "moves_uci TEXT NOT NULL,"
                    + "stockfish_version TEXT NOT NULL,"
                    + "search_depth INTEGER NOT NULL CHECK (search_depth > 0),"
                    + "created_at TEXT NOT NULL,"
                    + "opening_randomness TEXT"
                    + ")");
            statement.execute("CREATE TABLE IF NOT EXISTS positions ("
                    + "id INTEGER PRIMARY KEY,"
                    + "game_id INTEGER NOT NULL,"
                    + "ply INTEGER NOT NULL CHECK (ply >= 0),"
                    + "fen TEXT NOT NULL,"
                    + "legal_moves_json TEXT NOT NULL,"
                    + "teacher_policy_json TEXT NOT NULL,"
                    + "value_cp INTEGER,"
                    + "value_mate INTEGER,"
                    + "game_result TEXT,"
                    + "split TEXT NOT NULL CHECK (split IN ('train', 'validation', 'test')),"
                    + "UNIQUE (game_id, ply),"
                    + "FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE"
                    + ")");
            statement.execute("CREATE INDEX IF NOT EXISTS positions_game_id_index "
                    + "ON positions(game_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS positions_split_index "
                    + "ON positions(split)");
        }
    }
}
