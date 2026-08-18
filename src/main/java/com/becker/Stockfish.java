package com.becker;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Stockfish implements AutoCloseable {

    private final String path;
    private Process process;
    private BufferedReader reader;
    private BufferedWriter writer;

    public Stockfish() {
        String stockfishPath = System.getenv("STOCKFISH_PATH");
        if (stockfishPath == null || stockfishPath.isBlank()) {
            stockfishPath = "stockfish";
        }
        path = stockfishPath;
    }

    public Stockfish(String path) {
        this.path = path;
    }

    public void start() throws IOException {
        process = new ProcessBuilder(path).redirectErrorStream(true).start();
        reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

        sendCommand("uci");
        readUntil("uciok");
        sendCommand("isready");
        readUntil("readyok");
    }

    public String getBestMove(String fen) throws IOException {
        sendCommand("position fen " + fen);
        sendCommand("go movetime 1000");

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("bestmove ")) {
                String[] parts = line.split(" ");
                return parts[1];
            }
        }
        throw new IOException("Stockfish stopped before choosing a move.");
    }

    public List<StockfishAnalysis> analysePosition(String fen, int depth, int multiPv)
            throws IOException {
        if (depth < 1) {
            throw new IllegalArgumentException("Depth must be at least 1.");
        }
        if (multiPv < 1) {
            throw new IllegalArgumentException("MultiPV must be at least 1.");
        }

        sendCommand("setoption name MultiPV value " + multiPv);
        sendCommand("isready");
        readUntil("readyok");
        sendCommand("position fen " + fen);
        sendCommand("go depth " + depth);

        Map<Integer, StockfishAnalysis> latestAnalyses = new HashMap<>();
        boolean receivedBestMove = false;
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("info ")) {
                StockfishAnalysis analysis = parseAnalysisLine(line);
                if (analysis != null) {
                    latestAnalyses.put(analysis.getMultiPv(), analysis);
                }
            } else if (line.startsWith("bestmove ")) {
                receivedBestMove = true;
                break;
            }
        }

        if (!receivedBestMove) {
            throw new IOException("Stockfish stopped before analysis finished.");
        }

        List<StockfishAnalysis> analyses = new ArrayList<>(latestAnalyses.values());
        analyses.sort(Comparator.comparingInt(StockfishAnalysis::getMultiPv));
        return analyses;
    }

    @Override
    public void close() {
        try {
            if (writer != null) {
                sendCommand("quit");
                writer.close();
            }
            if (reader != null) {
                reader.close();
            }
        } catch (IOException ignored) {
        }

        if (process != null && process.isAlive()) {
            process.destroy();
        }
    }

    private void sendCommand(String command) throws IOException {
        writer.write(command);
        writer.newLine();
        writer.flush();
    }

    private void readUntil(String expected) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.equals(expected)) {
                return;
            }
        }
        throw new IOException("Stockfish stopped while starting.");
    }

    private StockfishAnalysis parseAnalysisLine(String line) {
        String[] parts = line.trim().split("\\s+");
        int multiPv = findInteger(parts, "multipv", 1);
        int depth = findInteger(parts, "depth", 0);
        int scoreIndex = findToken(parts, "score");
        int pvIndex = findToken(parts, "pv");

        if (depth == 0 || scoreIndex < 0 || pvIndex < 0 || scoreIndex + 2 >= parts.length
                || pvIndex + 1 >= parts.length) {
            return null;
        }

        Integer scoreCp = null;
        Integer scoreMate = null;
        String scoreType = parts[scoreIndex + 1];
        try {
            if (scoreType.equals("cp")) {
                scoreCp = Integer.valueOf(parts[scoreIndex + 2]);
            } else if (scoreType.equals("mate")) {
                scoreMate = Integer.valueOf(parts[scoreIndex + 2]);
            } else {
                return null;
            }
        } catch (NumberFormatException exception) {
            return null;
        }

        List<String> principalVariation = new ArrayList<>();
        for (int i = pvIndex + 1; i < parts.length; i++) {
            principalVariation.add(parts[i]);
        }

        return new StockfishAnalysis(
                multiPv,
                depth,
                principalVariation.get(0),
                principalVariation,
                scoreCp,
                scoreMate);
    }

    private int findToken(String[] parts, String token) {
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equals(token)) {
                return i;
            }
        }
        return -1;
    }

    private int findInteger(String[] parts, String token, int defaultValue) {
        int index = findToken(parts, token);
        if (index < 0 || index + 1 >= parts.length) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(parts[index + 1]);
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }
}
