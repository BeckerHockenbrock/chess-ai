package com.becker;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ChessSearcherTest {

    @Test
    void testSearchFindsLegalMove() throws IOException {
        String modelPath = ChessModel.findModelPath();
        Assumptions.assumeTrue(
                Files.exists(Path.of(modelPath)),
                "Skipping ChessSearcherTest because model file does not exist: " + modelPath
        );

        try (ChessModel model = new ChessModel()) {
            model.start();
            Board board = new Board();
            ChessSearcher searcher = new ChessSearcher();

            ChessSearcher.SearchResult result = searcher.search(board, model, 2);
            assertNotNull(result);
            assertNotNull(result.getBestMove());
            assertNotEquals("(none)", result.getBestMove());
            assertTrue(result.getNodes() > 0);
            assertTrue(board.getAllLegalMovesUci().contains(result.getBestMove()));
        }
    }
}
