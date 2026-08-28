package com.becker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import com.becker.pieces.Bishop;
import com.becker.pieces.King;
import com.becker.pieces.Knight;
import com.becker.pieces.Pawn;
import com.becker.pieces.Piece;
import com.becker.pieces.Queen;
import com.becker.pieces.Rook;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ChessGui {

    public static final String MODE_PLAY_MODEL = "Play vs AI Model";
    public static final String MODE_WATCH_SF_VS_MODEL = "Watch: Stockfish vs Model";
    public static final String MODE_WATCH_MODEL_VS_SF = "Watch: Model vs Stockfish";
    public static final String MODE_PLAY_STOCKFISH = "Play vs Stockfish";
    public static final String MODE_TWO_PLAYERS = "Two Players (Pass & Play)";

    @FXML
    private GridPane boardGrid;
    @FXML
    private ComboBox<String> modeComboBox;
    @FXML
    private Label humanColorLabel;
    @FXML
    private ComboBox<String> humanColorComboBox;
    @FXML
    private Label delayLabel;
    @FXML
    private ComboBox<String> delayComboBox;
    @FXML
    private Button actionButton;
    @FXML
    private Button newGameButton;
    @FXML
    private Label statusLabel;
    @FXML
    private Label evalLabel;
    @FXML
    private Label lastMoveLabel;

    private final Board board = new Board();
    private final FenCreator fenCreator = new FenCreator();
    private Stockfish stockfish;
    private ChessModel chessModel;

    private List<int[]> highlightedMoves = new ArrayList<>();
    private int selectedRow = -1;
    private int selectedCol = -1;
    private int[] lastMoveFrom = null;
    private int[] lastMoveTo = null;

    private volatile boolean matchRunning = false;
    private volatile boolean engineThinking = false;
    private Thread matchThread = null;

    @FXML
    private void initialize() {
        setupControls();
        drawBoard();
        updateStatus();
    }

    private void setupControls() {
        modeComboBox.getItems().addAll(
                MODE_PLAY_MODEL,
                MODE_WATCH_SF_VS_MODEL,
                MODE_WATCH_MODEL_VS_SF,
                MODE_PLAY_STOCKFISH,
                MODE_TWO_PLAYERS
        );
        modeComboBox.setValue(MODE_PLAY_MODEL);

        humanColorComboBox.getItems().addAll("White", "Black");
        humanColorComboBox.setValue("White");

        delayComboBox.getItems().addAll(
                "0.5s (Fast)",
                "1.0s",
                "1.5s",
                "2.0s (Default)",
                "3.0s (Relaxed)",
                "4.0s (Slow)"
        );
        delayComboBox.setValue("2.0s (Default)");

        modeComboBox.valueProperty().addListener((obs, oldVal, newVal) -> onModeChanged(newVal));
        humanColorComboBox.valueProperty().addListener((obs, oldVal, newVal) -> handleNewGame());
        
        onModeChanged(modeComboBox.getValue());
    }

    private void onModeChanged(String mode) {
        stopAiMatch();
        boolean isWatchMode = isWatchMode(mode);
        boolean isHumanVsAi = isHumanVsAi(mode);

        humanColorLabel.setVisible(isHumanVsAi);
        humanColorLabel.setManaged(isHumanVsAi);
        humanColorComboBox.setVisible(isHumanVsAi);
        humanColorComboBox.setManaged(isHumanVsAi);

        delayLabel.setVisible(isWatchMode);
        delayLabel.setManaged(isWatchMode);
        delayComboBox.setVisible(isWatchMode);
        delayComboBox.setManaged(isWatchMode);

        actionButton.setVisible(isWatchMode);
        actionButton.setManaged(isWatchMode);
        actionButton.setText("Start Match");
        actionButton.setStyle("-fx-background-color: #7fa650; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 6 16 6 16; -fx-background-radius: 4; -fx-cursor: hand;");

        handleNewGame();
    }

    private boolean isWatchMode(String mode) {
        return MODE_WATCH_SF_VS_MODEL.equals(mode) || MODE_WATCH_MODEL_VS_SF.equals(mode);
    }

    private boolean isHumanVsAi(String mode) {
        return MODE_PLAY_MODEL.equals(mode) || MODE_PLAY_STOCKFISH.equals(mode);
    }

    private int getSelectedDelayMs() {
        String val = delayComboBox.getValue();
        if (val == null) return 2000;
        if (val.startsWith("0.5")) return 500;
        if (val.startsWith("1.0")) return 1000;
        if (val.startsWith("1.5")) return 1500;
        if (val.startsWith("2.0")) return 2000;
        if (val.startsWith("3.0")) return 3000;
        if (val.startsWith("4.0")) return 4000;
        return 2000;
    }

    @FXML
    private void handleAction() {
        if (matchRunning) {
            stopAiMatch();
            actionButton.setText("Start Match");
            actionButton.setStyle("-fx-background-color: #7fa650; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 6 16 6 16; -fx-background-radius: 4; -fx-cursor: hand;");
            updateStatus();
        } else {
            startAiMatch();
        }
    }

    @FXML
    private void handleNewGame() {
        stopAiMatch();
        engineThinking = false;
        board.reset();
        selectedRow = -1;
        selectedCol = -1;
        highlightedMoves.clear();
        lastMoveFrom = null;
        lastMoveTo = null;
        
        evalLabel.setText("Eval: 0.00");
        lastMoveLabel.setText("Last Move: None");
        drawBoard();
        updateStatus();

        if (isWatchMode(modeComboBox.getValue())) {
            actionButton.setText("Start Match");
            actionButton.setStyle("-fx-background-color: #7fa650; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 6 16 6 16; -fx-background-radius: 4; -fx-cursor: hand;");
        } else if (isHumanVsAi(modeComboBox.getValue())) {
            int humanColor = "Black".equalsIgnoreCase(humanColorComboBox.getValue()) ? Piece.BLACK : Piece.WHITE;
            if (board.getCurrentTurn() != humanColor && !gameIsOver()) {
                triggerSingleAiMove();
            }
        }
    }

    private void startAiMatch() {
        if (gameIsOver()) {
            board.reset();
            lastMoveFrom = null;
            lastMoveTo = null;
            evalLabel.setText("Eval: 0.00");
            lastMoveLabel.setText("Last Move: None");
            drawBoard();
        }

        matchRunning = true;
        actionButton.setText("Stop Match");
        actionButton.setStyle("-fx-background-color: #d9534f; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 6 16 6 16; -fx-background-radius: 4; -fx-cursor: hand;");

        matchThread = new Thread(this::runAiMatchLoop, "AI-Match-Thread");
        matchThread.setDaemon(true);
        matchThread.start();
    }

    private void stopAiMatch() {
        matchRunning = false;
        if (matchThread != null && matchThread.isAlive()) {
            matchThread.interrupt();
            matchThread = null;
        }
    }

    private void runAiMatchLoop() {
        while (matchRunning) {
            if (gameIsOver()) {
                Platform.runLater(() -> {
                    matchRunning = false;
                    actionButton.setText("Start Match");
                    actionButton.setStyle("-fx-background-color: #7fa650; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 6 16 6 16; -fx-background-radius: 4; -fx-cursor: hand;");
                    updateStatus();
                });
                break;
            }

            int currentTurn = board.getCurrentTurn();
            boolean isWhite = (currentTurn == Piece.WHITE);
            String mode = modeComboBox.getValue();
            boolean whiteIsStockfish = MODE_WATCH_SF_VS_MODEL.equals(mode);

            String engineName;
            if (isWhite) {
                engineName = whiteIsStockfish ? "Stockfish" : "AI Model";
            } else {
                engineName = whiteIsStockfish ? "AI Model" : "Stockfish";
            }

            Platform.runLater(() -> statusLabel.setText(engineName + " (" + (isWhite ? "White" : "Black") + ") is thinking..."));

            long startTime = System.currentTimeMillis();
            String fen = fenCreator.makeFenString(board);
            String uciMove = null;
            String evalText = null;

            try {
                if ("Stockfish".equals(engineName)) {
                    Stockfish sf = getStockfishEngine();
                    uciMove = sf.getBestMove(fen);
                    evalText = "Stockfish move";
                } else {
                    ChessModel model = getModelEngine();
                    List<String> legalMoves = board.getAllLegalMovesUci();
                    ModelPrediction pred = model.predict(fen, legalMoves);
                    uciMove = pred.getBestMove();
                    evalText = String.format("Model Eval: %+.2f", pred.getValue());
                }
            } catch (Exception e) {
                final String errorMsg = e.getMessage();
                Platform.runLater(() -> {
                    statusLabel.setText("Engine error: " + errorMsg);
                    matchRunning = false;
                    actionButton.setText("Start Match");
                    actionButton.setStyle("-fx-background-color: #7fa650; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 6 16 6 16; -fx-background-radius: 4; -fx-cursor: hand;");
                });
                break;
            }

            if (!matchRunning || uciMove == null || "(none)".equals(uciMove)) {
                break;
            }

            long elapsed = System.currentTimeMillis() - startTime;
            int targetDelay = getSelectedDelayMs();
            long sleepTime = targetDelay - elapsed;
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    break;
                }
            }

            if (!matchRunning) {
                break;
            }

            final String finalMove = uciMove;
            final String finalEval = evalText;
            final String finalEngine = engineName;
            final int moveTurn = currentTurn;

            CountDownLatch latch = new CountDownLatch(1);
            Platform.runLater(() -> {
                try {
                    if (executeUciMove(finalMove)) {
                        lastMoveLabel.setText(String.format("Last: %s (%s) %s",
                                moveTurn == Piece.WHITE ? "White" : "Black", finalEngine, finalMove));
                        evalLabel.setText(finalEval);
                        updateStatus();
                    }
                } finally {
                    latch.countDown();
                }
            });

            try {
                latch.await();
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void triggerSingleAiMove() {
        if (engineThinking || gameIsOver()) {
            return;
        }

        String mode = modeComboBox.getValue();
        boolean isStockfishMode = MODE_PLAY_STOCKFISH.equals(mode);
        String engineName = isStockfishMode ? "Stockfish" : "AI Model";

        engineThinking = true;
        statusLabel.setText(engineName + " is thinking...");

        String fen = fenCreator.makeFenString(board);
        List<String> legalMoves = board.getAllLegalMovesUci();
        int turn = board.getCurrentTurn();

        Task<AiMoveResult> task = new Task<>() {
            @Override
            protected AiMoveResult call() throws Exception {
                // Short pacing pause for natural human-like response
                Thread.sleep(250);

                if (isStockfishMode) {
                    Stockfish sf = getStockfishEngine();
                    String move = sf.getBestMove(fen);
                    return new AiMoveResult(move, "Stockfish move");
                } else {
                    ChessModel model = getModelEngine();
                    ModelPrediction pred = model.predict(fen, legalMoves);
                    String eval = String.format("Model Eval: %+.2f", pred.getValue());
                    return new AiMoveResult(pred.getBestMove(), eval);
                }
            }
        };

        task.setOnSucceeded(event -> {
            engineThinking = false;
            AiMoveResult res = task.getValue();
            if (res != null && res.move != null && !"(none)".equals(res.move)) {
                if (executeUciMove(res.move)) {
                    lastMoveLabel.setText(String.format("Last: %s (%s) %s",
                            turn == Piece.WHITE ? "White" : "Black", engineName, res.move));
                    evalLabel.setText(res.eval);
                    updateStatus();
                } else {
                    statusLabel.setText(engineName + " attempted an illegal move: " + res.move);
                }
            }
        });

        task.setOnFailed(event -> {
            engineThinking = false;
            Throwable ex = task.getException();
            statusLabel.setText("AI error: " + (ex != null ? ex.getMessage() : "Unknown error"));
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private boolean executeUciMove(String move) {
        if (move == null || move.length() < 4) {
            return false;
        }
        int fromCol = move.charAt(0) - 'a';
        int fromRow = 8 - Character.getNumericValue(move.charAt(1));
        int toCol = move.charAt(2) - 'a';
        int toRow = 8 - Character.getNumericValue(move.charAt(3));

        lastMoveFrom = new int[]{fromRow, fromCol};
        lastMoveTo = new int[]{toRow, toCol};

        boolean success = board.makeUciMove(move);
        if (success) {
            drawBoard();
        }
        return success;
    }

    private void handleClick(int row, int col) {
        if (matchRunning || engineThinking) {
            return;
        }

        String mode = modeComboBox.getValue();
        if (isHumanVsAi(mode)) {
            int humanColor = "Black".equalsIgnoreCase(humanColorComboBox.getValue()) ? Piece.BLACK : Piece.WHITE;
            if (board.getCurrentTurn() != humanColor) {
                return;
            }
        }

        if (selectedRow != -1 && isHighlighted(row, col)) {
            Piece moving = board.getPiece(selectedRow, selectedCol);
            int prevRow = selectedRow;
            int prevCol = selectedCol;

            if (board.isCastle(selectedRow, selectedCol, row, col)) {
                board.castle(selectedRow, selectedCol, row, col);
            } else {
                board.movePiece(selectedRow, selectedCol, row, col);
            }

            if (moving instanceof Pawn && (row == 0 || row == 7)) {
                board.setPiece(row, col, choosePromotion(moving.getColor()));
            }

            lastMoveFrom = new int[]{prevRow, prevCol};
            lastMoveTo = new int[]{row, col};
            String uciStr = "" + (char) ('a' + prevCol) + (8 - prevRow) + (char) ('a' + col) + (8 - row);
            lastMoveLabel.setText("Last: Human " + uciStr);

            selectedRow = -1;
            selectedCol = -1;
            highlightedMoves.clear();
            drawBoard();
            updateStatus();

            if (isHumanVsAi(mode) && !gameIsOver()) {
                triggerSingleAiMove();
            }
            return;
        }

        Piece piece = board.getPiece(row, col);
        if (piece != null && piece.getColor() == board.getCurrentTurn()) {
            List<int[]> moves = board.getLegalMoves(row, col);
            highlightedMoves = moves != null ? moves : new ArrayList<>();
            selectedRow = row;
            selectedCol = col;
        } else {
            highlightedMoves.clear();
            selectedRow = -1;
            selectedCol = -1;
        }
        drawBoard();
    }

    private void drawBoard() {
        boardGrid.getChildren().clear();

        int[] kingInCheckPos = null;
        if (board.isInCheck(board.getCurrentTurn())) {
            kingInCheckPos = board.findKing(board.getCurrentTurn());
        }

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                StackPane cell = new StackPane();
                cell.setPrefSize(80, 80);

                Rectangle square = new Rectangle(80, 80);
                boolean isLight = (row + col) % 2 == 0;
                square.setFill(isLight ? Color.web("#f0d9b5") : Color.web("#b58863"));
                cell.getChildren().add(square);

                // Highlight last move
                if (isLastMoveSquare(row, col)) {
                    Rectangle lastMoveHighlight = new Rectangle(80, 80);
                    lastMoveHighlight.setFill(isLight ? Color.web("#cdd26a", 0.65) : Color.web("#aaa23a", 0.65));
                    cell.getChildren().add(lastMoveHighlight);
                }

                // Highlight selected square
                if (row == selectedRow && col == selectedCol) {
                    Rectangle selectHighlight = new Rectangle(80, 80);
                    selectHighlight.setFill(Color.web("#7b9652", 0.65));
                    cell.getChildren().add(selectHighlight);
                }

                // Highlight king if in check
                if (kingInCheckPos != null && row == kingInCheckPos[0] && col == kingInCheckPos[1]) {
                    Rectangle checkHighlight = new Rectangle(80, 80);
                    checkHighlight.setFill(Color.web("#e05353", 0.75));
                    cell.getChildren().add(checkHighlight);
                }

                // Rank / File coordinates labels on edge squares
                if (col == 0) {
                    Label rankLabel = new Label(String.valueOf(8 - row));
                    rankLabel.setFont(Font.font("Arial", FontWeight.BOLD, 11));
                    rankLabel.setTextFill(isLight ? Color.web("#b58863") : Color.web("#f0d9b5"));
                    StackPane.setAlignment(rankLabel, Pos.TOP_LEFT);
                    StackPane.setMargin(rankLabel, new Insets(2, 0, 0, 4));
                    cell.getChildren().add(rankLabel);
                }
                if (row == 7) {
                    Label fileLabel = new Label(String.valueOf((char) ('a' + col)));
                    fileLabel.setFont(Font.font("Arial", FontWeight.BOLD, 11));
                    fileLabel.setTextFill(isLight ? Color.web("#b58863") : Color.web("#f0d9b5"));
                    StackPane.setAlignment(fileLabel, Pos.BOTTOM_RIGHT);
                    StackPane.setMargin(fileLabel, new Insets(0, 4, 2, 0));
                    cell.getChildren().add(fileLabel);
                }

                // Draw piece
                Piece piece = board.getPiece(row, col);
                if (piece != null) {
                    try {
                        ImageView view = new ImageView(new Image(getClass().getResourceAsStream(piece.getSymbol())));
                        view.setFitWidth(64);
                        view.setFitHeight(64);
                        cell.getChildren().add(view);
                    } catch (Exception ignored) {
                    }
                }

                // Legal move indicator
                if (isHighlighted(row, col)) {
                    if (piece != null) {
                        // Capture target ring
                        Circle ring = new Circle(32, Color.TRANSPARENT);
                        ring.setStroke(Color.web("#222222", 0.4));
                        ring.setStrokeWidth(5);
                        cell.getChildren().add(ring);
                    } else {
                        // Empty square target dot
                        Circle dot = new Circle(11, Color.web("#222222", 0.35));
                        cell.getChildren().add(dot);
                    }
                }

                final int r = row;
                final int c = col;
                cell.setOnMouseClicked(e -> handleClick(r, c));

                boardGrid.add(cell, col, row);
            }
        }
    }

    private boolean isLastMoveSquare(int row, int col) {
        if (lastMoveFrom != null && lastMoveFrom[0] == row && lastMoveFrom[1] == col) {
            return true;
        }
        return lastMoveTo != null && lastMoveTo[0] == row && lastMoveTo[1] == col;
    }

    private boolean isHighlighted(int row, int col) {
        for (int[] move : highlightedMoves) {
            if (move[0] == row && move[1] == col) {
                return true;
            }
        }
        return false;
    }

    private void updateStatus() {
        if (statusLabel == null) {
            return;
        }

        int turn = board.getCurrentTurn();
        String who = (turn == Piece.WHITE) ? "White" : "Black";
        String opponent = (turn == Piece.WHITE) ? "Black" : "White";

        if (board.isCheckmate(turn)) {
            statusLabel.setText("Checkmate! " + opponent + " wins!");
        } else if (board.isStalemate(turn)) {
            statusLabel.setText("Stalemate! Draw.");
        } else if (board.isInCheck(turn)) {
            statusLabel.setText("Check! " + who + " is in check.");
        } else {
            statusLabel.setText(who + "'s turn");
        }
    }

    private boolean gameIsOver() {
        return board.isCheckmate(board.getCurrentTurn()) || board.isStalemate(board.getCurrentTurn());
    }

    private synchronized Stockfish getStockfishEngine() throws IOException {
        if (stockfish == null) {
            stockfish = new Stockfish();
            stockfish.start();
        }
        return stockfish;
    }

    private synchronized ChessModel getModelEngine() throws IOException {
        if (chessModel == null || !chessModel.isAlive()) {
            chessModel = new ChessModel();
            chessModel.start();
        }
        return chessModel;
    }

    public synchronized void closeEngines() {
        stopAiMatch();
        if (stockfish != null) {
            stockfish.close();
            stockfish = null;
        }
        if (chessModel != null) {
            chessModel.close();
            chessModel = null;
        }
    }

    private Piece choosePromotion(int color) {
        Piece[] options = { new Queen(color), new Rook(color), new Bishop(color), new Knight(color) };
        Piece[] chosen = { options[0] };

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Choose Promotion");

        HBox box = new HBox(12);
        box.setPadding(new Insets(14));
        box.setStyle("-fx-background-color: #2b2824;");
        for (Piece option : options) {
            ImageView view = new ImageView(new Image(getClass().getResourceAsStream(option.getSymbol())));
            view.setFitWidth(48);
            view.setFitHeight(48);
            Button button = new Button();
            button.setGraphic(view);
            button.setStyle("-fx-background-color: #454341; -fx-cursor: hand;");
            button.setOnAction(e -> {
                chosen[0] = option;
                dialog.close();
            });
            box.getChildren().add(button);
        }

        dialog.setScene(new Scene(box));
        dialog.showAndWait();
        return chosen[0];
    }

    private static class AiMoveResult {
        final String move;
        final String eval;

        AiMoveResult(String move, String eval) {
            this.move = move;
            this.eval = eval;
        }
    }
}
