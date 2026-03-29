package com.henri.nonogram.ui;

import com.henri.nonogram.model.CellState;
import com.henri.nonogram.model.GameState;
import com.henri.nonogram.model.Puzzle;
import com.henri.nonogram.service.ClueGenerator;
import com.henri.nonogram.service.SaveService;
import javafx.scene.input.MouseButton;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class GameSession {

    private static final int MAX_HEARTS = 3;

    private final GameState gameState;
    private final List<List<Integer>> rowClues;
    private final List<List<Integer>> columnClues;
    private final SaveService saveService = new SaveService();

    private final Deque<ActionBatch> undoStack = new ArrayDeque<>();
    private final Deque<ActionBatch> redoStack = new ArrayDeque<>();

    private ActionBatch currentBatch;

    private InputMode primaryMode = InputMode.FILL;
    private boolean solvedPopupAlreadyShown = false;
    private int hoveredRow = -1;
    private int hoveredCol = -1;
    private int heartsRemaining = MAX_HEARTS;
    private boolean gameOver = false;

    public GameSession(Puzzle puzzle) {
        this.gameState = new GameState(puzzle);

        ClueGenerator clueGenerator = new ClueGenerator();
        this.rowClues = clueGenerator.generateRowClues(puzzle.getSolution());
        this.columnClues = clueGenerator.generateColumnClues(puzzle.getSolution());

        loadProgress();
        this.solvedPopupAlreadyShown = isSolved();
        updateGameOverState();
    }

    public GameState getGameState() {
        return gameState;
    }

    public Puzzle getPuzzle() {
        return gameState.getPuzzle();
    }

    public List<List<Integer>> getRowClues() {
        return rowClues;
    }

    public List<List<Integer>> getColumnClues() {
        return columnClues;
    }

    public InputMode getPrimaryMode() {
        return primaryMode;
    }

    public void togglePrimaryMode() {
        primaryMode = primaryMode.opposite();
        saveProgress();
    }

    public int getHoveredRow() {
        return hoveredRow;
    }

    public int getHoveredCol() {
        return hoveredCol;
    }

    public int getHeartsRemaining() {
        return heartsRemaining;
    }

    public int getMaxHearts() {
        return MAX_HEARTS;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public boolean isInteractionLocked() {
        return gameOver || isSolved();
    }

    public void setHoveredCell(int row, int col) {
        this.hoveredRow = row;
        this.hoveredCol = col;
    }

    public void clearHoveredCell() {
        this.hoveredRow = -1;
        this.hoveredCol = -1;
    }

    public void beginAction(MouseButton button) {
        if (isInteractionLocked()) {
            currentBatch = null;
            return;
        }

        currentBatch = new ActionBatch(button, heartsRemaining);
    }

    public GameActionResult applyAction(MouseButton button, int row, int col) {
        if (isInteractionLocked()) {
            return GameActionResult.none();
        }

        if (currentBatch == null) {
            beginAction(button);
        }

        CellState targetState = resolveTargetState(button);
        if (targetState == null) {
            return GameActionResult.none();
        }

        CellState previousState = gameState.getCell(row, col);

        // No overwriting allowed once a cell is no longer empty.
        if (previousState != CellState.EMPTY) {
            return GameActionResult.none();
        }

        CellState correctState = getCorrectState(row, col);
        boolean wrongMove = targetState != correctState;
        boolean heartLost = false;

        CellState finalState = targetState;

        if (wrongMove) {
            heartLost = loseHeart();
            finalState = correctState;
        }

        setCellState(row, col, finalState, true);
        autoCrossCompletedLines(true);
        updateGameOverState();

        boolean solved = checkSolvedNow();
        return new GameActionResult(solved, gameOver, heartLost);
    }

    public void endAction() {
        if (currentBatch != null && !currentBatch.isEmpty()) {
            currentBatch.setHeartsAfter(heartsRemaining);
            undoStack.push(currentBatch);
            redoStack.clear();
            saveProgress();
        }

        currentBatch = null;
    }

    public boolean undo() {
        if (undoStack.isEmpty()) {
            return false;
        }

        ActionBatch batch = undoStack.pop();

        for (CellChange change : batch.getChanges().values()) {
            gameState.setCell(change.getRow(), change.getCol(), change.getPreviousState());
        }

        heartsRemaining = batch.getHeartsBefore();
        solvedPopupAlreadyShown = isSolved();
        updateGameOverState();

        redoStack.push(batch);
        saveProgress();
        return true;
    }

    public boolean redo() {
        if (redoStack.isEmpty()) {
            return false;
        }

        ActionBatch batch = redoStack.pop();

        for (CellChange change : batch.getChanges().values()) {
            gameState.setCell(change.getRow(), change.getCol(), change.getNewState());
        }

        heartsRemaining = batch.getHeartsAfter();
        solvedPopupAlreadyShown = isSolved();
        updateGameOverState();

        undoStack.push(batch);
        saveProgress();
        return true;
    }

    public void reset() {
        gameState.reset();
        undoStack.clear();
        redoStack.clear();
        solvedPopupAlreadyShown = false;
        clearHoveredCell();
        heartsRemaining = MAX_HEARTS;
        updateGameOverState();
        saveProgress();
    }

    public boolean isRowComplete(int row) {
        boolean[][] solution = getPuzzle().getSolution();
        int width = getPuzzle().getWidth();

        for (int col = 0; col < width; col++) {
            boolean shouldBeFilled = solution[row][col];
            boolean isFilled = gameState.getCell(row, col) == CellState.FILLED;

            if (shouldBeFilled != isFilled) {
                return false;
            }
        }

        return true;
    }

    public boolean isColumnComplete(int col) {
        boolean[][] solution = getPuzzle().getSolution();
        int height = getPuzzle().getHeight();

        for (int row = 0; row < height; row++) {
            boolean shouldBeFilled = solution[row][col];
            boolean isFilled = gameState.getCell(row, col) == CellState.FILLED;

            if (shouldBeFilled != isFilled) {
                return false;
            }
        }

        return true;
    }

    public boolean isSolved() {
        boolean[][] solution = getPuzzle().getSolution();
        int height = getPuzzle().getHeight();
        int width = getPuzzle().getWidth();

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                boolean shouldBeFilled = solution[row][col];
                CellState actualState = gameState.getCell(row, col);

                if (shouldBeFilled) {
                    if (actualState != CellState.FILLED) {
                        return false;
                    }
                } else {
                    if (actualState == CellState.FILLED) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    public CellState[][] copyCurrentGrid() {
        int height = getPuzzle().getHeight();
        int width = getPuzzle().getWidth();
        CellState[][] snapshot = new CellState[height][width];

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                snapshot[row][col] = gameState.getCell(row, col);
            }
        }

        return snapshot;
    }

    private CellState resolveTargetState(MouseButton button) {
        if (button == MouseButton.PRIMARY) {
            return primaryMode == InputMode.FILL ? CellState.FILLED : CellState.CROSSED;
        }

        if (button == MouseButton.SECONDARY) {
            return primaryMode == InputMode.FILL ? CellState.CROSSED : CellState.FILLED;
        }

        // Erasing is disabled because cells cannot be overwritten once set.
        return null;
    }

    private CellState getCorrectState(int row, int col) {
        return getPuzzle().getSolution()[row][col] ? CellState.FILLED : CellState.CROSSED;
    }

    private boolean loseHeart() {
        if (heartsRemaining > 0) {
            heartsRemaining--;
        }

        return true;
    }

    private void updateGameOverState() {
        gameOver = heartsRemaining <= 0 && !isSolved();
    }

    private boolean checkSolvedNow() {
        if (!solvedPopupAlreadyShown && isSolved()) {
            solvedPopupAlreadyShown = true;
            return true;
        }

        return false;
    }

    private void autoCrossCompletedLines(boolean recordChanges) {
        int height = getPuzzle().getHeight();
        int width = getPuzzle().getWidth();

        for (int row = 0; row < height; row++) {
            if (isRowComplete(row)) {
                autoCrossRow(row, recordChanges);
            }
        }

        for (int col = 0; col < width; col++) {
            if (isColumnComplete(col)) {
                autoCrossColumn(col, recordChanges);
            }
        }
    }

    private void autoCrossRow(int row, boolean recordChanges) {
        int width = getPuzzle().getWidth();

        for (int col = 0; col < width; col++) {
            if (gameState.getCell(row, col) == CellState.EMPTY) {
                setCellState(row, col, CellState.CROSSED, recordChanges);
            }
        }
    }

    private void autoCrossColumn(int col, boolean recordChanges) {
        int height = getPuzzle().getHeight();

        for (int row = 0; row < height; row++) {
            if (gameState.getCell(row, col) == CellState.EMPTY) {
                setCellState(row, col, CellState.CROSSED, recordChanges);
            }
        }
    }

    private void setCellState(int row, int col, CellState newState, boolean recordChanges) {
        CellState previousState = gameState.getCell(row, col);

        if (previousState == newState) {
            return;
        }

        gameState.setCell(row, col, newState);

        if (recordChanges && currentBatch != null) {
            currentBatch.recordChange(row, col, previousState, newState);
        }
    }

    private void loadProgress() {
        Optional<SaveService.SaveData> optionalSave = saveService.load(getPuzzle().getId());

        if (optionalSave.isEmpty()) {
            return;
        }

        SaveService.SaveData saveData = optionalSave.get();

        if (saveData.getPrimaryMode() != null) {
            this.primaryMode = saveData.getPrimaryMode();
        }

        this.heartsRemaining = Math.max(0, Math.min(MAX_HEARTS, saveData.getHeartsRemaining()));

        CellState[][] savedGrid = saveData.getGrid();

        if (savedGrid == null) {
            return;
        }

        int height = getPuzzle().getHeight();
        int width = getPuzzle().getWidth();

        if (savedGrid.length != height) {
            return;
        }

        for (int row = 0; row < height; row++) {
            if (savedGrid[row] == null || savedGrid[row].length != width) {
                return;
            }
        }

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                gameState.setCell(row, col, savedGrid[row][col]);
            }
        }

        updateGameOverState();
    }

    private void saveProgress() {
        saveService.save(getPuzzle().getId(), copyCurrentGrid(), primaryMode, heartsRemaining);
    }

    private static class ActionBatch {
        private final LinkedHashMap<String, CellChange> changes = new LinkedHashMap<>();
        private final int heartsBefore;
        private int heartsAfter;

        private ActionBatch(MouseButton button, int heartsBefore) {
            this.heartsBefore = heartsBefore;
            this.heartsAfter = heartsBefore;
        }

        public boolean isEmpty() {
            return changes.isEmpty() && heartsBefore == heartsAfter;
        }

        public Map<String, CellChange> getChanges() {
            return changes;
        }

        public int getHeartsBefore() {
            return heartsBefore;
        }

        public int getHeartsAfter() {
            return heartsAfter;
        }

        public void setHeartsAfter(int heartsAfter) {
            this.heartsAfter = heartsAfter;
        }

        public void recordChange(int row, int col, CellState previousState, CellState newState) {
            String key = row + ":" + col;

            if (!changes.containsKey(key)) {
                changes.put(key, new CellChange(row, col, previousState, newState));
                return;
            }

            CellChange existing = changes.get(key);
            existing.setNewState(newState);

            if (existing.getPreviousState() == existing.getNewState()) {
                changes.remove(key);
            }
        }
    }

    private static class CellChange {
        private final int row;
        private final int col;
        private final CellState previousState;
        private CellState newState;

        private CellChange(int row, int col, CellState previousState, CellState newState) {
            this.row = row;
            this.col = col;
            this.previousState = previousState;
            this.newState = newState;
        }

        public int getRow() {
            return row;
        }

        public int getCol() {
            return col;
        }

        public CellState getPreviousState() {
            return previousState;
        }

        public CellState getNewState() {
            return newState;
        }

        public void setNewState(CellState newState) {
            this.newState = newState;
        }
    }
}