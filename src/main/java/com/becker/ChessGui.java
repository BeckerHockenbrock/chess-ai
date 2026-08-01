package com.becker;

import java.util.ArrayList;
import java.util.List;

import com.becker.pieces.Bishop;
import com.becker.pieces.Knight;
import com.becker.pieces.Pawn;
import com.becker.pieces.Piece;
import com.becker.pieces.Queen;
import com.becker.pieces.Rook;

import javafx.fxml.FXML;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ChessGui {
    @FXML
    private GridPane boardGrid;
    @FXML
    private Label statusLabel;
    @FXML
    private Button stockfishButton;

    private Board board = new Board();
    private List<int[]> highlightedMoves = new ArrayList<>();
    private int selectedRow = -1;
    private int selectedCol = -1;
    private Stockfish stockfish;
    private boolean playingStockfish;
    private boolean startingStockfish;
    private boolean stockfishThinking;
    @FXML
    private void initialize() {
        drawBoard();
        updateStatus();
    }

    private void updateStatus() {
        if (statusLabel == null) {
            return;
        }

        String who;
        String opponent;
        if (board.getCurrentTurn() == Piece.WHITE) {
            who = "White";
            opponent = "Black";
        } else {
            who = "Black";
            opponent = "White";
        }

        if (board.isCheckmate(board.getCurrentTurn())) {
            statusLabel.setText("Checkmate! " + opponent + " wins");
        } else if (board.isStalemate(board.getCurrentTurn())) {
            statusLabel.setText("Stalemate! Draw");
        } else if (board.isInCheck(board.getCurrentTurn())) {
            statusLabel.setText(who + " is in check");
        } else {
            statusLabel.setText(who + "'s turn");
        }
    }

    private void drawBoard() {
        boardGrid.getChildren().clear();

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Rectangle square = new Rectangle(80, 80);
                if ((row + col) % 2 == 0) {
                    square.setFill(Color.BEIGE);
                } else {
                    square.setFill(Color.SADDLEBROWN);
                }

                StackPane cell = new StackPane(square);
                Piece piece = board.getPiece(row, col);
                if (piece != null) {
                    ImageView view = new ImageView(new Image(getClass().getResourceAsStream(piece.getSymbol())));
                    cell.getChildren().add(view);
                }

                if (isHighlighted(row, col)) {
                    Circle dot = new Circle(12, Color.GRAY);
                    cell.getChildren().add(dot);
                }

                final int r = row;
                final int c = col;
                cell.setOnMouseClicked(e -> handleClick(r, c));

                boardGrid.add(cell, col, row);
            }
        }
    }

    private boolean isHighlighted(int row, int col) {
        for (int[] move : highlightedMoves) {
            if (move[0] == row && move[1] == col) {
                return true;
            }
        }
        return false;
    }

    private void handleClick(int row, int col) {
        if (playingStockfish && (board.getCurrentTurn() == Piece.BLACK || stockfishThinking)) {
            return;
        }

        if (selectedRow != -1 && isHighlighted(row, col)) {
            Piece moving = board.getPiece(selectedRow, selectedCol);
            if (board.isCastle(selectedRow, selectedCol, row, col)) {
                board.castle(selectedRow, selectedCol, row, col);
            } else {
                board.movePiece(selectedRow, selectedCol, row, col);
            }
            if (moving instanceof Pawn && (row == 0 || row == 7)) {
                board.setPiece(row, col, choosePromotion(moving.getColor()));
            }
            selectedRow = -1;
            selectedCol = -1;
            highlightedMoves = new ArrayList<>();
            drawBoard();
            updateStatus();
            if (playingStockfish && board.getCurrentTurn() == Piece.BLACK && !gameIsOver()) {
                makeStockfishMove();
            }
            return;
        }

        Piece piece = board.getPiece(row, col);
        if (piece != null && piece.getColor() == board.getCurrentTurn()) {
            List<int[]> moves = board.getLegalMoves(row, col);
            if (moves != null) {
                highlightedMoves = moves;
            } else {
                highlightedMoves = new ArrayList<>();
            }
            selectedRow = row;
            selectedCol = col;
        } else {
            highlightedMoves = new ArrayList<>();
            selectedRow = -1;
            selectedCol = -1;
        }
        drawBoard();
    }

    @FXML
    private void toggleStockfish() {
        if (playingStockfish) {
            closeStockfish();
            updateStatus();
        } else if (!startingStockfish) {
            startStockfish();
        }
    }

    private void startStockfish() {
        startingStockfish = true;
        stockfishButton.setDisable(true);
        statusLabel.setText("Starting Stockfish...");

        Task<Stockfish> task = new Task<>() {
            @Override
            protected Stockfish call() throws Exception {
                Stockfish engine = new Stockfish();
                try {
                    engine.start();
                    return engine;
                } catch (Exception exception) {
                    engine.close();
                    throw exception;
                }
            }
        };

        task.setOnSucceeded(event -> {
            stockfish = task.getValue();
            playingStockfish = true;
            startingStockfish = false;
            stockfishButton.setDisable(false);
            stockfishButton.setText("Stop Stockfish");
            updateStatus();
            if (board.getCurrentTurn() == Piece.BLACK && !gameIsOver()) {
                makeStockfishMove();
            }
        });

        task.setOnFailed(event -> {
            startingStockfish = false;
            stockfishButton.setDisable(false);
            stockfishButton.setText("Play Stockfish");
            statusLabel.setText("Could not start Stockfish. Put it on PATH or set STOCKFISH_PATH.");
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private void makeStockfishMove() {
        if (stockfish == null || stockfishThinking || board.getCurrentTurn() != Piece.BLACK) {
            return;
        }

        stockfishThinking = true;
        statusLabel.setText("Stockfish is thinking...");
        String fen = new FenCreator().makeFenString(board);

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                return stockfish.getBestMove(fen);
            }
        };

        task.setOnSucceeded(event -> {
            stockfishThinking = false;
            if (!playingStockfish) {
                return;
            }

            String move = task.getValue();
            if (!"(none)".equals(move)) {
                if (board.makeUciMove(move)) {
                    drawBoard();
                } else {
                    statusLabel.setText("Stockfish sent an invalid move.");
                    return;
                }
            }
            updateStatus();
        });

        task.setOnFailed(event -> {
            stockfishThinking = false;
            if (playingStockfish) {
                statusLabel.setText("Stockfish could not make a move.");
            }
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private boolean gameIsOver() {
        return board.isCheckmate(board.getCurrentTurn()) || board.isStalemate(board.getCurrentTurn());
    }

    public void closeStockfish() {
        playingStockfish = false;
        startingStockfish = false;
        stockfishThinking = false;
        if (stockfish != null) {
            stockfish.close();
            stockfish = null;
        }
        if (stockfishButton != null) {
            stockfishButton.setDisable(false);
            stockfishButton.setText("Play Stockfish");
        }
    }

    private Piece choosePromotion(int color) {
        Piece[] options = { new Queen(color), new Rook(color), new Bishop(color), new Knight(color) };
        Piece[] chosen = { options[0] };

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Choose promotion");

        HBox box = new HBox(10);
        box.setPadding(new Insets(10));
        for (Piece option : options) {
            ImageView view = new ImageView(new Image(getClass().getResourceAsStream(option.getSymbol())));
            Button button = new Button();
            button.setGraphic(view);
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

}
