package com.henri.nonogram.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class StatsService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path statsFile = Path.of(System.getProperty("user.home"), ".nonogram", "stats.json");

    public PlayerStats loadStats() {
        try {
            if (!Files.exists(statsFile)) {
                return new PlayerStats();
            }

            PlayerStats stats = objectMapper.readValue(statsFile.toFile(), PlayerStats.class);
            if (stats.getFastestTimesByDifficulty() == null) {
                stats.setFastestTimesByDifficulty(new LinkedHashMap<>());
            }
            return stats;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load player stats", e);
        }
    }

    public void saveStats(PlayerStats stats) {
        try {
            Files.createDirectories(statsFile.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(statsFile.toFile(), stats);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save player stats", e);
        }
    }

    public void recordMistake() {
        PlayerStats stats = loadStats();
        stats.setTotalMistakesMade(stats.getTotalMistakesMade() + 1);
        saveStats(stats);
    }

    public void recordPuzzleCompleted(String difficulty, long elapsedMillis) {
        PlayerStats stats = loadStats();
        stats.setPuzzlesCompleted(stats.getPuzzlesCompleted() + 1);
        stats.setWinStreak(stats.getWinStreak() + 1);

        if (difficulty != null && !difficulty.isBlank() && elapsedMillis > 0) {
            Long currentBest = stats.getFastestTimesByDifficulty().get(difficulty);
            if (currentBest == null || elapsedMillis < currentBest) {
                stats.getFastestTimesByDifficulty().put(difficulty, elapsedMillis);
            }
        }

        saveStats(stats);
    }

    public void recordGameOver() {
        PlayerStats stats = loadStats();
        stats.setWinStreak(0);
        saveStats(stats);
    }

    public static String formatDuration(long millis) {
        long totalSeconds = Math.max(0, millis / 1000);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlayerStats {
        private int puzzlesCompleted;
        private int winStreak;
        private int totalMistakesMade;
        private Map<String, Long> fastestTimesByDifficulty = new LinkedHashMap<>();

        public PlayerStats() {
        }

        public int getPuzzlesCompleted() {
            return puzzlesCompleted;
        }

        public void setPuzzlesCompleted(int puzzlesCompleted) {
            this.puzzlesCompleted = puzzlesCompleted;
        }

        public int getWinStreak() {
            return winStreak;
        }

        public void setWinStreak(int winStreak) {
            this.winStreak = winStreak;
        }

        public int getTotalMistakesMade() {
            return totalMistakesMade;
        }

        public void setTotalMistakesMade(int totalMistakesMade) {
            this.totalMistakesMade = totalMistakesMade;
        }

        public Map<String, Long> getFastestTimesByDifficulty() {
            if (fastestTimesByDifficulty == null) {
                fastestTimesByDifficulty = new LinkedHashMap<>();
            }
            return fastestTimesByDifficulty;
        }

        public void setFastestTimesByDifficulty(Map<String, Long> fastestTimesByDifficulty) {
            this.fastestTimesByDifficulty = fastestTimesByDifficulty;
        }

        public long getFastestTimeForDifficulty(String difficulty) {
            Long value = getFastestTimesByDifficulty().get(difficulty);
            return value == null ? 0L : value;
        }
    }
}