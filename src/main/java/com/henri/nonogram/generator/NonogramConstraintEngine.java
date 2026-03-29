package com.henri.nonogram.generator;

import java.util.ArrayList;
import java.util.List;

public class NonogramConstraintEngine {

    public static final int UNKNOWN = 0;
    public static final int FILLED = 1;
    public static final int EMPTY = -1;

    private final int width;
    private final int height;
    private final int[][] cells;
    private final List<List<Integer>> rowClues;
    private final List<List<Integer>> columnClues;
    private final List<List<boolean[]>> rowDomains;
    private final List<List<boolean[]>> columnDomains;

    public NonogramConstraintEngine(List<List<Integer>> rowClues,
                                    List<List<Integer>> columnClues,
                                    LinePatternCache cache) {
        this.height = rowClues.size();
        this.width = columnClues.size();
        this.cells = new int[height][width];
        this.rowClues = rowClues;
        this.columnClues = columnClues;
        this.rowDomains = new ArrayList<>(height);
        this.columnDomains = new ArrayList<>(width);

        for (List<Integer> clue : rowClues) {
            rowDomains.add(new ArrayList<>(cache.getPatterns(width, clue)));
        }
        for (List<Integer> clue : columnClues) {
            columnDomains.add(new ArrayList<>(cache.getPatterns(height, clue)));
        }
    }

    private NonogramConstraintEngine(NonogramConstraintEngine other) {
        this.width = other.width;
        this.height = other.height;
        this.cells = new int[height][width];
        for (int row = 0; row < height; row++) {
            System.arraycopy(other.cells[row], 0, this.cells[row], 0, width);
        }
        this.rowClues = other.rowClues;
        this.columnClues = other.columnClues;
        this.rowDomains = deepCopyDomains(other.rowDomains);
        this.columnDomains = deepCopyDomains(other.columnDomains);
    }

    public NonogramConstraintEngine copy() {
        return new NonogramConstraintEngine(this);
    }

    public PropagationResult propagate() {
        boolean changedAny = false;
        int forcedMoves = 0;
        int domainReductionCount = 0;
        int passes = 0;

        boolean changed;
        do {
            passes++;
            changed = false;

            for (int row = 0; row < height; row++) {
                FilterResult filterResult = filterRow(row);
                if (filterResult.contradiction) {
                    return PropagationResult.contradiction();
                }
                changed |= filterResult.changed;
                domainReductionCount += filterResult.removedPatterns;

                ForcedResult forcedResult = forceRow(row);
                if (forcedResult.contradiction) {
                    return PropagationResult.contradiction();
                }
                changed |= forcedResult.changed;
                forcedMoves += forcedResult.forcedMoves;
            }

            for (int col = 0; col < width; col++) {
                FilterResult filterResult = filterColumn(col);
                if (filterResult.contradiction) {
                    return PropagationResult.contradiction();
                }
                changed |= filterResult.changed;
                domainReductionCount += filterResult.removedPatterns;

                ForcedResult forcedResult = forceColumn(col);
                if (forcedResult.contradiction) {
                    return PropagationResult.contradiction();
                }
                changed |= forcedResult.changed;
                forcedMoves += forcedResult.forcedMoves;
            }

            if (changed) {
                changedAny = true;
            }
        } while (changed);

        return new PropagationResult(false, changedAny, forcedMoves, domainReductionCount, passes, isSolved());
    }

