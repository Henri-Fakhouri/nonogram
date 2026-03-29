package com.henri.nonogram.ui;

import com.henri.nonogram.model.CellState;
import com.henri.nonogram.model.Puzzle;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.util.Duration;

public class BoardView extends GridPane {

    private static final int GRID_GAP = 0;
    private static final int GROUP_SIZE = 5;

    private static final int OUTER_BORDER_WIDTH = 2;
    private static final int MINOR_GRID_WIDTH = 1;
    private static final int MAJOR_GRID_WIDTH = 3;
    private static final int HEART_LOSS_END_STATE_DELAY_MS = 360;

    private static final Color EMPTY_COLOR = Color.web("#FFFFFF");
    private static final Color FILLED_COLOR = Color.web("#2F3640");
    private static final Color HOVER_EMPTY_COLOR = Color.web("#E8F0FE");
    private static final Color HOVER_FILLED_COLOR = Color.web("#25303B");
    private static final Color COMPLETED_EMPTY_COLOR = Color.web("#EDEDED");
    private static final Color COMPLETED_FILLED_COLOR = Color.web("#606B78");
    private static final Color CROSS_COLOR = Color.web("#7F8C8D");
    private static final String BORDER_COLOR = "#333333";

    private final GameSession session;
    private final Runnable onVisualStateChanged;
    private final CellUI[][] cellUis;
    private final int cellSize;
    private final EndStateListener endStateListener;

    private MouseButton dragMode = null;

