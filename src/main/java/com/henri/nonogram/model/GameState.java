package com.henri.nonogram.model;

public class GameState {

    private final Puzzle puzzle;
    private final CellState[][] playerGrid;

    public GameState(Puzzle puzzle) {
        this.puzzle = puzzle;
        this.playerGrid = new CellState[puzzle.getHeight()][puzzle.getWidth()];
        reset();
    }

    public Puzzle getPuzzle() {
        return puzzle;
    }

    public CellState getCell(int row, int col) {
        return playerGrid[row][col];
    }

    public void setCell(int row, int col, CellState state) {
        playerGrid[row][col] = state;
    }

    public void reset() {
        for (int row = 0; row < puzzle.getHeight(); row++) {
            for (int col = 0; col < puzzle.getWidth(); col++) {
                playerGrid[row][col] = CellState.EMPTY;
            }
        }
    }
}