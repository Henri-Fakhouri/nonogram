package com.henri.nonogram.ui;

public class GameActionResult {

    private final boolean solved;
    private final boolean gameOver;
    private final boolean heartLost;

    public GameActionResult(boolean solved, boolean gameOver, boolean heartLost) {
        this.solved = solved;
        this.gameOver = gameOver;
        this.heartLost = heartLost;
    }

    public boolean isSolved() {
        return solved;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public boolean isHeartLost() {
        return heartLost;
    }

    public static GameActionResult none() {
        return new GameActionResult(false, false, false);
    }
}