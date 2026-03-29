package com.henri.nonogram.generator;

public enum GeneratedDifficulty {
    EASY("Easy", 5, 5, 0, 140),
    MEDIUM("Medium", 8, 8, 141, 300),
    HARD("Hard", 10, 10, 301, 700);

    private final String displayName;
    private final int defaultWidth;
    private final int defaultHeight;
    private final int minScore;
    private final int maxScore;

    GeneratedDifficulty(String displayName, int defaultWidth, int defaultHeight, int minScore, int maxScore) {
        this.displayName = displayName;
        this.defaultWidth = defaultWidth;
        this.defaultHeight = defaultHeight;
        this.minScore = minScore;
        this.maxScore = maxScore;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getDefaultWidth() {
        return defaultWidth;
    }

    public int getDefaultHeight() {
        return defaultHeight;
    }

    public int getMinScore() {
        return minScore;
    }

    public int getMaxScore() {
        return maxScore;
    }

    public boolean matchesScore(int score, boolean usedBacktracking) {
        if (usedBacktracking) {
            return false;
        }
        return score >= minScore && score <= maxScore;
    }
}