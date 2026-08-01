package com.becker.pieces;

import java.util.List;

public interface Piece {

    int WHITE = 0;
    int BLACK = 1;

    int getColor();

    String getSymbol();

    List<int[]> getMoves(int row, int col, Piece[][] board);
}
