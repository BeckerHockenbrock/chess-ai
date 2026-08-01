package com.becker.pieces;

import java.util.ArrayList;
import java.util.List;

public class Knight implements Piece{
    
    private final int color;

    public Knight(int color) {
        this.color = color;
    }

    @Override
    public int getColor() {
        return color;
    }

    @Override
    public String getSymbol() {
        if(color == Piece.WHITE){
            return "/pictures/Chess_nlt60.png";
        } else{
            return "/pictures/Chess_ndt60.png";
        }
    }

    @Override
    public List<int[]> getMoves(int row, int col, Piece[][] board) {
        List<int[]> moves = new ArrayList<>();
        int[][] jumps = {{-2, -1}, {-2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}, {2, -1}, {2, 1}};

        for (int[] jump : jumps) {
            int r = row + jump[0];
            int c = col + jump[1];
            if (r >= 0 && r < 8 && c >= 0 && c < 8) {
                Piece target = board[r][c];
                if (target == null) {
                    moves.add(new int[]{r, c});
                } else if (target.getColor() != color) {
                    moves.add(new int[]{r, c});
                }
            }
        }

        return moves;
    }
}
