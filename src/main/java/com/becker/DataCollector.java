package com.becker;

import com.becker.pieces.Pawn;
import com.becker.pieces.Piece;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DataCollector {

    private static final int DEFAULT_SEARCH_DEPTH = 4;
    private static final int DEFAULT_MIN_OPENING_PLIES = 4;
    private static final int DEFAULT_MAX_OPENING_PLIES = 8;
    private static final int MULTI_PV = 3;
    private static final int POSITIONS_PER_GAME = 20;

    public static void main(String[] args) throws Exception {
        CollectionSettings settings = readSettings(args);
        Path databasePath = getDatabasePath();
        if (databasePath.getParent() != null) {
            Files.createDirectories(databasePath.getParent());
        }

        try (Connection connection = TrainingDatabase.open(databasePath.toString());
             Stockfish stockfish = startStockfish()) {
            int gathered = gatherPositions(connection, stockfish, settings);
            System.out.println("Gathered " + gathered + " positions in " + databasePath
                    + " at depth " + settings.searchDepth + " with random opening plies "
                    + settings.minOpeningPlies + "-" + settings.maxOpeningPlies + ".");
        }
    }

    private static Path getDatabasePath() {
        String configuredPath = System.getenv("TRAINING_DATABASE_PATH");
        if (configuredPath == null || configuredPath.isBlank()) {
            return Path.of("data", "training.db");
        }
        return Path.of(configuredPath);
    }

    static CollectionSettings readSettings(String[] args) {
        if (args.length != 4) {
            throw new IllegalArgumentException("Usage: make gather 100 DEPTH=8 "
                    + "MIN_OPENING_PLIES=8 MAX_OPENING_PLIES=20");
        }
        int count = readPositiveNumber(args[0], "Count");
        int searchDepth = readPositiveNumber(args[1], "Depth");
        int minOpeningPlies = readNonNegativeNumber(args[2], "Minimum opening plies");
        int maxOpeningPlies = readNonNegativeNumber(args[3], "Maximum opening plies");
        if (minOpeningPlies > maxOpeningPlies) {
            throw new IllegalArgumentException("Minimum opening plies cannot be greater than maximum opening plies.");
        }
        return new CollectionSettings(count, searchDepth, minOpeningPlies, maxOpeningPlies);
    }

    private static int readPositiveNumber(String value, String label) {
        int number = readNonNegativeNumber(value, label);
        if (number < 1) {
            throw new IllegalArgumentException(label + " must be a positive whole number.");
        }
        return number;
    }

    private static int readNonNegativeNumber(String value, String label) {
        try {
            int number = Integer.parseInt(value);
            if (number < 0) {
                throw new IllegalArgumentException(label + " cannot be negative.");
            }
            return number;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be a whole number.");
        }
    }

    private static Stockfish startStockfish() throws IOException {
        String configuredPath = System.getenv("STOCKFISH_PATH");
        String stockfishPath = configuredPath;
        if (stockfishPath == null || stockfishPath.isBlank()) {
            Path bundledPath = Path.of("src", "main", "stockfish", "stockfish-macos-m1-apple-silicon");
            stockfishPath = Files.isExecutable(bundledPath) ? bundledPath.toString() : "stockfish";
        }

        Stockfish stockfish = new Stockfish(stockfishPath);
        stockfish.start();
        return stockfish;
    }

    private static int gatherPositions(Connection connection, Stockfish stockfish, CollectionSettings settings)
            throws SQLException, IOException {
        int gathered = 0;
        int gameNumber = nextGameNumber(connection);
        Random random = new Random();

        while (gathered < settings.count) {
            int remaining = settings.count - gathered;
            int collectedInGame = gatherGame(connection, stockfish, random, gameNumber, remaining, settings);
            gathered += collectedInGame;
            gameNumber++;
        }
        return gathered;
    }

    private static int gatherGame(Connection connection, Stockfish stockfish, Random random,
                                  int gameNumber, int remaining, CollectionSettings settings)
            throws SQLException, IOException {
        Board board = new Board();
        FenCreator fenCreator = new FenCreator();
        String initialFen = fenCreator.makeFenString(board);
        List<String> gameMoves = new ArrayList<>();
        int requestedRandomOpeningPlies = settings.minOpeningPlies
                + random.nextInt(settings.maxOpeningPlies - settings.minOpeningPlies + 1);
        int randomOpeningPlies = playRandomOpening(board, gameMoves, random, requestedRandomOpeningPlies);

        connection.setAutoCommit(false);
        try {
            long gameId = insertGame(connection, initialFen, splitForGame(gameNumber), randomOpeningPlies,
                    settings);
            int collected = 0;
            int maximumForGame = Math.min(POSITIONS_PER_GAME, remaining);

            while (collected < maximumForGame && board.hasAnyLegalMove(board.getCurrentTurn())) {
                String fen = fenCreator.makeFenString(board);
                List<String> legalMoves = getLegalMoves(board);
                List<StockfishAnalysis> analyses = stockfish.analysePosition(fen, settings.searchDepth, MULTI_PV);
                if (analyses.isEmpty()) {
                    throw new IOException("Stockfish returned no analysis for " + fen);
                }

                int absolutePly = randomOpeningPlies + collected;
                insertPosition(connection, gameId, absolutePly, fen, legalMoves, analyses,
                        splitForGame(gameNumber));
                String bestMove = analyses.get(0).getFirstMove();
                if (!board.makeUciMove(bestMove)) {
                    throw new IOException("Stockfish returned an illegal move: " + bestMove);
                }
                gameMoves.add(bestMove);
                collected++;
            }

            if (collected == 0) {
                connection.rollback();
                return 0;
            }

            updateGame(connection, gameId, gameMoves);
            updateGameResult(connection, gameId, getGameResult(board));
            connection.commit();
            return collected;
        } catch (SQLException | IOException | RuntimeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private static int playRandomOpening(Board board, List<String> gameMoves, Random random, int plies) {
        int played = 0;
        for (int ply = 0; ply < plies; ply++) {
            List<String> legalMoves = getLegalMoves(board);
            if (legalMoves.isEmpty()) {
                return played;
            }
            String move = legalMoves.get(random.nextInt(legalMoves.size()));
            board.makeUciMove(move);
            gameMoves.add(move);
            played++;
        }
        return played;
    }

    private static List<String> getLegalMoves(Board board) {
        List<String> moves = new ArrayList<>();
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Piece piece = board.getPiece(row, col);
                if (piece == null || piece.getColor() != board.getCurrentTurn()) {
                    continue;
                }
                for (int[] destination : board.getLegalMoves(row, col)) {
                    String move = square(row, col) + square(destination[0], destination[1]);
                    if (piece instanceof Pawn && (destination[0] == 0 || destination[0] == 7)) {
                        moves.add(move + "q");
                        moves.add(move + "r");
                        moves.add(move + "b");
                        moves.add(move + "n");
                    } else {
                        moves.add(move);
                    }
                }
            }
        }
        return moves;
    }

    private static String square(int row, int col) {
        return "" + (char) ('a' + col) + (8 - row);
    }

    private static long insertGame(Connection connection, String initialFen, String split,
                                   int randomOpeningPlies, CollectionSettings settings) throws SQLException {
        String sql = "INSERT INTO games (initial_fen, moves_uci, stockfish_version, search_depth, "
                + "created_at, opening_randomness) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, initialFen);
            statement.setString(2, "");
            statement.setString(3, "Stockfish");
            statement.setInt(4, settings.searchDepth);
            statement.setString(5, Instant.now().toString());
            statement.setString(6, "random_plies=" + randomOpeningPlies + "; configured_range="
                    + settings.minOpeningPlies + "-" + settings.maxOpeningPlies + "; split=" + split);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Could not create a game record.");
    }

    private static void insertPosition(Connection connection, long gameId, int ply, String fen,
                                       List<String> legalMoves, List<StockfishAnalysis> analyses,
                                       String split) throws SQLException {
        String sql = "INSERT INTO positions (game_id, ply, fen, legal_moves_json, teacher_policy_json, "
                + "value_cp, value_mate, split) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        StockfishAnalysis bestAnalysis = analyses.get(0);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, gameId);
            statement.setInt(2, ply);
            statement.setString(3, fen);
            statement.setString(4, movesToJson(legalMoves));
            statement.setString(5, analysesToJson(analyses));
            setNullableInteger(statement, 6, bestAnalysis.getScoreCp());
            setNullableInteger(statement, 7, bestAnalysis.getScoreMate());
            statement.setString(8, split);
            statement.executeUpdate();
        }
    }

    private static void setNullableInteger(PreparedStatement statement, int index, Integer value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private static void updateGame(Connection connection, long gameId, List<String> gameMoves)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE games SET moves_uci = ? WHERE id = ?")) {
            statement.setString(1, String.join(" ", gameMoves));
            statement.setLong(2, gameId);
            statement.executeUpdate();
        }
    }

    private static void updateGameResult(Connection connection, long gameId, String gameResult)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE positions SET game_result = ? WHERE game_id = ?")) {
            statement.setString(1, gameResult);
            statement.setLong(2, gameId);
            statement.executeUpdate();
        }
    }

    private static String getGameResult(Board board) {
        int sideToMove = board.getCurrentTurn();
        if (board.isCheckmate(sideToMove)) {
            return sideToMove == Piece.WHITE ? "black_win" : "white_win";
        }
        if (board.isStalemate(sideToMove)) {
            return "draw";
        }
        return "unfinished";
    }

    private static int nextGameNumber(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM games")) {
            result.next();
            return result.getInt(1) + 1;
        }
    }

    private static String splitForGame(int gameNumber) {
        int remainder = gameNumber % 5;
        if (remainder == 0) {
            return "test";
        }
        if (remainder == 1) {
            return "validation";
        }
        return "train";
    }

    private static String movesToJson(List<String> moves) {
        List<String> quotedMoves = new ArrayList<>();
        for (String move : moves) {
            quotedMoves.add("\"" + move + "\"");
        }
        return "[" + String.join(",", quotedMoves) + "]";
    }

    private static String analysesToJson(List<StockfishAnalysis> analyses) {
        List<String> values = new ArrayList<>();
        for (StockfishAnalysis analysis : analyses) {
            String scoreCp = analysis.getScoreCp() == null ? "null" : analysis.getScoreCp().toString();
            String scoreMate = analysis.getScoreMate() == null ? "null" : analysis.getScoreMate().toString();
            values.add("{\"move\":\"" + analysis.getFirstMove() + "\",\"scoreCp\":" + scoreCp
                    + ",\"scoreMate\":" + scoreMate + "}");
        }
        return "[" + String.join(",", values) + "]";
    }

    static class CollectionSettings {
        final int count;
        final int searchDepth;
        final int minOpeningPlies;
        final int maxOpeningPlies;

        CollectionSettings(int count, int searchDepth, int minOpeningPlies, int maxOpeningPlies) {
            this.count = count;
            this.searchDepth = searchDepth;
            this.minOpeningPlies = minOpeningPlies;
            this.maxOpeningPlies = maxOpeningPlies;
        }
    }
}
