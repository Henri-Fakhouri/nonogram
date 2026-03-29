package com.henri.nonogram.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.henri.nonogram.model.CellState;
import com.henri.nonogram.ui.InputMode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class SaveService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path savesDirectory = Path.of(System.getProperty("user.home"), ".nonogram", "saves");

    public Optional<SaveData> load(String puzzleId) {
        try {
            Path file = savesDirectory.resolve(puzzleId + ".json");

            if (!Files.exists(file)) {
                return Optional.empty();
            }

            SaveData data = objectMapper.readValue(file.toFile(), SaveData.class);
            return Optional.of(data);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load save for puzzle: " + puzzleId, e);
        }
    }

    public void save(String puzzleId, CellState[][] grid, InputMode primaryMode, int heartsRemaining) {
        try {
            Files.createDirectories(savesDirectory);

            SaveData data = new SaveData();
            data.setPuzzleId(puzzleId);
            data.setPrimaryMode(primaryMode);
            data.setHeartsRemaining(heartsRemaining);
            data.setGrid(copyGrid(grid));

            Path file = savesDirectory.resolve(puzzleId + ".json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), data);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save puzzle: " + puzzleId, e);
        }
    }

    private CellState[][] copyGrid(CellState[][] source) {
        CellState[][] copy = new CellState[source.length][];

        for (int row = 0; row < source.length; row++) {
            copy[row] = new CellState[source[row].length];
            System.arraycopy(source[row], 0, copy[row], 0, source[row].length);
        }

        return copy;
    }

    public static class SaveData {
        private String puzzleId;
        private InputMode primaryMode;
        private int heartsRemaining = 3;
        private CellState[][] grid;

        public SaveData() {
        }

        public String getPuzzleId() {
            return puzzleId;
        }

        public void setPuzzleId(String puzzleId) {
            this.puzzleId = puzzleId;
        }

        public InputMode getPrimaryMode() {
            return primaryMode;
        }

        public void setPrimaryMode(InputMode primaryMode) {
            this.primaryMode = primaryMode;
        }

        public int getHeartsRemaining() {
            return heartsRemaining;
        }

        public void setHeartsRemaining(int heartsRemaining) {
            this.heartsRemaining = heartsRemaining;
        }

        public CellState[][] getGrid() {
            return grid;
        }

        public void setGrid(CellState[][] grid) {
            this.grid = grid;
        }
    }
}