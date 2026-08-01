package com.becker;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
