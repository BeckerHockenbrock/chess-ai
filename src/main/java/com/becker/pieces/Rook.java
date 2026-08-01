package com.becker.pieces;

import java.util.ArrayList;
import java.util.List;

public class Rook implements Piece{
    private final int color;
    private boolean hasMoved = false;

    public Rook(int color) {
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
            return "/pictures/Chess_rlt60.png";
        } else{
            return "/pictures/Chess_rdt60.png";
        }
    }

    @Override
    public List<int[]> getMoves(int row, int col, Piece[][] board) {
        List<int[]> moves = new ArrayList<>();
        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

        for (int[] dir : directions) {
            int r = row + dir[0];
            int c = col + dir[1];
            while (r >= 0 && r < 8 && c >= 0 && c < 8) {
                Piece target = board[r][c];
                if (target == null) {
                    moves.add(new int[]{r, c});
                } else {
                    if (target.getColor() != color) {
                        moves.add(new int[]{r, c});
                    }
                    break;
                }
                r += dir[0];
                c += dir[1];
            }
        }

        return moves;
    }
}
