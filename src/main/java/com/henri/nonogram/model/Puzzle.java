package com.henri.nonogram.model;

public class Puzzle {

    private String id;
    private String title;
    private int width;
    private int height;
    private boolean[][] solution;
    private String pack;
    private String difficulty;

    public Puzzle() {
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean[][] getSolution() {
        return solution;
    }

    public String getPack() {
        return pack;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public void setSolution(boolean[][] solution) {
        this.solution = solution;
    }

    public void setPack(String pack) {
        this.pack = pack;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getDisplayPack() {
        return (pack == null || pack.isBlank()) ? "Misc" : pack;
    }

    public String getDisplayDifficulty() {
        return (difficulty == null || difficulty.isBlank()) ? "Unknown" : difficulty;
    }
}