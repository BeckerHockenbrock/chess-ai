package com.becker.pieces;

import java.util.ArrayList;
import java.util.List;

public class Pawn implements Piece {

    private final int color;

    public Pawn(int color) {
        this.color = color;
    }

    @Override
    public int getColor() {
        return color;
    }

    @Override
    public String getSymbol() {
        if(color == Piece.WHITE){
            return "/pictures/Chess_plt60.png";
        } else{
            return "/pictures/Chess_pdt60.png";
        }
    }

    @Override
    public List<int[]> getMoves(int row, int col, Piece[][] board) {
        List<int[]> moves = new ArrayList<>();

        int direction;
        if(color == Piece.WHITE){
            direction = -1;
        } else{
            direction = 1;
        }
        int nextRow = row + direction;

        if(nextRow < 0 || nextRow > 7){
            return moves;
        }

        if(board[nextRow][col] == null){
            moves.add(new int[]{nextRow, col});

            int startRow;
            if(color == Piece.WHITE){
                startRow = 6;
            } else{
                startRow = 1;
            }
            int doubleRow = row + direction + direction;
            if(row == startRow && board[doubleRow][col] == null){
                moves.add(new int[]{doubleRow, col});
            }
        }
        if(col +1 <= 7 && board[nextRow][col+1] != null && board[nextRow][col+1].getColor() != color){
            moves.add(new int[]{nextRow, col+1});
        }
        if(col -1 >= 0 && board[nextRow][col-1] != null && board[nextRow][col-1].getColor() != color){
            moves.add(new int[]{nextRow, col-1});
        }

        return moves;
    }

}
