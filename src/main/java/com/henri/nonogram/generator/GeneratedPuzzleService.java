package com.henri.nonogram.generator;

import com.henri.nonogram.model.Puzzle;
import com.henri.nonogram.service.ClueGenerator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Random;

public class GeneratedPuzzleService {

    private static final int MAX_ATTEMPTS = 700;

    private final ClueGenerator clueGenerator = new ClueGenerator();
    private final LinePatternCache cache = new LinePatternCache();
    private final ExactNonogramSolver exactSolver = new ExactNonogramSolver(cache);
    private final HumanDifficultySolver difficultySolver = new HumanDifficultySolver(cache);

    public Puzzle generate(GeneratedDifficulty difficulty, long seed) {
        return generate(difficulty.getDefaultWidth(), difficulty.getDefaultHeight(), difficulty, seed);
    }

    public Puzzle generate(int width, int height, GeneratedDifficulty difficulty, long seed) {
        Candidate bestCandidate = null;

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            boolean[][] grid = buildCandidateGrid(width, height, difficulty, seed, attempt);
            int aestheticsScore = scoreAesthetics(grid, difficulty);
            if (aestheticsScore < 45) {
                continue;
            }

            List<List<Integer>> rowClues = clueGenerator.generateRowClues(grid);
            List<List<Integer>> columnClues = clueGenerator.generateColumnClues(grid);

            ExactNonogramSolver.SolutionCountResult exactResult = exactSolver.countSolutions(rowClues, columnClues, 2);
            if (!exactResult.isUnique()) {
                continue;
            }

            HumanDifficultySolver.DifficultyEstimate difficultyEstimate = difficultySolver.analyze(rowClues, columnClues);
            if (!isAcceptableForDifficulty(difficulty, difficultyEstimate)) {
                continue;
            }

            Candidate candidate = new Candidate(grid, aestheticsScore, difficultyEstimate);
            if (difficulty.matchesScore(difficultyEstimate.getScore(), difficultyEstimate.isUsedBacktracking())) {
                return buildPuzzle(candidate.grid, difficulty, seed, attempt);
            }

            if (bestCandidate == null || candidate.distanceTo(difficulty) < bestCandidate.distanceTo(difficulty)) {
                bestCandidate = candidate;
            }
        }

        if (bestCandidate != null) {
            return buildPuzzle(bestCandidate.grid, difficulty, seed, -1);
        }

        throw new IllegalStateException("Failed to generate a puzzle for " + difficulty.getDisplayName());
    }

    private Puzzle buildPuzzle(boolean[][] grid, GeneratedDifficulty difficulty, long seed, int attempt) {
        Puzzle puzzle = new Puzzle();
        puzzle.setId(buildPuzzleId(grid, difficulty, seed, attempt));
        puzzle.setTitle(buildTitle(difficulty, grid[0].length, grid.length, seed));
        puzzle.setPack("Endless");
        puzzle.setDifficulty(difficulty.getDisplayName());
        puzzle.setWidth(grid[0].length);
        puzzle.setHeight(grid.length);
        puzzle.setSolution(copyGrid(grid));
        return puzzle;
    }

    private String buildPuzzleId(boolean[][] grid, GeneratedDifficulty difficulty, long seed, int attempt) {
        long hash = 1125899906842597L;
        for (boolean[] row : grid) {
            for (boolean cell : row) {
                hash = 31 * hash + (cell ? 1 : 0);
            }
        }
        return "generated_" + grid[0].length + "x" + grid.length + "_"
                + difficulty.name().toLowerCase() + "_" + seed + "_" + attempt + "_" + Long.toUnsignedString(hash, 36);
    }

