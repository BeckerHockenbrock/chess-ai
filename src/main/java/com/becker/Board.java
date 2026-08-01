package com.becker;

import java.util.ArrayList;
import java.util.List;

import com.becker.pieces.*;

public class Board {

    private Piece[][] grid;
    private int[] enPassantTarget;

    public Board() {
        grid = new Piece[8][8];
        placePeices();
    }

    private void placePeices() {
        for (int col = 0; col < 8; col++) {
            grid[1][col] = new Pawn(Piece.BLACK);
            grid[6][col] = new Pawn(Piece.WHITE);
        }
        grid[0][0] = new Rook(Piece.BLACK);
        grid[0][7] = new Rook(Piece.BLACK);
        grid[7][0] = new Rook(Piece.WHITE);
        grid[7][7] = new Rook(Piece.WHITE);

        grid[0][1] = new Knight(Piece.BLACK);
        grid[0][6] = new Knight(Piece.BLACK);
        grid[7][1] = new Knight(Piece.WHITE);
        grid[7][6] = new Knight(Piece.WHITE);

        grid[0][2] = new Bishop(Piece.BLACK);
        grid[0][5] = new Bishop(Piece.BLACK);
        grid[7][2] = new Bishop(Piece.WHITE);
        grid[7][5] = new Bishop(Piece.WHITE);

        grid[0][3] = new Queen(Piece.BLACK);
        grid[0][4] = new King(Piece.BLACK);

        grid[7][3] = new Queen(Piece.WHITE);
        grid[7][4] = new King(Piece.WHITE);
    }

    public Piece getPiece(int row, int col) {
        return grid[row][col];
    }

    public void setPiece(int row, int col, Piece piece) {
        grid[row][col] = piece;
    }

    public void movePiece(int fromRow, int fromCol, int toRow, int toCol) {
        Piece moving = grid[fromRow][fromCol];

        if (isEnPassant(fromRow, fromCol, toRow, toCol)) {
            grid[fromRow][toCol] = null;
        }

        grid[toRow][toCol] = moving;
        grid[fromRow][fromCol] = null;

        if (moving instanceof Pawn && Math.abs(toRow - fromRow) == 2) {
            enPassantTarget = new int[]{(fromRow + toRow) / 2, fromCol};
        } else {
            enPassantTarget = null;
        }

        if (moving instanceof King) {
            ((King) moving).setMoved();
        } else if (moving instanceof Rook) {
            ((Rook) moving).setMoved();
        }
    }

    public boolean isEnPassant(int fromRow, int fromCol, int toRow, int toCol) {
        Piece moving = grid[fromRow][fromCol];
        return moving instanceof Pawn
                && enPassantTarget != null
                && toRow == enPassantTarget[0] && toCol == enPassantTarget[1]
                && toCol != fromCol;
    }

    public boolean isCastle(int fromRow, int fromCol, int toRow, int toCol) {
        Piece moving = grid[fromRow][fromCol];
        return moving instanceof King && Math.abs(toCol - fromCol) == 2;
    }

    public void castle(int fromRow, int fromCol, int toRow, int toCol) {
        movePiece(fromRow, fromCol, toRow, toCol);
        if (toCol > fromCol) {
            movePiece(fromRow, 7, fromRow, 5);
        } else {
            movePiece(fromRow, 0, fromRow, 3);
        }
    }

    public Piece[][] getGrid() {
        return grid;
    }

    public int[] findKing(int color) {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece piece = grid[r][c];
                if (piece instanceof King && piece.getColor() == color) {
                    return new int[]{r, c};
                }
            }
        }
        return null;
    }

    public boolean isInCheck(int color) {
        int[] pos = findKing(color);
        if (pos == null) {
            return false;
        }
        King king = (King) grid[pos[0]][pos[1]];
        return king.isChecked(pos[0], pos[1], grid);
    }

    public List<int[]> getLegalMoves(int row, int col) {
        Piece piece = grid[row][col];
        if (piece == null) {
            return new ArrayList<>();
        }
        int color = piece.getColor();
        List<int[]> legal = new ArrayList<>();
        for (int[] move : pseudoMoves(row, col)) {
            int toRow = move[0];
            int toCol = move[1];
            Piece captured = grid[toRow][toCol];

            boolean enPassant = isEnPassant(row, col, toRow, toCol);
            Piece passedPawn = grid[row][toCol];

            if (enPassant) {
                grid[row][toCol] = null;
            }

            grid[toRow][toCol] = piece;
            grid[row][col] = null;
            boolean leavesKingInCheck = isInCheck(color);
            grid[row][col] = piece;
            grid[toRow][toCol] = captured;

            if (enPassant) {
                grid[row][toCol] = passedPawn;
            }

            if (!leavesKingInCheck) {
                legal.add(move);
            }
        }
        return legal;
    }

    private List<int[]> pseudoMoves(int row, int col) {
        Piece piece = grid[row][col];
        List<int[]> moves = new ArrayList<>(piece.getMoves(row, col, grid));
        if (piece instanceof Pawn && enPassantTarget != null) {
            int direction;
            if(piece.getColor() == Piece.WHITE){
                direction = -1;
            } else{
                direction = 1;
            }
            if (enPassantTarget[0] == row + direction && Math.abs(enPassantTarget[1] - col) == 1) {
                moves.add(new int[]{enPassantTarget[0], enPassantTarget[1]});
            }
        }
        return moves;
    }

    public boolean hasAnyLegalMove(int color) {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece piece = grid[r][c];
                if (piece != null && piece.getColor() == color && !getLegalMoves(r, c).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isCheckmate(int color) {
        return isInCheck(color) && !hasAnyLegalMove(color);
    }

    public boolean isStalemate(int color) {
        return !isInCheck(color) && !hasAnyLegalMove(color);
    }
}
