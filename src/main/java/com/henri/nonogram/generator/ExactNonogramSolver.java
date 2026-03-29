package com.henri.nonogram.generator;

import java.util.List;

public class ExactNonogramSolver {

    private final LinePatternCache cache;

    public ExactNonogramSolver(LinePatternCache cache) {
        this.cache = cache;
    }

    public SolutionCountResult countSolutions(List<List<Integer>> rowClues,
                                              List<List<Integer>> columnClues,
                                              int maxSolutionsToFind) {
        long startedAt = System.nanoTime();
        NonogramConstraintEngine engine = new NonogramConstraintEngine(rowClues, columnClues, cache);
        SearchAccumulator accumulator = new SearchAccumulator(maxSolutionsToFind);
        search(engine, accumulator);
        return new SolutionCountResult(
                accumulator.solutionCount,
                accumulator.firstSolution,
                accumulator.searchNodes,
                System.nanoTime() - startedAt
        );
    }

    private void search(NonogramConstraintEngine engine, SearchAccumulator accumulator) {
        if (accumulator.solutionCount >= accumulator.maxSolutionsToFind) {
            return;
        }

        accumulator.searchNodes++;
        NonogramConstraintEngine.PropagationResult result = engine.propagate();
        if (result.isContradiction()) {
            return;
        }

        if (engine.isSolved()) {
            accumulator.solutionCount++;
            if (accumulator.firstSolution == null) {
                accumulator.firstSolution = engine.copyCells();
            }
            return;
        }

        int branchDescriptor = engine.chooseBestBranchLineType();
        if (branchDescriptor == -1) {
            return;
        }

        int type = branchDescriptor / 10_000;
        int index = branchDescriptor % 10_000;

        if (type == 0) {
            for (boolean[] pattern : engine.getRowDomain(index)) {
                if (accumulator.solutionCount >= accumulator.maxSolutionsToFind) {
                    return;
                }
                NonogramConstraintEngine branched = engine.copy();
                branched.assignRowPattern(index, pattern);
                search(branched, accumulator);
            }
            return;
        }

        for (boolean[] pattern : engine.getColumnDomain(index)) {
            if (accumulator.solutionCount >= accumulator.maxSolutionsToFind) {
                return;
            }
            NonogramConstraintEngine branched = engine.copy();
            branched.assignColumnPattern(index, pattern);
            search(branched, accumulator);
        }
    }

    public static class SolutionCountResult {
        private final int solutionCount;
        private final int[][] firstSolution;
        private final int searchNodes;
        private final long elapsedNanos;

        public SolutionCountResult(int solutionCount, int[][] firstSolution, int searchNodes, long elapsedNanos) {
            this.solutionCount = solutionCount;
            this.firstSolution = firstSolution;
            this.searchNodes = searchNodes;
            this.elapsedNanos = elapsedNanos;
        }

        public int getSolutionCount() {
            return solutionCount;
        }

        public int[][] getFirstSolution() {
            return firstSolution;
        }

        public int getSearchNodes() {
            return searchNodes;
        }

        public long getElapsedNanos() {
            return elapsedNanos;
        }

        public boolean isUnique() {
            return solutionCount == 1;
        }
    }

    private static class SearchAccumulator {
        private final int maxSolutionsToFind;
        private int solutionCount;
        private int[][] firstSolution;
        private int searchNodes;

        private SearchAccumulator(int maxSolutionsToFind) {
            this.maxSolutionsToFind = maxSolutionsToFind;
        }
    }
}