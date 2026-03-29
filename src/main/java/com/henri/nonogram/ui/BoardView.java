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

    private static final int HEART_LOSS_END_STATE_DELAY_MS = 340;

    private static final Color EMPTY_COLOR = Color.web("#FFFFFF");
    private static final Color FILLED_COLOR = Color.web("#3F556F");

    private static final Color HOVER_EMPTY_COLOR = Color.web("#EDF4FB");
    private static final Color HOVER_FILLED_COLOR = Color.web("#365274");

    private static final Color INTERSECTION_EMPTY_COLOR = Color.web("#E2ECF8");
    private static final Color INTERSECTION_FILLED_COLOR = Color.web("#2D486C");

    private static final Color COMPLETED_EMPTY_COLOR = Color.web("#EFF4FA");
    private static final Color COMPLETED_FILLED_COLOR = Color.web("#7E94AF");

    private static final Color VICTORY_FILLED_FLASH = Color.web("#9AB2CF");
    private static final Color VICTORY_EMPTY_FLASH = Color.web("#F1F6FD");

    private static final Color CROSS_COLOR = Color.web("#111111");
    private static final Color WRONG_CROSS_COLOR = Color.web("#E25555");
    private static final String BORDER_COLOR = "#2D2D2D";

    private final GameSession session;
    private final Runnable onVisualStateChanged;
    private final CellUI[][] cellUis;
    private final int cellSize;
    private final EndStateListener endStateListener;

    private MouseButton dragMode = null;
    private boolean victoryAnimationRunning = false;

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
        if (max <= 20) {
            return 17;
        }
        return 14;
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
        flashOverlay.setArcWidth(6);
        flashOverlay.setArcHeight(6);
        flashOverlay.setFill(Color.rgb(231, 76, 60, 0.78));
        flashOverlay.setOpacity(0.0);
        flashOverlay.setMouseTransparent(true);

        Text text = new Text();
        text.setStyle("-fx-font-size: " + Math.max(12, cellSize / 3) + "px;");
        text.setFill(CROSS_COLOR);
        text.setOpacity(1.0);

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
        updateCellVisual(row, col, true);

        cell.setOnMousePressed(event -> {
            if (session.isInteractionLocked() || victoryAnimationRunning) {
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
            if (!session.isInteractionLocked() && !victoryAnimationRunning) {
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

            if (dragMode != null && !session.isInteractionLocked() && !victoryAnimationRunning) {
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
            playWrongMoveFeedback(row, col);
        }

        if (result.isHeartLost() || result.isSolved() || result.isGameOver()) {
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

    private void playWrongMoveFeedback(int row, int col) {
        CellUI cell = cellUis[row][col];

        if (session.getGameState().getCell(row, col) == CellState.CROSSED) {
            cell.wrongCrossUntilEpochMillis = System.currentTimeMillis() + 900L;
        }

        FadeTransition flashIn = new FadeTransition(Duration.millis(70), cell.flashOverlay);
        flashIn.setFromValue(0.0);
        flashIn.setToValue(0.92);

        FadeTransition flashOut = new FadeTransition(Duration.millis(180), cell.flashOverlay);
        flashOut.setFromValue(0.92);
        flashOut.setToValue(0.0);

        ScaleTransition shrink = new ScaleTransition(Duration.millis(85), cell.root);
        shrink.setFromX(1.0);
        shrink.setFromY(1.0);
        shrink.setToX(0.92);
        shrink.setToY(0.92);

        ScaleTransition rebound = new ScaleTransition(Duration.millis(130), cell.root);
        rebound.setFromX(0.92);
        rebound.setFromY(0.92);
        rebound.setToX(1.0);
        rebound.setToY(1.0);

        ParallelTransition cellFeedback = new ParallelTransition(
                new SequentialTransition(flashIn, flashOut),
                new SequentialTransition(shrink, rebound)
        );
        cellFeedback.play();

        SequentialTransition boardShake = new SequentialTransition(
                createShakeStep(-7, 35),
                createShakeStep(7, 45),
                createShakeStep(-4, 40),
                createShakeStep(4, 40),
                createShakeStep(0, 35)
        );
        boardShake.play();

        if (cell.wrongCrossUntilEpochMillis > 0L) {
            PauseTransition holdRedCross = new PauseTransition(Duration.millis(920));
            holdRedCross.setOnFinished(event -> refreshBoardVisuals());
            holdRedCross.play();
        }
    }

    private TranslateTransition createShakeStep(double targetX, int millis) {
        TranslateTransition step = new TranslateTransition(Duration.millis(millis), this);
        step.setToX(targetX);
        return step;
    }

    public void refreshBoardVisuals() {
        int height = session.getPuzzle().getHeight();
        int width = session.getPuzzle().getWidth();

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                updateCellVisual(row, col, false);
            }
        }
    }

    private void updateCellVisual(int row, int col, boolean immediate) {
        CellUI cellUI = cellUis[row][col];
        CellState state = session.getGameState().getCell(row, col);

        if (state != CellState.CROSSED) {
            cellUI.wrongCrossUntilEpochMillis = 0L;
        }

        boolean rowComplete = session.isRowComplete(row);
        boolean colComplete = session.isColumnComplete(col);
        boolean complete = rowComplete || colComplete;

        boolean rowHovered = row == session.getHoveredRow();
        boolean colHovered = col == session.getHoveredCol();
        boolean intersection = rowHovered && colHovered;
        boolean hoveredLine = rowHovered || colHovered;

        Color emptyColor = EMPTY_COLOR;
        Color filledColor = FILLED_COLOR;

        if (complete) {
            emptyColor = COMPLETED_EMPTY_COLOR;
            filledColor = COMPLETED_FILLED_COLOR;
        } else if (intersection) {
            emptyColor = INTERSECTION_EMPTY_COLOR;
            filledColor = INTERSECTION_FILLED_COLOR;
        } else if (hoveredLine) {
            emptyColor = HOVER_EMPTY_COLOR;
            filledColor = HOVER_FILLED_COLOR;
        }

        Color targetFill = state == CellState.FILLED ? filledColor : emptyColor;
        String targetText = state == CellState.CROSSED ? "✕" : "";
        Color crossTextColor = cellUI.wrongCrossUntilEpochMillis > System.currentTimeMillis()
                ? WRONG_CROSS_COLOR
                : CROSS_COLOR;

        boolean stateChanged = cellUI.lastState != state;
        boolean fillChanged = !targetFill.equals(cellUI.lastFill);

        if (stateChanged) {
            switch (state) {
                case FILLED -> animateFillPlacement(cellUI, targetFill);
                case CROSSED -> animateCrossPlacement(cellUI, targetFill, crossTextColor);
                case EMPTY -> {
                    cellUI.text.setText("");
                    cellUI.text.setOpacity(1.0);
                    cellUI.text.setScaleX(1.0);
                    cellUI.text.setScaleY(1.0);
                    setRectangleFill(cellUI.rectangle, targetFill, immediate);
                }
            }
        } else {
            if (fillChanged) {
                setRectangleFill(cellUI.rectangle, targetFill, immediate);
            }
            if (!targetText.equals(cellUI.text.getText())) {
                cellUI.text.setText(targetText);
            }
        }

        if (state != CellState.CROSSED) {
            cellUI.text.setText("");
            cellUI.text.setOpacity(1.0);
            cellUI.text.setScaleX(1.0);
            cellUI.text.setScaleY(1.0);
        } else {
            cellUI.text.setFill(crossTextColor);
        }

        cellUI.lastState = state;
        cellUI.lastFill = targetFill;
    }

    private void setRectangleFill(Rectangle rectangle, Color targetFill, boolean immediate) {
        Color current = (Color) rectangle.getFill();

        if (immediate || current == null || current.equals(targetFill)) {
            rectangle.setFill(targetFill);
            return;
        }

        FillTransition transition = new FillTransition(Duration.millis(110), rectangle, current, targetFill);
        transition.play();
    }

    private void animateFillPlacement(CellUI cellUI, Color targetFill) {
        Color current = (Color) cellUI.rectangle.getFill();
        if (current == null) {
            current = EMPTY_COLOR;
        }

        FillTransition fillTransition = new FillTransition(Duration.millis(110), cellUI.rectangle, current, targetFill);

        ScaleTransition grow = new ScaleTransition(Duration.millis(95), cellUI.root);
        grow.setFromX(1.0);
        grow.setFromY(1.0);
        grow.setToX(1.08);
        grow.setToY(1.08);

        ScaleTransition settle = new ScaleTransition(Duration.millis(110), cellUI.root);
        settle.setFromX(1.08);
        settle.setFromY(1.08);
        settle.setToX(1.0);
        settle.setToY(1.0);

        new ParallelTransition(fillTransition, new SequentialTransition(grow, settle)).play();
    }

    private void animateCrossPlacement(CellUI cellUI, Color targetFill, Color crossTextColor) {
        Color current = (Color) cellUI.rectangle.getFill();
        if (current == null) {
            current = EMPTY_COLOR;
        }

        cellUI.text.setText("✕");
        cellUI.text.setFill(crossTextColor);
        cellUI.text.setOpacity(0.0);
        cellUI.text.setScaleX(0.55);
        cellUI.text.setScaleY(0.55);

        FillTransition fillTransition = new FillTransition(Duration.millis(90), cellUI.rectangle, current, targetFill);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(120), cellUI.text);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);

        ScaleTransition grow = new ScaleTransition(Duration.millis(120), cellUI.text);
        grow.setFromX(0.55);
        grow.setFromY(0.55);
        grow.setToX(1.0);
        grow.setToY(1.0);

        new ParallelTransition(fillTransition, fadeIn, grow).play();
    }

    public void playVictoryReveal(Runnable onFinished) {
        if (victoryAnimationRunning) {
            return;
        }

        victoryAnimationRunning = true;

        int height = session.getPuzzle().getHeight();
        int width = session.getPuzzle().getWidth();
        int centerRow = height / 2;
        int centerCol = width / 2;

        ParallelTransition master = new ParallelTransition();
        long maxDelay = 0L;

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                CellUI cell = cellUis[row][col];
                boolean filled = session.getPuzzle().getSolution()[row][col];

                Color baseColor = (Color) cell.rectangle.getFill();
                Color flashColor = filled ? VICTORY_FILLED_FLASH : VICTORY_EMPTY_FLASH;
                double pulseScale = filled ? 1.10 : 1.05;

                long delayMillis = 45L * (Math.abs(row - centerRow) + Math.abs(col - centerCol));
                if (delayMillis > maxDelay) {
                    maxDelay = delayMillis;
                }

                FillTransition brighten = new FillTransition(Duration.millis(130), cell.rectangle, baseColor, flashColor);
                FillTransition normalize = new FillTransition(Duration.millis(180), cell.rectangle, flashColor, baseColor);

                ScaleTransition grow = new ScaleTransition(Duration.millis(130), cell.root);
                grow.setFromX(1.0);
                grow.setFromY(1.0);
                grow.setToX(pulseScale);
                grow.setToY(pulseScale);

                ScaleTransition shrink = new ScaleTransition(Duration.millis(180), cell.root);
                shrink.setFromX(pulseScale);
                shrink.setFromY(pulseScale);
                shrink.setToX(1.0);
                shrink.setToY(1.0);

                SequentialTransition pulse = new SequentialTransition(
                        new ParallelTransition(brighten, grow),
                        new ParallelTransition(normalize, shrink)
                );

                PauseTransition delay = new PauseTransition(Duration.millis(delayMillis));
                master.getChildren().add(new SequentialTransition(delay, pulse));
            }
        }

        PauseTransition finish = new PauseTransition(Duration.millis(maxDelay + 340));
        finish.setOnFinished(event -> {
            victoryAnimationRunning = false;
            if (onFinished != null) {
                onFinished.run();
            }
        });

        master.getChildren().add(finish);
        master.play();
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
        private CellState lastState;
        private Color lastFill;
        private long wrongCrossUntilEpochMillis;

        private CellUI(StackPane root, Rectangle rectangle, Rectangle flashOverlay, Text text) {
            this.root = root;
            this.rectangle = rectangle;
            this.flashOverlay = flashOverlay;
            this.text = text;
            this.lastState = null;
            this.lastFill = null;
            this.wrongCrossUntilEpochMillis = 0L;
        }
    }
}