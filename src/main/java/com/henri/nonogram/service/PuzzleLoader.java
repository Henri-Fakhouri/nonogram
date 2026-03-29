package com.henri.nonogram.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.henri.nonogram.model.Puzzle;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class PuzzleLoader {

    private final ObjectMapper mapper = new ObjectMapper();

    public Puzzle loadFromResource(String path) {
        try {
            return mapper.readValue(
                    getClass().getClassLoader().getResourceAsStream(path),
                    Puzzle.class
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<Puzzle> loadAllPuzzles() {
        try {
            List<Puzzle> puzzles = new ArrayList<>();

            URL url = getClass().getClassLoader().getResource("puzzles");
            File folder = new File(url.toURI());

            for (File file : folder.listFiles()) {
                if (file.getName().endsWith(".json")) {
                    Puzzle puzzle = mapper.readValue(file, Puzzle.class);
                    puzzles.add(puzzle);
                }
            }

            return puzzles;

        } catch (Exception e) {
            throw new RuntimeException("Failed to load puzzles", e);
        }
    }
}