package com.henri.nonogram.service;

import java.util.ArrayList;
import java.util.List;

public class ClueGenerator {

    public List<List<Integer>> generateRowClues(boolean[][] grid) {
        List<List<Integer>> allClues = new ArrayList<>();

        for (boolean[] row : grid) {
            List<Integer> clues = new ArrayList<>();
            int count = 0;

            for (boolean cell : row) {
                if (cell) {
                    count++;
                } else if (count > 0) {
                    clues.add(count);
                    count = 0;
                }
            }

            if (count > 0) {
                clues.add(count);
            }

            allClues.add(clues);
        }

        return allClues;
    }

    public List<List<Integer>> generateColumnClues(boolean[][] grid) {
        List<List<Integer>> allClues = new ArrayList<>();

        int height = grid.length;
        int width = grid[0].length;

        for (int col = 0; col < width; col++) {
            List<Integer> clues = new ArrayList<>();
            int count = 0;

            for (int row = 0; row < height; row++) {
                if (grid[row][col]) {
                    count++;
                } else if (count > 0) {
                    clues.add(count);
                    count = 0;
                }
            }

            if (count > 0) {
                clues.add(count);
            }

            allClues.add(clues);
        }

        return allClues;
    }
}