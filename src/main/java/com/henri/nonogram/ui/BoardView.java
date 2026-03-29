package com.henri.nonogram.ui;

import com.henri.nonogram.model.CellState;
import com.henri.nonogram.model.Puzzle;
import javafx.animation.FadeTransition;
import javafx.animation.FillTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
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
    private static final Color LINE_HOVER_EMPTY_COLOR = Color.web("#EDF5FF");
    private static final Color LINE_HOVER_FILLED_COLOR = Color.web("#33495E");
    private static final Color INTERSECTION_EMPTY_COLOR = Color.web("#DDEBFF");
    private static final Color INTERSECTION_FILLED_COLOR = Color.web("#243442");
    private static final Color COMPLETED_EMPTY_COLOR = Color.web("#EDEDED");
    private static final Color COMPLETED_FILLED_COLOR = Color.web("#606B78");
    private static final Color VICTORY_FILLED_ACCENT = Color.web("#506D8B");
    private static final Color VICTORY_EMPTY_ACCENT = Color.web("#F2F6FD");
    private static final Color CROSS_COLOR = Color.web("#7F8C8D");
    private static final String BORDER_COLOR = "#333333";

    private final GameSession session;
    private final Runnable onVisualStateChanged;
    private final CellUI[][] cellUis;
    private final int cellSize;
    private final EndStateListener endStateListener;

    private MouseButton dragMode = null;
    private boolean completionRevealRunning = false;

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

        Rectangle flashOverlay = new Rectangle(cellSize, cellSize);
        flashOverlay.setFill(Color.rgb(231, 76, 60, 0.60));
        flashOverlay.setOpacity(0.0);
        flashOverlay.setMouseTransparent(true);

        Text text = new Text();
        text.setStyle("-fx-font-size: " + Math.max(12, cellSize / 3) + "px;");
        text.setFill(CROSS_COLOR);

        StackPane cell = new StackPane(rectangle, flashOverlay, text);
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

        cellUis[row][col] = new CellUI(cell, rectangle, flashOverlay, text);
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

        if (result.isHeartLost()) {
            playWrongCellFlash(row, col);
            playBoardShake();
        }

        boolean stopContinuousInput = result.isHeartLost() || result.isSolved() || result.isGameOver();
        if (stopContinuousInput) {
            dragMode = null;
            session.endAction();
        }

        if (result.isSolved()) {
            fireEndState(endStateListener::onVictory, result.isHeartLost());
        } else if (result.isGameOver()) {
            fireEndState(endStateListener::onGameOver, result.isHeartLost());
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

    private void playWrongCellFlash(int row, int col) {
        CellUI cell = cellUis[row][col];

        FadeTransition flashIn = new FadeTransition(Duration.millis(75), cell.flashOverlay);
        flashIn.setFromValue(0.0);
        flashIn.setToValue(0.85);

        FadeTransition flashOut = new FadeTransition(Duration.millis(190), cell.flashOverlay);
        flashOut.setFromValue(0.85);
        flashOut.setToValue(0.0);

        ScaleTransition shrink = new ScaleTransition(Duration.millis(90), cell.root);
        shrink.setFromX(1.0);
        shrink.setFromY(1.0);
        shrink.setToX(0.94);
        shrink.setToY(0.94);

        ScaleTransition grow = new ScaleTransition(Duration.millis(120), cell.root);
        grow.setFromX(0.94);
        grow.setFromY(0.94);
        grow.setToX(1.0);
        grow.setToY(1.0);

        ParallelTransition animation = new ParallelTransition(
                new SequentialTransition(flashIn, flashOut),
                new SequentialTransition(shrink, grow)
        );
        animation.play();
    }

    private void playBoardShake() {
        SequentialTransition shake = new SequentialTransition(
                createShakeStep(-6, 35),
                createShakeStep(6, 45),
                createShakeStep(-4, 40),
                createShakeStep(4, 40),
                createShakeStep(0, 35)
        );
        shake.play();
    }

    private TranslateTransition createShakeStep(double targetX, int millis) {
        TranslateTransition step = new TranslateTransition(Duration.millis(millis), this);
        step.setToX(targetX);
        return step;
    }

    public void playCompletionReveal(Runnable onFinished) {
        if (completionRevealRunning) {
            return;
        }

        completionRevealRunning = true;

        int height = session.getPuzzle().getHeight();
        int width = session.getPuzzle().getWidth();
        ParallelTransition master = new ParallelTransition();

        for (int row = 0; row < height; row++) {
            ParallelTransition rowPulse = new ParallelTransition();

            for (int col = 0; col < width; col++) {
                boolean shouldBeFilled = session.getPuzzle().getSolution()[row][col];
                rowPulse.getChildren().add(createVictoryPulse(cellUis[row][col], shouldBeFilled));
            }

            PauseTransition delay = new PauseTransition(Duration.millis(row * 55L));
            master.getChildren().add(new SequentialTransition(delay, rowPulse));
        }

        PauseTransition finish = new PauseTransition(Duration.millis(Math.max(450, (height - 1) * 55L + 260)));
        finish.setOnFinished(event -> {
            completionRevealRunning = false;
            if (onFinished != null) {
                onFinished.run();
            }
        });

        master.getChildren().add(finish);
        master.play();
    }

    private SequentialTransition createVictoryPulse(CellUI cell, boolean filled) {
        Color baseColor = (Color) cell.rectangle.getFill();
        Color accent = filled ? VICTORY_FILLED_ACCENT : VICTORY_EMPTY_ACCENT;
        double scalePeak = filled ? 1.08 : 1.03;

        FillTransition brighten = new FillTransition(Duration.millis(110), cell.rectangle);
        brighten.setFromValue(baseColor);
        brighten.setToValue(accent);

        FillTransition normalize = new FillTransition(Duration.millis(140), cell.rectangle);
        normalize.setFromValue(accent);
        normalize.setToValue(baseColor);

        ScaleTransition grow = new ScaleTransition(Duration.millis(110), cell.root);
        grow.setFromX(1.0);
        grow.setFromY(1.0);
        grow.setToX(scalePeak);
        grow.setToY(scalePeak);

        ScaleTransition shrink = new ScaleTransition(Duration.millis(140), cell.root);
        shrink.setFromX(scalePeak);
        shrink.setFromY(scalePeak);
        shrink.setToX(1.0);
        shrink.setToY(1.0);

        return new SequentialTransition(
                new ParallelTransition(brighten, grow),
                new ParallelTransition(normalize, shrink)
        );
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

        boolean rowHovered = row == session.getHoveredRow();
        boolean colHovered = col == session.getHoveredCol();
        boolean intersection = rowHovered && colHovered;
        boolean highlighted = rowHovered || colHovered;

        Color emptyColor = EMPTY_COLOR;
        Color filledColor = FILLED_COLOR;

        if (complete) {
            emptyColor = COMPLETED_EMPTY_COLOR;
            filledColor = COMPLETED_FILLED_COLOR;
        } else if (intersection) {
            emptyColor = INTERSECTION_EMPTY_COLOR;
            filledColor = INTERSECTION_FILLED_COLOR;
        } else if (highlighted) {
            emptyColor = LINE_HOVER_EMPTY_COLOR;
            filledColor = LINE_HOVER_FILLED_COLOR;
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
        private final StackPane root;
        private final Rectangle rectangle;
        private final Rectangle flashOverlay;
        private final Text text;

        private CellUI(StackPane root, Rectangle rectangle, Rectangle flashOverlay, Text text) {
            this.root = root;
            this.rectangle = rectangle;
            this.flashOverlay = flashOverlay;
            this.text = text;
        }
    }
}