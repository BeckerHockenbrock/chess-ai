package com.becker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StockfishTest {

    @TempDir
    Path tempFolder;

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private Path createMockStockfish(String mode, Path receivedCommand) throws IOException {
        String javaBin = ProcessHandle.current().info().command().orElse("java");
        String classPath = System.getProperty("java.class.path");
        String commandArg = receivedCommand != null ? receivedCommand.toAbsolutePath().toString() : "";

        if (isWindows()) {
            Path script = tempFolder.resolve("mock_stockfish_" + mode + ".cmd");
            String content = "@echo off\r\n\"" + javaBin + "\" -cp \"" + classPath + "\" "
                    + MockStockfishRunner.class.getName() + " " + mode + " \"" + commandArg + "\"\r\n";
            Files.writeString(script, content);
            return script;
        } else {
            Path script = tempFolder.resolve("mock_stockfish_" + mode);
            String content = "#!/bin/sh\nexec \"" + javaBin + "\" -cp \"" + classPath + "\" "
                    + MockStockfishRunner.class.getName() + " " + mode + " \"" + commandArg + "\"\n";
            Files.writeString(script, content);
            script.toFile().setExecutable(true);
            return script;
        }
    }

    @Test
    void sendsFenAndReadsBestMove() throws IOException {
        Path receivedCommand = tempFolder.resolve("command.txt");
        Path fakeStockfish = createMockStockfish("simple", receivedCommand);

        Stockfish stockfish = new Stockfish(fakeStockfish.toString());
        stockfish.start();

        assertEquals("e7e5", stockfish.getBestMove("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"));
        assertEquals("position fen rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1",
                Files.readString(receivedCommand).strip());

        stockfish.close();
    }

    @Test
    void parsesMultiPvAnalysis() throws IOException {
        Path fakeStockfish = createMockStockfish("multipv", null);

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

    public static class MockStockfishRunner {
        public static void main(String[] args) throws IOException {
            String mode = args.length > 0 ? args[0] : "simple";
            Path receivedCommand = args.length > 1 && !args[1].isBlank() ? Path.of(args[1]) : null;
            try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in))) {
                String line;
                while ((line = in.readLine()) != null) {
                    line = line.trim();
                    if ("uci".equals(line)) {
                        System.out.println("id name Test Stockfish");
                        System.out.println("uciok");
                        System.out.flush();
                    } else if ("isready".equals(line)) {
                        System.out.println("readyok");
                        System.out.flush();
                    } else if (line.startsWith("position")) {
                        if (receivedCommand != null) {
                            Files.writeString(receivedCommand, line);
                        }
                    } else if (line.startsWith("go")) {
                        if ("multipv".equals(mode)) {
                            System.out.println("info depth 8 multipv 1 score cp 35 pv e2e4 e7e5 g1f3");
                            System.out.println("info depth 8 multipv 2 score mate 3 pv d2d4 d7d5");
                            System.out.println("bestmove e2e4");
                            System.out.flush();
                        } else {
                            System.out.println("bestmove e7e5");
                            System.out.flush();
                        }
                    } else if ("quit".equals(line)) {
                        break;
                    }
                }
            }
        }
    }
}
