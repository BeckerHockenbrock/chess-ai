package com.becker;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChessModelTest {

    @Test
    void testChessModelStartupAndPrediction() throws IOException {
        String modelPath = ChessModel.findModelPath();
        Assumptions.assumeTrue(
                Files.exists(Path.of(modelPath)),
                "Skipping ChessModelTest because model file does not exist: " + modelPath
        );

        try (ChessModel model = new ChessModel()) {
            model.start();
            assertTrue(model.isAlive());

            String startFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
            List<String> legalMoves = List.of("e2e4", "d2d4", "g1f3", "b1c3", "c2c4");

            ModelPrediction prediction = model.predict(startFen, legalMoves);
            assertNotNull(prediction);
            assertNotNull(prediction.getBestMove());
            assertTrue(legalMoves.contains(prediction.getBestMove()));
            assertFalse(prediction.getTopMoves().isEmpty());

            double eval = model.evaluate(startFen);
            assertTrue(eval >= -1.0 && eval <= 1.0);
        }
    }
}
