package com.becker;

import java.util.List;

public class StockfishAnalysis {

    private final int multiPv;
    private final int depth;
    private final String firstMove;
    private final List<String> principalVariation;
    private final Integer scoreCp;
    private final Integer scoreMate;

    public StockfishAnalysis(int multiPv, int depth, String firstMove,
                             List<String> principalVariation, Integer scoreCp,
                             Integer scoreMate) {
        this.multiPv = multiPv;
        this.depth = depth;
        this.firstMove = firstMove;
        this.principalVariation = List.copyOf(principalVariation);
        this.scoreCp = scoreCp;
        this.scoreMate = scoreMate;
    }

    public int getMultiPv() {
        return multiPv;
    }

    public int getDepth() {
        return depth;
    }

    public String getFirstMove() {
        return firstMove;
    }

    public List<String> getPrincipalVariation() {
        return principalVariation;
    }

    public Integer getScoreCp() {
        return scoreCp;
    }

    public Integer getScoreMate() {
        return scoreMate;
    }
}