    public boolean isSolved() {
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                if (cells[row][col] == UNKNOWN) {
                    return false;
                }
            }
        }
        return true;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getCell(int row, int col) {
        return cells[row][col];
    }

    public void assignCell(int row, int col, int value) {
        if (cells[row][col] != UNKNOWN && cells[row][col] != value) {
            throw new IllegalStateException("Contradictory assignment");
        }
        cells[row][col] = value;
    }

    public void assignRowPattern(int row, boolean[] pattern) {
        for (int col = 0; col < width; col++) {
            assignCell(row, col, pattern[col] ? FILLED : EMPTY);
        }
    }

    public void assignColumnPattern(int col, boolean[] pattern) {
        for (int row = 0; row < height; row++) {
            assignCell(row, col, pattern[row] ? FILLED : EMPTY);
        }
    }

    public int chooseBestBranchLineType() {
        int bestSize = Integer.MAX_VALUE;
        int bestType = -1;
        int bestIndex = -1;

        for (int row = 0; row < height; row++) {
            int size = rowDomains.get(row).size();
            if (size > 1 && size < bestSize) {
                bestSize = size;
                bestType = 0;
                bestIndex = row;
            }
        }

        for (int col = 0; col < width; col++) {
            int size = columnDomains.get(col).size();
            if (size > 1 && size < bestSize) {
                bestSize = size;
                bestType = 1;
                bestIndex = col;
            }
        }

        return bestType == -1 ? -1 : (bestType * 10_000 + bestIndex);
    }

    public List<boolean[]> getRowDomain(int row) {
        return rowDomains.get(row);
    }

    public List<boolean[]> getColumnDomain(int col) {
        return columnDomains.get(col);
    }

    public int countUnknownCells() {
        int count = 0;
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                if (cells[row][col] == UNKNOWN) {
                    count++;
                }
            }
        }
        return count;
    }

    public int[][] copyCells() {
        int[][] copy = new int[height][width];
        for (int row = 0; row < height; row++) {
            System.arraycopy(cells[row], 0, copy[row], 0, width);
        }
        return copy;
    }

    private FilterResult filterRow(int row) {
        List<boolean[]> domain = rowDomains.get(row);
        int before = domain.size();
        domain.removeIf(pattern -> !matchesRow(row, pattern));
        if (domain.isEmpty()) {
            return FilterResult.contradiction();
        }
        return new FilterResult(before != domain.size(), before - domain.size(), false);
    }

    private FilterResult filterColumn(int col) {
        List<boolean[]> domain = columnDomains.get(col);
        int before = domain.size();
        domain.removeIf(pattern -> !matchesColumn(col, pattern));
        if (domain.isEmpty()) {
            return FilterResult.contradiction();
        }
        return new FilterResult(before != domain.size(), before - domain.size(), false);
    }

    private ForcedResult forceRow(int row) {
        List<boolean[]> domain = rowDomains.get(row);
        if (domain.isEmpty()) {
            return ForcedResult.contradiction();
        }

        boolean changed = false;
        int forcedMoves = 0;
        for (int col = 0; col < width; col++) {
            Boolean forced = intersectAt(domain, col);
            if (forced == null) {
                continue;
            }

            int value = forced ? FILLED : EMPTY;
            int current = cells[row][col];
            if (current == UNKNOWN) {
                cells[row][col] = value;
                changed = true;
                forcedMoves++;
            } else if (current != value) {
                return ForcedResult.contradiction();
            }
        }
        return new ForcedResult(changed, forcedMoves, false);
    }

    private ForcedResult forceColumn(int col) {
        List<boolean[]> domain = columnDomains.get(col);
        if (domain.isEmpty()) {
            return ForcedResult.contradiction();
        }

        boolean changed = false;
        int forcedMoves = 0;
        for (int row = 0; row < height; row++) {
            Boolean forced = intersectAt(domain, row);
            if (forced == null) {
                continue;
            }

            int value = forced ? FILLED : EMPTY;
            int current = cells[row][col];
            if (current == UNKNOWN) {
                cells[row][col] = value;
                changed = true;
                forcedMoves++;
            } else if (current != value) {
                return ForcedResult.contradiction();
            }
        }
        return new ForcedResult(changed, forcedMoves, false);
    }

    private boolean matchesRow(int row, boolean[] pattern) {
        for (int col = 0; col < width; col++) {
            int current = cells[row][col];
            if (current == UNKNOWN) {
                continue;
            }
            if (current == FILLED && !pattern[col]) {
                return false;
            }
            if (current == EMPTY && pattern[col]) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesColumn(int col, boolean[] pattern) {
        for (int row = 0; row < height; row++) {
            int current = cells[row][col];
            if (current == UNKNOWN) {
                continue;
            }
            if (current == FILLED && !pattern[row]) {
                return false;
            }
            if (current == EMPTY && pattern[row]) {
                return false;
            }
        }
        return true;
    }

    private Boolean intersectAt(List<boolean[]> domain, int index) {
        boolean first = domain.get(0)[index];
        for (int i = 1; i < domain.size(); i++) {
            if (domain.get(i)[index] != first) {
                return null;
            }
        }
        return first;
    }

    private List<List<boolean[]>> deepCopyDomains(List<List<boolean[]>> domains) {
        List<List<boolean[]>> copy = new ArrayList<>(domains.size());
        for (List<boolean[]> domain : domains) {
            copy.add(new ArrayList<>(domain));
        }
        return copy;
    }

    public static class PropagationResult {
        private final boolean contradiction;
        private final boolean changed;
        private final int forcedMoves;
        private final int domainReductionCount;
        private final int passes;
        private final boolean solved;

        private PropagationResult(boolean contradiction,
                                  boolean changed,
                                  int forcedMoves,
                                  int domainReductionCount,
                                  int passes,
                                  boolean solved) {
            this.contradiction = contradiction;
            this.changed = changed;
            this.forcedMoves = forcedMoves;
            this.domainReductionCount = domainReductionCount;
            this.passes = passes;
            this.solved = solved;
        }

        public static PropagationResult contradiction() {
            return new PropagationResult(true, false, 0, 0, 0, false);
        }

        public boolean isContradiction() {
            return contradiction;
        }

        public boolean isChanged() {
            return changed;
        }

        public int getForcedMoves() {
            return forcedMoves;
        }

        public int getDomainReductionCount() {
            return domainReductionCount;
        }

        public int getPasses() {
            return passes;
        }

        public boolean isSolved() {
            return solved;
        }
    }

    private static class FilterResult {
        private final boolean changed;
        private final int removedPatterns;
        private final boolean contradiction;

        private FilterResult(boolean changed, int removedPatterns, boolean contradiction) {
            this.changed = changed;
            this.removedPatterns = removedPatterns;
            this.contradiction = contradiction;
        }

        public static FilterResult contradiction() {
            return new FilterResult(false, 0, true);
        }
    }

    private static class ForcedResult {
        private final boolean changed;
        private final int forcedMoves;
        private final boolean contradiction;

        private ForcedResult(boolean changed, int forcedMoves, boolean contradiction) {
            this.changed = changed;
            this.forcedMoves = forcedMoves;
            this.contradiction = contradiction;
        }

        public static ForcedResult contradiction() {
            return new ForcedResult(false, 0, true);
        }
    }
}