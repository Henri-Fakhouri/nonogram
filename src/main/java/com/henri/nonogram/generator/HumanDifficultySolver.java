package com.henri.nonogram.generator;

import java.util.ArrayList;
import java.util.List;

public class HumanDifficultySolver {

    private final LinePatternCache cache;

    public HumanDifficultySolver(LinePatternCache cache) {
        this.cache = cache;
    }

    public DifficultyEstimate analyze(List<List<Integer>> rowClues, List<List<Integer>> columnClues) {
        NonogramConstraintEngine engine = new NonogramConstraintEngine(rowClues, columnClues, cache);

        int deterministicPasses = 0;
        int forcedMoves = 0;
        int domainReductions = 0;
        int lookaheadSteps = 0;
        int maxTechniqueTier = 1;
        boolean usedBacktracking = false;
        List<String> trace = new ArrayList<>();

        while (true) {
            NonogramConstraintEngine.PropagationResult result = engine.propagate();
            if (result.isContradiction()) {
                return new DifficultyEstimate(false, true, 4, Integer.MAX_VALUE,
                        deterministicPasses, forcedMoves, domainReductions, lookaheadSteps, 0, trace);
            }

            deterministicPasses += result.getPasses();
            forcedMoves += result.getForcedMoves();
            domainReductions += result.getDomainReductionCount();

            if (engine.isSolved()) {
                int score = computeScore(forcedMoves, domainReductions, deterministicPasses, lookaheadSteps, false);
                return new DifficultyEstimate(true, false, maxTechniqueTier, score,
                        deterministicPasses, forcedMoves, domainReductions, lookaheadSteps, 0, trace);
            }

            LookaheadResult lookaheadResult = applySingleCellLookahead(engine);
            if (lookaheadResult.contradiction) {
                return new DifficultyEstimate(false, true, 4, Integer.MAX_VALUE,
                        deterministicPasses, forcedMoves, domainReductions, lookaheadSteps, 0, trace);
            }

            if (!lookaheadResult.progressMade) {
                usedBacktracking = true;
                break;
            }

            maxTechniqueTier = 3;
            lookaheadSteps += lookaheadResult.forcedAssignments;
            trace.add("Lookahead forced " + lookaheadResult.forcedAssignments + " cells");
        }

        int score = computeScore(forcedMoves, domainReductions, deterministicPasses, lookaheadSteps, true);
        return new DifficultyEstimate(false, usedBacktracking, 4, score,
                deterministicPasses, forcedMoves, domainReductions, lookaheadSteps, 1, trace);
    }

    private LookaheadResult applySingleCellLookahead(NonogramConstraintEngine engine) {
        for (int row = 0; row < engine.getHeight(); row++) {
            for (int col = 0; col < engine.getWidth(); col++) {
                if (engine.getCell(row, col) != NonogramConstraintEngine.UNKNOWN) {
                    continue;
                }

                boolean filledImpossible = assumptionContradicts(engine, row, col, NonogramConstraintEngine.FILLED);
                boolean emptyImpossible = assumptionContradicts(engine, row, col, NonogramConstraintEngine.EMPTY);

                if (filledImpossible && emptyImpossible) {
                    return LookaheadResult.contradiction();
                }

                if (filledImpossible) {
                    engine.assignCell(row, col, NonogramConstraintEngine.EMPTY);
                    return new LookaheadResult(true, 1, false);
                }

                if (emptyImpossible) {
                    engine.assignCell(row, col, NonogramConstraintEngine.FILLED);
                    return new LookaheadResult(true, 1, false);
                }
            }
        }

        return new LookaheadResult(false, 0, false);
    }

    private boolean assumptionContradicts(NonogramConstraintEngine engine, int row, int col, int value) {
        NonogramConstraintEngine copy = engine.copy();
        copy.assignCell(row, col, value);
        return copy.propagate().isContradiction();
    }

    private int computeScore(int forcedMoves,
                             int domainReductions,
                             int deterministicPasses,
                             int lookaheadSteps,
                             boolean usedBacktracking) {
        int score = 0;
        score += forcedMoves * 3;
        score += domainReductions;
        score += deterministicPasses * 12;
        score += lookaheadSteps * 90;
        if (usedBacktracking) {
            score += 500;
        }
        return score;
    }

    public static class DifficultyEstimate {
        private final boolean solved;
        private final boolean usedBacktracking;
        private final int maxTechniqueTier;
        private final int score;
        private final int deterministicPasses;
        private final int forcedMoves;
        private final int domainReductions;
        private final int lookaheadSteps;
        private final int branchingCount;
        private final List<String> trace;

        public DifficultyEstimate(boolean solved,
                                  boolean usedBacktracking,
                                  int maxTechniqueTier,
                                  int score,
                                  int deterministicPasses,
                                  int forcedMoves,
                                  int domainReductions,
                                  int lookaheadSteps,
                                  int branchingCount,
                                  List<String> trace) {
            this.solved = solved;
            this.usedBacktracking = usedBacktracking;
            this.maxTechniqueTier = maxTechniqueTier;
            this.score = score;
            this.deterministicPasses = deterministicPasses;
            this.forcedMoves = forcedMoves;
            this.domainReductions = domainReductions;
            this.lookaheadSteps = lookaheadSteps;
            this.branchingCount = branchingCount;
            this.trace = trace;
        }

        public boolean isSolved() {
            return solved;
        }

        public boolean isUsedBacktracking() {
            return usedBacktracking;
        }

        public int getMaxTechniqueTier() {
            return maxTechniqueTier;
        }

        public int getScore() {
            return score;
        }

        public int getDeterministicPasses() {
            return deterministicPasses;
        }

        public int getForcedMoves() {
            return forcedMoves;
        }

        public int getDomainReductions() {
            return domainReductions;
        }

        public int getLookaheadSteps() {
            return lookaheadSteps;
        }

        public int getBranchingCount() {
            return branchingCount;
        }

        public List<String> getTrace() {
            return trace;
        }
    }

    private static class LookaheadResult {
        private final boolean progressMade;
        private final int forcedAssignments;
        private final boolean contradiction;

        private LookaheadResult(boolean progressMade, int forcedAssignments, boolean contradiction) {
            this.progressMade = progressMade;
            this.forcedAssignments = forcedAssignments;
            this.contradiction = contradiction;
        }

        public static LookaheadResult contradiction() {
            return new LookaheadResult(false, 0, true);
        }
    }
}