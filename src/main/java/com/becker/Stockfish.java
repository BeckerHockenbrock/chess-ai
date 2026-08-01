package com.becker;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class Stockfish {

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
}