private String buildTitle(GeneratedDifficulty difficulty, int width, int height, long seed) {
    return "Endless " + difficulty.getDisplayName() + " " + width + "x" + height;
}

    private boolean[][] buildCandidateGrid(int width, int height, GeneratedDifficulty difficulty, long seed, int attempt) {
        Random random = new Random(seed ^ (0x9E3779B97F4A7C15L * (attempt + 1L)));
        double[][] field = new double[height][width];

        int blobCount = switch (difficulty) {
            case EASY -> 3 + random.nextInt(2);
            case MEDIUM -> 4 + random.nextInt(2);
            case HARD -> 5 + random.nextInt(3);
            case EXPERT -> 8 + random.nextInt(4);
        };

        for (int i = 0; i < blobCount; i++) {
            stampBlob(field, random, difficulty, true);
        }

        int carveCount = switch (difficulty) {
            case EASY -> 0;
            case MEDIUM, HARD -> 1 + random.nextInt(2);
            case EXPERT -> 2 + random.nextInt(3);
        };
        for (int i = 0; i < carveCount; i++) {
            stampBlob(field, random, difficulty, false);
        }

        int smoothingSteps = switch (difficulty) {
            case EASY -> 2;
            case MEDIUM, HARD -> 1;
            case EXPERT -> random.nextBoolean() ? 1 : 0;
        };
        for (int i = 0; i < smoothingSteps; i++) {
            field = smooth(field);
        }

        boolean mirrorVertically = switch (difficulty) {
            case EASY, MEDIUM -> true;
            case HARD -> random.nextBoolean();
            case EXPERT -> random.nextInt(4) == 0;
        };
        boolean mirrorHorizontally = switch (difficulty) {
            case EASY -> random.nextBoolean();
            case MEDIUM -> random.nextInt(4) == 0;
            case HARD, EXPERT -> false;
        };

        if (mirrorVertically) {
            applyVerticalMirror(field);
        }
        if (mirrorHorizontally) {
            applyHorizontalMirror(field);
        }

        double threshold = chooseThreshold(field, difficulty);
        boolean[][] grid = threshold(field, threshold);

        cleanupGrid(grid, difficulty);
        ensureNotEmptyOrFull(grid, random);
        retainLargestComponent(grid);
        cleanupGrid(grid, difficulty);

        // Hard guarantee:
        // generated puzzles must never contain a fully empty row or column.
        ensureNoEmptyRowsOrColumns(grid, random);

        return grid;
    }

    private void stampBlob(double[][] field, Random random, GeneratedDifficulty difficulty, boolean additive) {
        int height = field.length;
        int width = field[0].length;

        double centerRow = random.nextDouble() * (height - 1);
        double centerCol = random.nextDouble() * (width - 1);
        double radiusRow = switch (difficulty) {
            case EASY -> 1.6 + random.nextDouble() * 1.8;
            case MEDIUM -> 1.4 + random.nextDouble() * 1.7;
            case HARD -> 1.2 + random.nextDouble() * 1.5;
            case EXPERT -> 1.8 + random.nextDouble() * 2.8;
        };
        double radiusCol = switch (difficulty) {
            case EASY -> 1.6 + random.nextDouble() * 1.8;
            case MEDIUM -> 1.4 + random.nextDouble() * 1.7;
            case HARD -> 1.2 + random.nextDouble() * 1.5;
            case EXPERT -> 1.8 + random.nextDouble() * 2.8;
        };
        double intensity = additive ? (0.9 + random.nextDouble() * 0.9) : -(0.7 + random.nextDouble() * 0.7);

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                double dr = (row - centerRow) / radiusRow;
                double dc = (col - centerCol) / radiusCol;
                double distance = dr * dr + dc * dc;
                if (distance > 2.6) {
                    continue;
                }
                double influence = Math.exp(-distance);
                field[row][col] += influence * intensity;
            }
        }
    }

    private double[][] smooth(double[][] source) {
        int height = source.length;
        int width = source[0].length;
        double[][] smoothed = new double[height][width];

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                double total = source[row][col] * 2.0;
                double weight = 2.0;

                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) {
                            continue;
                        }
                        int nr = row + dy;
                        int nc = col + dx;
                        if (nr < 0 || nr >= height || nc < 0 || nc >= width) {
                            continue;
                        }
                        total += source[nr][nc];
                        weight += 1.0;
                    }
                }

                smoothed[row][col] = total / weight;
            }
        }

        return smoothed;
    }

    private void applyVerticalMirror(double[][] field) {
        int height = field.length;
        int width = field[0].length;
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width / 2; col++) {
                double average = (field[row][col] + field[row][width - 1 - col]) / 2.0;
                field[row][col] = average;
                field[row][width - 1 - col] = average;
            }
        }
    }

    private void applyHorizontalMirror(double[][] field) {
        int height = field.length;
        int width = field[0].length;
        for (int row = 0; row < height / 2; row++) {
            for (int col = 0; col < width; col++) {
                double average = (field[row][col] + field[height - 1 - row][col]) / 2.0;
                field[row][col] = average;
                field[height - 1 - row][col] = average;
            }
        }
    }

    private double chooseThreshold(double[][] field, GeneratedDifficulty difficulty) {
        List<Double> values = new ArrayList<>();
        for (double[] row : field) {
            for (double value : row) {
                values.add(value);
            }
        }
        values.sort(Comparator.naturalOrder());

        double percentile = switch (difficulty) {
            case EASY, MEDIUM, HARD -> 0.50;
            case EXPERT -> 0.48;
        };

        int index = (int) Math.max(0, Math.min(values.size() - 1, Math.round((float) (values.size() * percentile))));
        return values.get(index);
    }

    private boolean[][] threshold(double[][] field, double threshold) {
        int height = field.length;
        int width = field[0].length;
        boolean[][] grid = new boolean[height][width];
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                grid[row][col] = field[row][col] >= threshold;
            }
        }
        return grid;
    }

    private void cleanupGrid(boolean[][] grid, GeneratedDifficulty difficulty) {
        int height = grid.length;
        int width = grid[0].length;
        boolean[][] copy = copyGrid(grid);

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int filledNeighbors = countFilledNeighbors(copy, row, col);
                if (copy[row][col] && filledNeighbors <= 1) {
                    grid[row][col] = false;
                } else if (!copy[row][col]
                        && filledNeighbors >= 6
                        && (difficulty == GeneratedDifficulty.EASY || difficulty == GeneratedDifficulty.MEDIUM)) {
                    grid[row][col] = true;
                }
            }
        }
    }

    private void ensureNotEmptyOrFull(boolean[][] grid, Random random) {
        int height = grid.length;
        int width = grid[0].length;
        int filled = 0;
        for (boolean[] row : grid) {
            for (boolean cell : row) {
                if (cell) {
                    filled++;
                }
            }
        }

        if (filled == 0) {
            grid[random.nextInt(height)][random.nextInt(width)] = true;
        } else if (filled == width * height) {
            grid[random.nextInt(height)][random.nextInt(width)] = false;
        }
    }

    private void ensureNoEmptyRowsOrColumns(boolean[][] grid, Random random) {
        int height = grid.length;
        int width = grid[0].length;

        for (int row = 0; row < height; row++) {
            if (countFilledInRow(grid, row) == 0) {
                int col = chooseBestColumnForEmptyRow(grid, row, random);
                grid[row][col] = true;
            }
        }

        for (int col = 0; col < width; col++) {
            if (countFilledInColumn(grid, col) == 0) {
                int row = chooseBestRowForEmptyColumn(grid, col, random);
                grid[row][col] = true;
            }
        }
    }

    private int chooseBestColumnForEmptyRow(boolean[][] grid, int row, Random random) {
        int width = grid[0].length;
        int bestScore = Integer.MIN_VALUE;
        List<Integer> bestColumns = new ArrayList<>();

        for (int col = 0; col < width; col++) {
            int score = scorePlacement(grid, row, col);

            if (countFilledInColumn(grid, col) > 0) {
                score += 4;
            }

            if ((row > 0 && grid[row - 1][col]) || (row + 1 < grid.length && grid[row + 1][col])) {
                score += 3;
            }

            if (score > bestScore) {
                bestScore = score;
                bestColumns.clear();
                bestColumns.add(col);
            } else if (score == bestScore) {
                bestColumns.add(col);
            }
        }

        return bestColumns.get(random.nextInt(bestColumns.size()));
    }

    private int chooseBestRowForEmptyColumn(boolean[][] grid, int col, Random random) {
        int height = grid.length;
        int bestScore = Integer.MIN_VALUE;
        List<Integer> bestRows = new ArrayList<>();

        for (int row = 0; row < height; row++) {
            int score = scorePlacement(grid, row, col);

            if (countFilledInRow(grid, row) > 0) {
                score += 4;
            }

            if ((col > 0 && grid[row][col - 1]) || (col + 1 < grid[0].length && grid[row][col + 1])) {
                score += 3;
            }

            if (score > bestScore) {
                bestScore = score;
                bestRows.clear();
                bestRows.add(row);
            } else if (score == bestScore) {
                bestRows.add(row);
            }
        }

        return bestRows.get(random.nextInt(bestRows.size()));
    }

    private int scorePlacement(boolean[][] grid, int row, int col) {
        int score = countFilledNeighbors(grid, row, col) * 2;

        if (row > 0 && grid[row - 1][col]) {
            score += 2;
        }
        if (row + 1 < grid.length && grid[row + 1][col]) {
            score += 2;
        }
        if (col > 0 && grid[row][col - 1]) {
            score += 2;
        }
        if (col + 1 < grid[0].length && grid[row][col + 1]) {
            score += 2;
        }

        return score;
    }

    private int countFilledInRow(boolean[][] grid, int row) {
        int count = 0;
        for (int col = 0; col < grid[row].length; col++) {
            if (grid[row][col]) {
                count++;
            }
        }
        return count;
    }

    private int countFilledInColumn(boolean[][] grid, int col) {
        int count = 0;
        for (boolean[] row : grid) {
            if (row[col]) {
                count++;
            }
        }
        return count;
    }

    private void retainLargestComponent(boolean[][] grid) {
        int height = grid.length;
        int width = grid[0].length;
        boolean[][] visited = new boolean[height][width];
        List<int[]> largest = new ArrayList<>();

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                if (!grid[row][col] || visited[row][col]) {
                    continue;
                }
                List<int[]> component = floodFill(grid, visited, row, col);
                if (component.size() > largest.size()) {
                    largest = component;
                }
            }
        }

        if (largest.isEmpty()) {
            return;
        }

        boolean[][] keep = new boolean[height][width];
        for (int[] cell : largest) {
            keep[cell[0]][cell[1]] = true;
        }

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                grid[row][col] = keep[row][col];
            }
        }
    }

    private List<int[]> floodFill(boolean[][] grid, boolean[][] visited, int startRow, int startCol) {
        int height = grid.length;
        int width = grid[0].length;
        Deque<int[]> queue = new ArrayDeque<>();
        List<int[]> component = new ArrayList<>();

        queue.add(new int[]{startRow, startCol});
        visited[startRow][startCol] = true;

        while (!queue.isEmpty()) {
            int[] current = queue.removeFirst();
            component.add(current);

            int row = current[0];
            int col = current[1];
            int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] direction : directions) {
                int nr = row + direction[0];
                int nc = col + direction[1];
                if (nr < 0 || nr >= height || nc < 0 || nc >= width) {
                    continue;
                }
                if (!grid[nr][nc] || visited[nr][nc]) {
                    continue;
                }
                visited[nr][nc] = true;
                queue.addLast(new int[]{nr, nc});
            }
        }

        return component;
    }

    private int scoreAesthetics(boolean[][] grid, GeneratedDifficulty difficulty) {
        int height = grid.length;
        int width = grid[0].length;
        int total = width * height;
        int filled = 0;
        int isolatedFilled = 0;
        int rowRuns = 0;
        int columnRuns = 0;
        int emptyRows = 0;
        int fullRows = 0;
        int emptyColumns = 0;
        int fullColumns = 0;

        for (int row = 0; row < height; row++) {
            boolean inRun = false;
            int rowFilled = 0;
            for (int col = 0; col < width; col++) {
                if (grid[row][col]) {
                    filled++;
                    rowFilled++;
                    if (!inRun) {
                        rowRuns++;
                    }
                    inRun = true;
                    if (countFilledNeighbors(grid, row, col) <= 1) {
                        isolatedFilled++;
                    }
                } else {
                    inRun = false;
                }
            }
            if (rowFilled == 0) {
                emptyRows++;
            } else if (rowFilled == width) {
                fullRows++;
            }
        }

        for (int col = 0; col < width; col++) {
            boolean inRun = false;
            int colFilled = 0;
            for (int row = 0; row < height; row++) {
                if (grid[row][col]) {
                    colFilled++;
                    if (!inRun) {
                        columnRuns++;
                    }
                    inRun = true;
                } else {
                    inRun = false;
                }
            }
            if (colFilled == 0) {
                emptyColumns++;
            } else if (colFilled == height) {
                fullColumns++;
            }
        }

        double density = (double) filled / total;
        int score = 100;

        if (density < 0.22 || density > 0.62) {
            score -= 40;
        } else if (difficulty == GeneratedDifficulty.EASY && (density < 0.28 || density > 0.55)) {
            score -= 20;
        } else if (difficulty == GeneratedDifficulty.EXPERT && density >= 0.30 && density <= 0.58) {
            score += 10;
        }

        score -= isolatedFilled * 8;
        score -= Math.max(0, (rowRuns + columnRuns) - (width + height)) * 2;
        score -= (emptyRows + emptyColumns) * 6;
        score -= (fullRows + fullColumns) * 8;

        int filledComponents = countFilledComponents(grid);
        score -= Math.max(0, filledComponents - 1) * 12;

        return score;
    }

    private int countFilledComponents(boolean[][] grid) {
        int height = grid.length;
        int width = grid[0].length;
        boolean[][] visited = new boolean[height][width];
        int components = 0;
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                if (!grid[row][col] || visited[row][col]) {
                    continue;
                }
                components++;
                floodFill(grid, visited, row, col);
            }
        }
        return components;
    }

    private int countFilledNeighbors(boolean[][] grid, int row, int col) {
        int count = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                int nr = row + dy;
                int nc = col + dx;
                if (nr < 0 || nr >= grid.length || nc < 0 || nc >= grid[0].length) {
                    continue;
                }
                if (grid[nr][nc]) {
                    count++;
                }
            }
        }
        return count;
    }

    private boolean[][] copyGrid(boolean[][] source) {
        boolean[][] copy = new boolean[source.length][source[0].length];
        for (int row = 0; row < source.length; row++) {
            System.arraycopy(source[row], 0, copy[row], 0, source[row].length);
        }
        return copy;
    }

    private boolean isAcceptableForDifficulty(GeneratedDifficulty difficulty,
                                             HumanDifficultySolver.DifficultyEstimate difficultyEstimate) {
        if (difficultyEstimate.isSolved()) {
            return true;
        }

        return difficulty == GeneratedDifficulty.EXPERT && difficultyEstimate.isUsedBacktracking();
    }

    private static class Candidate {
        private final boolean[][] grid;
        private final int aestheticsScore;
        private final HumanDifficultySolver.DifficultyEstimate difficultyEstimate;

        private Candidate(boolean[][] grid, int aestheticsScore, HumanDifficultySolver.DifficultyEstimate difficultyEstimate) {
            this.grid = grid;
            this.aestheticsScore = aestheticsScore;
            this.difficultyEstimate = difficultyEstimate;
        }

        private int distanceTo(GeneratedDifficulty target) {
            int scoreDistance;
            if (difficultyEstimate.getScore() < target.getMinScore()) {
                scoreDistance = target.getMinScore() - difficultyEstimate.getScore();
            } else if (difficultyEstimate.getScore() > target.getMaxScore()) {
                scoreDistance = difficultyEstimate.getScore() - target.getMaxScore();
            } else {
                scoreDistance = 0;
            }
            if (target == GeneratedDifficulty.EXPERT && !difficultyEstimate.isUsedBacktracking()) {
                scoreDistance += 180;
            }
            return scoreDistance - aestheticsScore;
        }
    }
}