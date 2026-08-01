package com.becker.pieces;

import java.util.ArrayList;
import java.util.List;

public class Queen implements Piece{
    private final int color;

    public Queen(int color) {
        this.color = color;
    }

    @Override
    public int getColor() {
        return color;
    }

    @Override
    public String getSymbol() {
        if(color == Piece.WHITE){
            return "/pictures/Chess_qlt60.png";
        } else{
            return "/pictures/Chess_qdt60.png";
        }
    }

    @Override
    public List<int[]> getMoves(int row, int col, Piece[][] board) {
        List<int[]> moves = new ArrayList<>();
        moves.addAll(new Rook(color).getMoves(row, col, board));
        moves.addAll(new Bishop(color).getMoves(row, col, board));
        return moves;
    }
}
