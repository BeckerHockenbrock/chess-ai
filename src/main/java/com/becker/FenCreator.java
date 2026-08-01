package com.becker;

import com.becker.pieces.*;

public class FenCreator {

    public String makeFenString(Board board) {
        StringBuilder fen = new StringBuilder();
        for (int row = 0; row < 8; row++) {
            int emptyCount = 0;
            for (int col = 0; col < 8; col++) {
                Piece piece = board.getPiece(row, col);
                if (piece == null) {
                    emptyCount++;
                } else {
                    if (emptyCount > 0) {
                        fen.append(emptyCount);
                        emptyCount = 0;
                    }
                    
                    String c = getPieceStr(piece);
                    if (piece.getColor() == Piece.WHITE) {
                        c = c.toUpperCase();
                    }
                    fen.append(c);
                }
            }
            if (emptyCount > 0) {
                fen.append(emptyCount);
            }
            if (row < 7) {
                fen.append('/');
            }
        }
        fen.append(' ')
                .append(board.getCurrentTurn() == Piece.WHITE ? 'w' : 'b')
                .append(' ')
                .append(board.getCastlingRights())
                .append(' ')
                .append(getEnPassantSquare(board))
                .append(' ')
                .append(board.getHalfmoveClock())
                .append(' ')
                .append(board.getFullmoveNumber());
        return fen.toString();
    }

    private String getEnPassantSquare(Board board) {
        int[] target = board.getEnPassantTarget();
        if (target == null) {
            return "-";
        }
        char file = (char) ('a' + target[1]);
        int rank = 8 - target[0];
        return "" + file + rank;
    }

    private String getPieceStr(Piece piece) {
        if (piece instanceof Pawn) {
            return "p";
        }
        if (piece instanceof Knight) {
            return "n";
        }
        if (piece instanceof Bishop) {
            return "b";
        }
        if (piece instanceof Rook) {
            return "r";
        }
        if (piece instanceof Queen) {
            return "q";
        }
        if (piece instanceof King) {
            return "k";
        }
        throw new IllegalArgumentException("Unsupported piece type: " + piece.getClass().getName());
    }

}
