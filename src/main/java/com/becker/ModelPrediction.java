package com.becker;

import java.util.Collections;
import java.util.List;

public class ModelPrediction {
    private final String bestMove;
    private final double value;
    private final List<ScoredMove> topMoves;

    public ModelPrediction(String bestMove, double value, List<ScoredMove> topMoves) {
        this.bestMove = bestMove;
        this.value = value;
        this.topMoves = topMoves != null ? Collections.unmodifiableList(topMoves) : Collections.emptyList();
    }

    public String getBestMove() {
        return bestMove;
    }

    public double getValue() {
        return value;
    }

    public List<ScoredMove> getTopMoves() {
        return topMoves;
    }

    public static class ScoredMove {
        private final String move;
        private final double score;

        public ScoredMove(String move, double score) {
            this.move = move;
            this.score = score;
        }

        public String getMove() {
            return move;
        }

        public double getScore() {
            return score;
        }

        @Override
        public String toString() {
            return move + ":" + String.format("%.3f", score);
        }
    }
}
