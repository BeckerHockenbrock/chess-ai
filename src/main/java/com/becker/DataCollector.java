package com.becker;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

        try (Connection connection = TrainingDatabase.open(databasePath.toString())) {
            long startTime = System.currentTimeMillis();
            System.out.printf("Starting data collection: target=%d positions, depth=%d, workers=%d%n",
                    settings.count, settings.searchDepth, settings.threads);

            int gathered = gatherPositionsParallel(connection, settings);

            long elapsed = System.currentTimeMillis() - startTime;
            double seconds = elapsed / 1000.0;
            double positionsPerSec = gathered / Math.max(0.001, seconds);

            System.out.printf("Done! Gathered %d positions in %s at depth %d in %.2fs (%.1f pos/sec) using %d workers.%n",
                    gathered, databasePath, settings.searchDepth, seconds, positionsPerSec, settings.threads);
        }
    }

    private static Path getDatabasePath() {
        String configuredPath = System.getProperty("training.database.path", System.getenv("TRAINING_DATABASE_PATH"));
        if (configuredPath == null || configuredPath.isBlank()) {
            return Path.of("data", "training.db");
        }
        return Path.of(configuredPath);
    }

    static CollectionSettings readSettings(String[] args) {
        if (args.length < 4 || args.length > 5) {
            throw new IllegalArgumentException("Usage: make gather 100 DEPTH=8 "
                    + "MIN_OPENING_PLIES=8 MAX_OPENING_PLIES=20 [THREADS=8]");
        }
        int count = readPositiveNumber(args[0], "Count");
        int searchDepth = readPositiveNumber(args[1], "Depth");
        int minOpeningPlies = readNonNegativeNumber(args[2], "Minimum opening plies");
        int maxOpeningPlies = readNonNegativeNumber(args[3], "Maximum opening plies");
        if (minOpeningPlies > maxOpeningPlies) {
            throw new IllegalArgumentException("Minimum opening plies cannot be greater than maximum opening plies.");
        }
        int defaultThreads = Math.max(1, Runtime.getRuntime().availableProcessors());
        int threads = (args.length >= 5) ? readPositiveNumber(args[4], "Threads") : defaultThreads;
        return new CollectionSettings(count, searchDepth, minOpeningPlies, maxOpeningPlies, threads);
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
        Stockfish stockfish = new Stockfish();
        stockfish.start();
        stockfish.setMultiPv(MULTI_PV);
        stockfish.setHash(64);
        return stockfish;
    }

    private static int gatherPositionsParallel(Connection connection, CollectionSettings settings)
            throws SQLException, InterruptedException {
        int initialGameNumber = nextGameNumber(connection);
        AtomicInteger totalGathered = new AtomicInteger(0);
        AtomicInteger nextGameNum = new AtomicInteger(initialGameNumber);
        Object dbLock = new Object();

        ExecutorService executor = Executors.newFixedThreadPool(settings.threads);

        for (int workerId = 0; workerId < settings.threads; workerId++) {
            executor.submit(() -> {
                try (Stockfish stockfish = startStockfish()) {
                    Random random = new Random();
                    while (totalGathered.get() < settings.count) {
                        int remaining = settings.count - totalGathered.get();
                        if (remaining <= 0) {
                            break;
                        }

                        int gameNumber = nextGameNum.getAndIncrement();
                        CollectedGame game = playAndAnalyzeGame(stockfish, random, gameNumber, remaining, settings);
                        if (game == null || game.positions.isEmpty()) {
                            continue;
                        }

                        synchronized (dbLock) {
                            int current = totalGathered.get();
                            if (current >= settings.count) {
                                break;
                            }
                            int canSave = Math.min(game.positions.size(), settings.count - current);
                            if (canSave > 0) {
                                saveGame(connection, game, canSave, gameNumber, settings);
                                int newTotal = totalGathered.addAndGet(canSave);
                                double pct = (newTotal * 100.0) / settings.count;
                                System.out.printf("Collected %d/%d positions (%.1f%%) [Game #%d]%n",
                                        newTotal, settings.count, pct, gameNumber);
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Worker thread error: " + e.getMessage());
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(24, TimeUnit.HOURS);
        return totalGathered.get();
    }

    private static CollectedGame playAndAnalyzeGame(Stockfish stockfish, Random random,
                                                    int gameNumber, int remaining, CollectionSettings settings)
            throws IOException {
        Board board = new Board();
        FenCreator fenCreator = new FenCreator();
        String initialFen = fenCreator.makeFenString(board);
        List<String> gameMoves = new ArrayList<>();

        int requestedRandomOpeningPlies = settings.minOpeningPlies
                + random.nextInt(settings.maxOpeningPlies - settings.minOpeningPlies + 1);
        int randomOpeningPlies = playRandomOpening(board, gameMoves, random, requestedRandomOpeningPlies);

        int maximumForGame = Math.min(POSITIONS_PER_GAME, remaining);
        List<PositionRecord> positions = new ArrayList<>();

        while (positions.size() < maximumForGame && board.hasAnyLegalMove(board.getCurrentTurn())) {
            String fen = fenCreator.makeFenString(board);
            List<String> legalMoves = board.getAllLegalMovesUci();
            List<StockfishAnalysis> analyses = stockfish.analysePosition(fen, settings.searchDepth, MULTI_PV);
            if (analyses.isEmpty()) {
                throw new IOException("Stockfish returned no analysis for " + fen);
            }

            int absolutePly = randomOpeningPlies + positions.size();
            positions.add(new PositionRecord(absolutePly, fen, legalMoves, analyses));

            String bestMove = analyses.get(0).getFirstMove();
            if (!board.makeUciMove(bestMove)) {
                throw new IOException("Stockfish returned an illegal move: " + bestMove);
            }
            gameMoves.add(bestMove);
        }

        if (positions.isEmpty()) {
            return null;
        }

        String result = getGameResult(board);
        String split = splitForGame(gameNumber);
        return new CollectedGame(initialFen, randomOpeningPlies, split, gameMoves, result, positions);
    }

    private static void saveGame(Connection connection, CollectedGame game, int countToSave,
                                 int gameNumber, CollectionSettings settings) throws SQLException {
        connection.setAutoCommit(false);
        try {
            long gameId = insertGame(connection, game.initialFen, game.split, game.randomOpeningPlies, settings);
            for (int i = 0; i < countToSave; i++) {
                PositionRecord pos = game.positions.get(i);
                insertPosition(connection, gameId, pos.ply, pos.fen, pos.legalMoves, pos.analyses, game.split);
            }
            int totalMoves = game.randomOpeningPlies + countToSave;
            updateGame(connection, gameId, game.gameMoves.subList(0, Math.min(game.gameMoves.size(), totalMoves)));
            updateGameResult(connection, gameId, game.gameResult);
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private static int playRandomOpening(Board board, List<String> gameMoves, Random random, int plies) {
        int played = 0;
        for (int ply = 0; ply < plies; ply++) {
            List<String> legalMoves = board.getAllLegalMovesUci();
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
        final int threads;

        CollectionSettings(int count, int searchDepth, int minOpeningPlies, int maxOpeningPlies, int threads) {
            this.count = count;
            this.searchDepth = searchDepth;
            this.minOpeningPlies = minOpeningPlies;
            this.maxOpeningPlies = maxOpeningPlies;
            this.threads = threads;
        }
    }

    private static class PositionRecord {
        final int ply;
        final String fen;
        final List<String> legalMoves;
        final List<StockfishAnalysis> analyses;

        PositionRecord(int ply, String fen, List<String> legalMoves, List<StockfishAnalysis> analyses) {
            this.ply = ply;
            this.fen = fen;
            this.legalMoves = legalMoves;
            this.analyses = analyses;
        }
    }

    private static class CollectedGame {
        final String initialFen;
        final int randomOpeningPlies;
        final String split;
        final List<String> gameMoves;
        final String gameResult;
        final List<PositionRecord> positions;

        CollectedGame(String initialFen, int randomOpeningPlies, String split,
                      List<String> gameMoves, String gameResult, List<PositionRecord> positions) {
            this.initialFen = initialFen;
            this.randomOpeningPlies = randomOpeningPlies;
            this.split = split;
            this.gameMoves = gameMoves;
            this.gameResult = gameResult;
            this.positions = positions;
        }
    }
}
