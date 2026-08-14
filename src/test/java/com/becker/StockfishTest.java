package com.becker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StockfishTest {

    @TempDir
    Path tempFolder;

    @Test
    void sendsFenAndReadsBestMove() throws IOException {
        Path fakeStockfish = tempFolder.resolve("stockfish");
        Path receivedCommand = tempFolder.resolve("command.txt");
        String script = "#!/bin/sh\n"
                + "while IFS= read -r line\n"
                + "do\n"
                + "  case \"$line\" in\n"
                + "    uci) echo 'id name Test Stockfish'; echo 'uciok' ;;\n"
                + "    isready) echo 'readyok' ;;\n"
                + "    position*) echo \"$line\" > '" + receivedCommand + "' ;;\n"
                + "    go*) echo 'bestmove e7e5' ;;\n"
                + "    quit) exit 0 ;;\n"
                + "  esac\n"
                + "done\n";
        Files.writeString(fakeStockfish, script);
        fakeStockfish.toFile().setExecutable(true);

        Stockfish stockfish = new Stockfish(fakeStockfish.toString());
        stockfish.start();

        assertEquals("e7e5", stockfish.getBestMove("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"));
        assertEquals("position fen rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1",
                Files.readString(receivedCommand).strip());

        stockfish.close();
    }

    @Test
    void parsesMultiPvAnalysis() throws IOException {
        Path fakeStockfish = tempFolder.resolve("stockfish-multipv");
        String script = "#!/bin/sh\n"
                + "while IFS= read -r line\n"
                + "do\n"
                + "  case \"$line\" in\n"
                + "    uci) echo 'id name Test Stockfish'; echo 'uciok' ;;\n"
                + "    isready) echo 'readyok' ;;\n"
                + "    go*)\n"
                + "      echo 'info depth 8 multipv 1 score cp 35 pv e2e4 e7e5 g1f3'\n"
                + "      echo 'info depth 8 multipv 2 score mate 3 pv d2d4 d7d5'\n"
                + "      echo 'bestmove e2e4' ;;\n"
                + "    quit) exit 0 ;;\n"
                + "  esac\n"
                + "done\n";
        Files.writeString(fakeStockfish, script);
        fakeStockfish.toFile().setExecutable(true);

        Stockfish stockfish = new Stockfish(fakeStockfish.toString());
        stockfish.start();

        List<StockfishAnalysis> analyses = stockfish.analysePosition("startpos", 8, 2);

        assertEquals(2, analyses.size());
        assertEquals(1, analyses.get(0).getMultiPv());
        assertEquals("e2e4", analyses.get(0).getFirstMove());
        assertEquals(List.of("e2e4", "e7e5", "g1f3"),
                analyses.get(0).getPrincipalVariation());
        assertEquals(35, analyses.get(0).getScoreCp());
        assertEquals(2, analyses.get(1).getMultiPv());
        assertEquals("d2d4", analyses.get(1).getFirstMove());
        assertEquals(3, analyses.get(1).getScoreMate());

        stockfish.close();
    }
}
