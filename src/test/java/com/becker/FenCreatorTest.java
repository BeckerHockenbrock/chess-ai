package com.becker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FenCreatorTest {

    private final FenCreator fenCreator = new FenCreator();

    @Test
    void createsTheCompleteStartingPositionFen() {
        Board board = new Board();

        assertEquals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                fenCreator.makeFenString(board));
    }

    @Test
    void recordsEnPassantTargetAndSideToMoveAfterDoublePawnAdvance() {
        Board board = new Board();
        board.movePiece(6, 4, 4, 4); // e2-e4

        assertEquals("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
                fenCreator.makeFenString(board));
    }

    @Test
    void recordsCountersAndClearsEnPassantAfterSubsequentMoves() {
        Board board = new Board();
        board.movePiece(6, 4, 4, 4); // e2-e4
        board.movePiece(1, 4, 3, 4); // e7-e5
        board.movePiece(7, 6, 5, 5); // g1-f3
        board.movePiece(0, 1, 2, 2); // b8-c6

        assertEquals("r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3",
                fenCreator.makeFenString(board));
    }

    @Test
    void recordsCastlingRightsAfterTheKingMoves() {
        Board board = new Board();
        board.movePiece(6, 4, 5, 4); // e2-e3
        board.movePiece(1, 4, 2, 4); // e7-e6
        board.movePiece(7, 5, 6, 4); // f1-e2
        board.movePiece(0, 5, 1, 4); // f8-e7
        board.castle(7, 4, 7, 6); // O-O

        assertEquals("kq", board.getCastlingRights());
        assertEquals("b", fenCreator.makeFenString(board).split(" ")[1]);
    }
}
