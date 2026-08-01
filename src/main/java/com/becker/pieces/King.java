package com.becker.pieces;

import java.util.ArrayList;
import java.util.List;

public class King implements Piece{
    private final int color;
    private boolean hasMoved = false;

    public King(int color) {
        this.color = color;
    }

    public boolean getHasMoved() {
        return hasMoved;
    }

    public void setMoved() {
        hasMoved = true;
    }

    @Override
    public int getColor() {
        return color;
    }

    @Override
    public String getSymbol() {
        if(color == Piece.WHITE){
            return "/pictures/Chess_klt60.png";
        } else{
            return "/pictures/Chess_kdt60.png";
        }
    }

    @Override
    public List<int[]> getMoves(int row, int col, Piece[][] board) {
        List<int[]> moves = new ArrayList<>();
        int[][] steps = {{-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}};

        for (int[] step : steps) {
            int r = row + step[0];
            int c = col + step[1];
            if (r < 0 || r > 7 || c < 0 || c > 7) {
                continue;
            }
            Piece target = board[r][c];
            if (target != null && target.getColor() == color) {
                continue;
            }

            board[r][c] = this;
            board[row][col] = null;
            boolean inCheck = isChecked(r, c, board);
            board[row][col] = this;
            board[r][c] = target;

            if (!inCheck) {
                moves.add(new int[]{r, c});
            }
        }

        if (!hasMoved && !isChecked(row, col, board)) {
            addCastleMoves(row, col, board, moves);
        }

        return moves;
    }

    private void addCastleMoves(int row, int col, Piece[][] board, List<int[]> moves) {
        Piece kingsideRook = board[row][7];
        if (kingsideRook instanceof Rook && kingsideRook.getColor() == color && !((Rook) kingsideRook).getHasMoved()) {
            if (board[row][5] == null && board[row][6] == null) {
                if (!isChecked(row, 5, board) && !isChecked(row, 6, board)) {
                    moves.add(new int[]{row, 6});
                }
            }
        }

        Piece queensideRook = board[row][0];
        if (queensideRook instanceof Rook && queensideRook.getColor() == color && !((Rook) queensideRook).getHasMoved()) {
            if (board[row][1] == null && board[row][2] == null && board[row][3] == null) {
                if (!isChecked(row, 3, board) && !isChecked(row, 2, board)) {
                    moves.add(new int[]{row, 2});
                }
            }
        }
    }

    public boolean isChecked(int row, int col, Piece[][] board) {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece attacker = board[r][c];
                if (attacker == null) {
                    continue;
                }
                if (attacker.getColor() == color) {
                    continue;
                }
                if (attacker instanceof King) {
                    int rowDiff = Math.abs(r - row);
                    int colDiff = Math.abs(c - col);
                    if (rowDiff <= 1 && colDiff <= 1) {
                        return true;
                    }
                    continue;
                }
                List<int[]> moves = attacker.getMoves(r, c, board);
                for (int[] move : moves) {
                    if (move[0] == row && move[1] == col) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
