package com.becker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

class TrainingDatabaseTest {

    @Test
    void createsTrainingTablesAndEnforcesForeignKeys() throws SQLException {
        try (Connection connection = TrainingDatabase.open(":memory:")) {
            try (Statement statement = connection.createStatement()) {
                assertEquals(1, pragmaValue(statement, "foreign_keys"));
                assertEquals(1, countRows(statement, "sqlite_master", "name = 'games'"));
                assertEquals(1, countRows(statement, "sqlite_master", "name = 'positions'"));

                statement.executeUpdate("INSERT INTO games "
                        + "(id, initial_fen, moves_uci, stockfish_version, search_depth, created_at) "
                        + "VALUES (1, 'fen', '', 'test', 4, 'now')");
                statement.executeUpdate("INSERT INTO positions "
                        + "(game_id, ply, fen, legal_moves_json, teacher_policy_json, split) "
                        + "VALUES (1, 0, 'fen', '[]', '[]', 'train')");
                assertEquals(1, countRows(statement, "positions", "game_id = 1"));
            }
        }
    }

    private int pragmaValue(Statement statement, String pragma) throws SQLException {
        try (ResultSet result = statement.executeQuery("PRAGMA " + pragma)) {
            result.next();
            return result.getInt(1);
        }
    }

    private int countRows(Statement statement, String table, String condition) throws SQLException {
        try (ResultSet result = statement.executeQuery(
                "SELECT COUNT(*) FROM " + table + " WHERE " + condition)) {
            result.next();
            return result.getInt(1);
        }
    }
}
