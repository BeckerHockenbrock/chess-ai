package com.becker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.becker.pieces.Piece;

public class ChessSearcher {

    private final FenCreator fenCreator = new FenCreator();
    private final Map<String, Double> evalCache = new HashMap<>();
    private int nodesEvaluated = 0;

    public static class SearchResult {
        private final String bestMove;
        private final double score;
        private final int depth;
        private final int nodes;
        private final List<String> pvLine;

        public SearchResult(String bestMove, double score, int depth, int nodes, List<String> pvLine) {
            this.bestMove = bestMove;
            this.score = score;
            this.depth = depth;
            this.nodes = nodes;
            this.pvLine = pvLine != null ? pvLine : new ArrayList<>();
        }

        public String getBestMove() {
            return bestMove;
        }

        public double getScore() {
            return score;
        }

        public int getDepth() {
            return depth;
        }

        public int getNodes() {
            return nodes;
        }

        public List<String> getPvLine() {
            return pvLine;
        }
    }

    public SearchResult search(Board rootBoard, ChessModel model, int depth) throws IOException {
        nodesEvaluated = 0;
        evalCache.clear();

        List<String> rootLegalMoves = rootBoard.getAllLegalMovesUci();
        if (rootLegalMoves.isEmpty()) {
            return new SearchResult("(none)", 0.0, depth, 0, List.of());
        }
        if (depth <= 1) {
            String fen = fenCreator.makeFenString(rootBoard);
            ModelPrediction pred = model.predict(fen, rootLegalMoves);
            return new SearchResult(pred.getBestMove(), pred.getValue(), 1, 1, List.of(pred.getBestMove()));
        }

        int maximizingColor = rootBoard.getCurrentTurn();
        String fen = fenCreator.makeFenString(rootBoard);
        ModelPrediction rootPred = model.predict(fen, rootLegalMoves);

        // Sort candidate root moves by policy score
        List<String> sortedMoves = orderMovesByPolicy(rootLegalMoves, rootPred.getTopMoves());

        String bestMove = sortedMoves.get(0);
        double bestScore = (maximizingColor == Piece.WHITE) ? -10000.0 : 10000.0;
        double alpha = -10000.0;
        double beta = 10000.0;

        for (String move : sortedMoves) {
            Board childBoard = rootBoard.copy();
            if (!childBoard.makeUciMove(move)) {
                continue;
            }

            double score = alphaBeta(childBoard, model, depth - 1, alpha, beta, maximizingColor);
            if (maximizingColor == Piece.WHITE) {
                if (score > bestScore) {
                    bestScore = score;
                    bestMove = move;
                }
                alpha = Math.max(alpha, bestScore);
            } else {
                if (score < bestScore) {
                    bestScore = score;
                    bestMove = move;
                }
                beta = Math.min(beta, bestScore);
            }
        }

        return new SearchResult(bestMove, bestScore, depth, nodesEvaluated, List.of(bestMove));
    }

    private double alphaBeta(Board board, ChessModel model, int depth, double alpha, double beta, int maximizingColor) throws IOException {
        nodesEvaluated++;
        int currentTurn = board.getCurrentTurn();

        // Terminal game state checks
        if (board.isCheckmate(currentTurn)) {
            return (currentTurn == Piece.WHITE) ? (-9000.0 - depth) : (9000.0 + depth);
        }
        if (board.isStalemate(currentTurn) || board.getHalfmoveClock() >= 100) {
            return 0.0;
        }

        // Leaf evaluation
        if (depth <= 0) {
            String fen = fenCreator.makeFenString(board);
            Double cached = evalCache.get(fen);
            if (cached != null) {
                return cached;
            }
            double eval = model.evaluate(fen);
            evalCache.put(fen, eval);
            return eval;
        }

        List<String> legalMoves = board.getAllLegalMovesUci();
        if (legalMoves.isEmpty()) {
            return 0.0;
        }

        if (currentTurn == Piece.WHITE) {
            double maxEval = -10000.0;
            for (String move : legalMoves) {
                Board child = board.copy();
                if (!child.makeUciMove(move)) {
                    continue;
                }
                double eval = alphaBeta(child, model, depth - 1, alpha, beta, maximizingColor);
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    break; // Beta cutoff
                }
            }
            return maxEval;
        } else {
            double minEval = 10000.0;
            for (String move : legalMoves) {
                Board child = board.copy();
                if (!child.makeUciMove(move)) {
                    continue;
                }
                double eval = alphaBeta(child, model, depth - 1, alpha, beta, maximizingColor);
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    break; // Alpha cutoff
                }
            }
            return minEval;
        }
    }

    private List<String> orderMovesByPolicy(List<String> legalMoves, List<ModelPrediction.ScoredMove> topMoves) {
        Map<String, Double> scoreMap = new HashMap<>();
        if (topMoves != null) {
            for (ModelPrediction.ScoredMove sm : topMoves) {
                scoreMap.put(sm.getMove(), sm.getScore());
            }
        }
        List<String> sorted = new ArrayList<>(legalMoves);
        sorted.sort(Comparator.comparingDouble((String m) -> scoreMap.getOrDefault(m, -999.0)).reversed());
        return sorted;
    }
}