    public BoardView(GameSession session, Runnable onVisualStateChanged, EndStateListener endStateListener) {
        this.session = session;
        this.onVisualStateChanged = onVisualStateChanged;
        this.endStateListener = endStateListener;
        this.cellUis = new CellUI[session.getPuzzle().getHeight()][session.getPuzzle().getWidth()];
        this.cellSize = computeCellSize(session.getPuzzle());

        getStyleClass().add("board-view");
        setAlignment(Pos.CENTER);
        setHgap(GRID_GAP);
        setVgap(GRID_GAP);
        setStyle("""
                -fx-border-color: %s;
                -fx-border-width: %d;
                -fx-background-color: transparent;
                """.formatted(BORDER_COLOR, OUTER_BORDER_WIDTH));

        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null) {
                oldScene.removeEventFilter(MouseEvent.MOUSE_RELEASED, this::handleSceneMouseReleased);
            }
            if (newScene != null) {
                newScene.addEventFilter(MouseEvent.MOUSE_RELEASED, this::handleSceneMouseReleased);
            }
        });

        buildBoard();
    }

    public static int computeCellSize(Puzzle puzzle) {
        int max = Math.max(puzzle.getWidth(), puzzle.getHeight());

        if (max <= 5) {
            return 52;
        }
        if (max <= 10) {
            return 40;
        }
        if (max <= 15) {
            return 30;
        }
        return 24;
    }

    public int getCellSize() {
        return cellSize;
    }

    private void handleSceneMouseReleased(MouseEvent event) {
        dragMode = null;
        session.endAction();
        onVisualStateChanged.run();
    }

    private void buildBoard() {
        int height = session.getPuzzle().getHeight();
        int width = session.getPuzzle().getWidth();

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                StackPane cell = createCell(row, col);
                add(cell, col, row);
            }
        }
    }

    private StackPane createCell(int row, int col) {
        Rectangle rectangle = new Rectangle(cellSize, cellSize);
        rectangle.setStroke(null);
        rectangle.setFill(EMPTY_COLOR);

        Text text = new Text();
        text.setStyle("-fx-font-size: " + Math.max(12, cellSize / 3) + "px;");
        text.setFill(CROSS_COLOR);

        StackPane cell = new StackPane(rectangle, text);
        cell.setAlignment(Pos.CENTER);
        cell.setMinSize(cellSize, cellSize);
        cell.setPrefSize(cellSize, cellSize);
        cell.setMaxSize(cellSize, cellSize);

        int top = row == 0 ? 0 : ((row % GROUP_SIZE == 0) ? MAJOR_GRID_WIDTH : MINOR_GRID_WIDTH);
        int left = col == 0 ? 0 : ((col % GROUP_SIZE == 0) ? MAJOR_GRID_WIDTH : MINOR_GRID_WIDTH);

        cell.setStyle("""
                -fx-border-color: %s;
                -fx-background-color: transparent;
                -fx-border-width: %d 0 0 %d;
                """.formatted(BORDER_COLOR, top, left));

        cellUis[row][col] = new CellUI(rectangle, text);
        updateCellVisual(row, col);

        cell.setOnMousePressed(event -> {
            if (session.isInteractionLocked()) {
                event.consume();
                return;
            }

            dragMode = event.getButton();
            session.beginAction(dragMode);
            session.setHoveredCell(row, col);
            applyDragAction(row, col);
            refreshBoardVisuals();
            onVisualStateChanged.run();
            event.consume();
        });

        cell.setOnDragDetected(event -> {
            if (!session.isInteractionLocked()) {
                cell.startFullDrag();
            }
            event.consume();
        });

        cell.setOnMouseEntered(event -> {
            session.setHoveredCell(row, col);
            refreshBoardVisuals();
            onVisualStateChanged.run();
        });

        cell.setOnMouseExited(event -> {
            if (session.getHoveredRow() == row && session.getHoveredCol() == col) {
                session.clearHoveredCell();
                refreshBoardVisuals();
                onVisualStateChanged.run();
            }
        });

        cell.setOnMouseDragEntered(event -> {
            session.setHoveredCell(row, col);

            if (dragMode != null && !session.isInteractionLocked()) {
                applyDragAction(row, col);
            }

            refreshBoardVisuals();
            onVisualStateChanged.run();
            event.consume();
        });

        return cell;
    }

    private void applyDragAction(int row, int col) {
        if (dragMode == null) {
            return;
        }

        GameActionResult result = session.applyAction(dragMode, row, col);

        boolean stopContinuousInput = result.isHeartLost() || result.isSolved() || result.isGameOver();
        if (stopContinuousInput) {
            dragMode = null;
            session.endAction();
        }

        if (result.isSolved()) {
            fireEndState(() -> endStateListener.onVictory(), result.isHeartLost());
        } else if (result.isGameOver()) {
            fireEndState(() -> endStateListener.onGameOver(), result.isHeartLost());
        }
    }

    private void fireEndState(Runnable action, boolean delayForHeartAnimation) {
        if (!delayForHeartAnimation) {
            action.run();
            return;
        }

        PauseTransition pause = new PauseTransition(Duration.millis(HEART_LOSS_END_STATE_DELAY_MS));
        pause.setOnFinished(event -> action.run());
        pause.play();
    }

    public void refreshBoardVisuals() {
        int height = session.getPuzzle().getHeight();
        int width = session.getPuzzle().getWidth();

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                updateCellVisual(row, col);
            }
        }
    }

    private void updateCellVisual(int row, int col) {
        CellUI cellUI = cellUis[row][col];
        CellState state = session.getGameState().getCell(row, col);

        boolean rowComplete = session.isRowComplete(row);
        boolean colComplete = session.isColumnComplete(col);
        boolean complete = rowComplete || colComplete;
        boolean hovered = row == session.getHoveredRow() || col == session.getHoveredCol();

        Color emptyColor = EMPTY_COLOR;
        Color filledColor = FILLED_COLOR;

        if (complete) {
            emptyColor = COMPLETED_EMPTY_COLOR;
            filledColor = COMPLETED_FILLED_COLOR;
        } else if (hovered) {
            emptyColor = HOVER_EMPTY_COLOR;
            filledColor = HOVER_FILLED_COLOR;
        }

        switch (state) {
            case EMPTY -> {
                cellUI.rectangle.setFill(emptyColor);
                cellUI.text.setText("");
                cellUI.text.setFill(CROSS_COLOR);
            }
            case FILLED -> {
                cellUI.rectangle.setFill(filledColor);
                cellUI.text.setText("");
                cellUI.text.setFill(CROSS_COLOR);
            }
            case CROSSED -> {
                cellUI.rectangle.setFill(emptyColor);
                cellUI.text.setText("✕");
                cellUI.text.setFill(CROSS_COLOR);
            }
        }
    }

    public interface EndStateListener {
        void onVictory();

        default void onGameOver() {
        }
    }

    private static class CellUI {
        private final Rectangle rectangle;
        private final Text text;

        private CellUI(Rectangle rectangle, Text text) {
            this.rectangle = rectangle;
            this.text = text;
        }
    }
}