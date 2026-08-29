package com.becker;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ChessModel implements AutoCloseable {

    private final String pythonPath;
    private final String modelPath;
    private Process process;
    private BufferedReader reader;
    private BufferedWriter writer;
    private Thread stderrThread;

    public ChessModel() {
        this(findPythonExecutable(), findModelPath());
    }

    public ChessModel(String pythonPath, String modelPath) {
        this.pythonPath = pythonPath;
        this.modelPath = modelPath;
    }

    public static String findPythonExecutable() {
        String configured = System.getenv("PYTHON_PATH");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        Path venvPythonWin = Path.of(".venv", "Scripts", "python.exe");
        if (Files.exists(venvPythonWin)) {
            return venvPythonWin.toString();
        }
        Path venvPython = Path.of(".venv", "bin", "python");
        if (Files.exists(venvPython)) {
            return venvPython.toString();
        }
        return "python";
    }

    public static String findModelPath() {
        String configured = System.getenv("CHESS_MODEL_PATH");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String prop = System.getProperty("chess.model.path");
        if (prop != null && !prop.isBlank()) {
            return prop;
        }
        Path defaultModel = Path.of("data", "chess_model.pt");
        if (Files.exists(defaultModel)) {
            return defaultModel.toString();
        }
        return "data/chess_model.pt";
    }

    public void start() throws IOException {
        Path scriptPath = Path.of("src", "main", "python", "model_service.py");
        if (!Files.exists(scriptPath)) {
            throw new IOException("Model service script not found at: " + scriptPath);
        }

        ProcessBuilder pb = new ProcessBuilder(
                pythonPath,
                scriptPath.toString(),
                "--model",
                modelPath
        );
        pb.environment().put("PYTHONPATH", "src/main/python");
        pb.redirectErrorStream(false);

        process = pb.start();
        reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

        stderrThread = new Thread(() -> {
            try (BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String errLine;
                while ((errLine = errReader.readLine()) != null) {
                    System.err.println("[ChessModel stderr] " + errLine);
                }
            } catch (IOException ignored) {
            }
        });
        stderrThread.setDaemon(true);
        stderrThread.start();

        String initLine = reader.readLine();
        if (initLine == null || !initLine.startsWith("READY")) {
            if (initLine != null && initLine.startsWith("ERROR")) {
                throw new IOException("ChessModel failed to start: " + initLine);
            }
            throw new IOException("ChessModel service did not initialize properly. Got: " + initLine);
        }
    }

    public synchronized ModelPrediction predict(String fen, List<String> legalMoves) throws IOException {
        if (process == null || !process.isAlive()) {
            throw new IOException("ChessModel process is not running.");
        }

        String movesStr = legalMoves == null || legalMoves.isEmpty() ? "" : String.join(",", legalMoves);
        sendCommand("PREDICT " + fen + " | " + movesStr);

        String response = reader.readLine();
        if (response == null) {
            throw new IOException("ChessModel service terminated unexpectedly.");
        }
        if (response.startsWith("ERROR")) {
            throw new IOException("ChessModel prediction error: " + response);
        }

        return parsePredictionResponse(response);
    }

    public String getBestMove(String fen, List<String> legalMoves) throws IOException {
        return predict(fen, legalMoves).getBestMove();
    }

    public synchronized double evaluate(String fen) throws IOException {
        if (process == null || !process.isAlive()) {
            throw new IOException("ChessModel process is not running.");
        }
        sendCommand("EVALUATE " + fen);
        String response = reader.readLine();
        if (response == null) {
            throw new IOException("ChessModel service terminated unexpectedly.");
        }
        if (response.startsWith("ERROR")) {
            throw new IOException("ChessModel evaluate error: " + response);
        }
        if (response.startsWith("VALUE ")) {
            String[] parts = response.split("\\s+");
            return Double.parseDouble(parts[1]);
        }
        return 0.0;
    }

    private ModelPrediction parsePredictionResponse(String line) {
        String[] tokens = line.split("\\s+");
        String bestMove = "(none)";
        double value = 0.0;
        List<ModelPrediction.ScoredMove> topMoves = new ArrayList<>();

        for (int i = 0; i < tokens.length; i++) {
            if ("BESTMOVE".equalsIgnoreCase(tokens[i]) && i + 1 < tokens.length) {
                bestMove = tokens[i + 1];
            } else if ("VALUE".equalsIgnoreCase(tokens[i]) && i + 1 < tokens.length) {
                try {
                    value = Double.parseDouble(tokens[i + 1]);
                } catch (NumberFormatException ignored) {
                }
            } else if ("TOP".equalsIgnoreCase(tokens[i]) && i + 1 < tokens.length) {
                String[] pairs = tokens[i + 1].split(",");
                for (String pair : pairs) {
                    String[] parts = pair.split(":");
                    if (parts.length == 2) {
                        try {
                            topMoves.add(new ModelPrediction.ScoredMove(parts[0], Double.parseDouble(parts[1])));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        }
        return new ModelPrediction(bestMove, value, topMoves);
    }

    private void sendCommand(String command) throws IOException {
        writer.write(command);
        writer.newLine();
        writer.flush();
    }

    public boolean isAlive() {
        return process != null && process.isAlive();
    }

    @Override
    public synchronized void close() {
        try {
            if (writer != null) {
                sendCommand("QUIT");
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
}
