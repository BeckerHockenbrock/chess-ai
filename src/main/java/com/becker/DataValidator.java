package com.becker;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DataValidator {

    public static void main(String[] args) throws Exception {
        Path databasePath = getDatabasePath();
        if (!Files.isRegularFile(databasePath)) {
            throw new IllegalArgumentException("Training database was not found: " + databasePath);
        }

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath)) {
            List<String> errors = validate(connection);
            printSummary(connection);

            if (!errors.isEmpty()) {
                for (String error : errors) {
                    System.err.println("ERROR: " + error);
                }
                throw new IllegalStateException("Training data validation failed with " + errors.size()
                        + " error(s).");
            }
        }

        System.out.println("Training data validation passed.");
    }

    private static Path getDatabasePath() {
        String configuredPath = System.getProperty("training.database.path", System.getenv("TRAINING_DATABASE_PATH"));
        if (configuredPath == null || configuredPath.isBlank()) {
            return Path.of("data", "training.db");
        }
        return Path.of(configuredPath);
    }

    static List<String> validate(Connection connection) throws SQLException {
        List<String> errors = new ArrayList<>();
        validateJson(connection, errors);
        if (errors.isEmpty()) {
            validateTeacherMoves(connection, errors);
        }
        validateGameSplits(connection, errors);
        validateFenReplay(connection, errors);
        return errors;
    }

    private static void validateJson(Connection connection, List<String> errors) throws SQLException {
        String sql = "SELECT id FROM positions WHERE json_valid(legal_moves_json) = 0 "
                + "OR json_valid(teacher_policy_json) = 0";
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                addError(errors, "Position " + result.getLong("id") + " contains invalid JSON.");
            }
        }

        String emptyTeacherSql = "SELECT id FROM positions WHERE json_array_length(teacher_policy_json) = 0";
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(emptyTeacherSql)) {
            while (result.next()) {
                addError(errors, "Position " + result.getLong("id") + " has no teacher move.");
            }
        }
    }

    private static void validateTeacherMoves(Connection connection, List<String> errors) throws SQLException {
        String sql = "SELECT p.id FROM positions p WHERE json_extract(p.teacher_policy_json, '$[0].move') "
                + "IS NULL OR NOT EXISTS (SELECT 1 FROM json_each(p.legal_moves_json) "
                + "WHERE value = json_extract(p.teacher_policy_json, '$[0].move'))";
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                addError(errors, "Position " + result.getLong("id")
                        + " has a teacher move that is not legal.");
            }
        }
    }

    private static void validateGameSplits(Connection connection, List<String> errors) throws SQLException {
        String sql = "SELECT game_id FROM positions GROUP BY game_id HAVING COUNT(DISTINCT split) != 1";
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                addError(errors, "Game " + result.getLong("game_id") + " is assigned to more than one split.");
            }
        }
    }

    private static void validateFenReplay(Connection connection, List<String> errors) throws SQLException {
        String expectedInitialFen = new FenCreator().makeFenString(new Board());
        String gameSql = "SELECT id, initial_fen, moves_uci FROM games ORDER BY id";
        try (Statement gameStatement = connection.createStatement();
             ResultSet games = gameStatement.executeQuery(gameSql)) {
            while (games.next()) {
                long gameId = games.getLong("id");
                String initialFen = games.getString("initial_fen");
                if (!expectedInitialFen.equals(initialFen)) {
                    addError(errors, "Game " + gameId + " does not use the standard starting position.");
                    continue;
                }
                validateGamePositions(connection, gameId, splitMoves(games.getString("moves_uci")), errors);
            }
        }
    }

    private static void validateGamePositions(Connection connection, long gameId, List<String> moves,
                                              List<String> errors) throws SQLException {
        String positionSql = "SELECT id, ply, fen, json_extract(teacher_policy_json, '$[0].move') "
                + "AS teacher_move FROM positions WHERE game_id = ? ORDER BY ply";
        Board board = new Board();
        FenCreator fenCreator = new FenCreator();
        int appliedMoves = 0;
        int previousPly = -1;

        try (PreparedStatement statement = connection.prepareStatement(positionSql)) {
            statement.setLong(1, gameId);
            try (ResultSet positions = statement.executeQuery()) {
                while (positions.next()) {
                    long positionId = positions.getLong("id");
                    int ply = positions.getInt("ply");
                    if (ply <= previousPly) {
                        addError(errors, "Position " + positionId + " does not have an increasing ply number.");
                        continue;
                    }
                    if (ply >= moves.size()) {
                        addError(errors, "Position " + positionId + " has no matching move in its game history.");
                        continue;
                    }

                    while (appliedMoves < ply) {
                        String move = moves.get(appliedMoves);
                        if (!board.makeUciMove(move)) {
                            addError(errors, "Game " + gameId + " contains an illegal replay move: " + move);
                            return;
                        }
                        appliedMoves++;
                    }

                    String expectedFen = fenCreator.makeFenString(board);
                    if (!expectedFen.equals(positions.getString("fen"))) {
                        addError(errors, "Position " + positionId + " does not match replayed game history.");
                    }
                    if (!moves.get(ply).equals(positions.getString("teacher_move"))) {
                        addError(errors, "Position " + positionId + " does not match its saved teacher move.");
                    }
                    previousPly = ply;
                }
            }
        }
    }

    private static List<String> splitMoves(String movesUci) {
        if (movesUci == null || movesUci.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.asList(movesUci.split("\\s+"));
    }

    private static void printSummary(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) AS positions, "
                     + "COUNT(DISTINCT game_id) AS games, MIN(value_cp) AS min_cp, "
                     + "MAX(value_cp) AS max_cp, SUM(value_mate IS NOT NULL) AS mate_labels "
                     + "FROM positions")) {
            result.next();
            System.out.println("Positions: " + result.getInt("positions")
                    + ", games: " + result.getInt("games")
                    + ", centipawn range: " + result.getObject("min_cp") + " to "
                    + result.getObject("max_cp")
                    + ", mate labels: " + result.getInt("mate_labels"));
        }

        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT split, COUNT(*) AS positions "
                     + "FROM positions GROUP BY split ORDER BY split")) {
            while (result.next()) {
                System.out.println(result.getString("split") + ": "
                        + result.getInt("positions") + " positions");
            }
        }
    }

    private static void addError(List<String> errors, String error) {
        if (errors.size() < 25) {
            errors.add(error);
        }
    }
}
