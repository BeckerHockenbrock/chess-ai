package com.becker;

import com.becker.pieces.*;

public class FenCreator {

    public FenCreator(Board board) {
        makeFenString(board);
    }

    public String makeFenString(Board board) {
        String fen = "";
        for (int row = 0; row < 8; row++) {
            int emptyCount = 0;
            for (int col = 0; col < 8; col++) {
                Piece piece = board.getPiece(row, col);
                if (piece == null) {
                    emptyCount++;
                } else {
                    if (emptyCount > 0) {
                        fen += emptyCount;
                        emptyCount = 0;
                    }
                    
                    String c = getPieceStr(piece);
                    if (piece.getColor() == Piece.WHITE) {
                        c = c.toUpperCase();
                    }
                    fen += c;
                }
            }
            if (emptyCount > 0) {
                fen += emptyCount;
            }
            if (row < 7) {
                fen += "/";
            }
        }
        return fen;
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
        return "?";
    }

}
