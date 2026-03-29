package com.henri.nonogram.ui;

import com.henri.nonogram.model.Puzzle;
import com.henri.nonogram.service.StatsService;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

public class GameView extends BorderPane {

    private static final String CLUE_BOX_FILL = "#FFFFFF";
    private static final String CLUE_BOX_BORDER = "#AEB9C9";

    private final GameSession session;
    private final Runnable onBack;
    private final Runnable onNextPuzzle;
    private final BoardView boardView;
    private final StatsService statsService = new StatsService();
    private final int cellSize;

    private final int boardPixelWidth;
    private final int boardPixelHeight;
    private final int maxRowClueCount;
    private final int maxColumnClueCount;
    private final int rowClueCellWidth;
    private final int rowClueAreaWidth;
    private final int columnClueAreaHeight;

    private final List<List<ClueCellUi>> rowClueCells = new ArrayList<>();
    private final List<List<ClueCellUi>> columnClueCells = new ArrayList<>();

    private Button modeButton;
    private HBox heartsBox;
    private Label runSummaryLabel;
    private StackPane centerStack;
    private int lastRenderedHearts = -1;
    private boolean victorySequenceRunning = false;

    public GameView(Puzzle puzzle, Runnable onBack, Runnable onNextPuzzle) {
        this.session = new GameSession(puzzle);
        this.onBack = onBack;
        this.onNextPuzzle = onNextPuzzle;
        this.boardView = new BoardView(session, this::refreshAllUi, new BoardView.EndStateListener() {
            @Override
            public void onVictory() {
                playVictorySequence();
            }

            @Override
            public void onGameOver() {
                showEndOverlay(false);
            }
        });
        this.cellSize = boardView.getCellSize();

        this.boardPixelWidth = session.getPuzzle().getWidth() * cellSize;
        this.boardPixelHeight = session.getPuzzle().getHeight() * cellSize;
        this.maxRowClueCount = Math.max(1, session.getRowClues().stream().mapToInt(List::size).max().orElse(1));
        this.maxColumnClueCount = Math.max(1, session.getColumnClues().stream().mapToInt(List::size).max().orElse(1));
        this.rowClueCellWidth = Math.max(30, cellSize / 2 + 8);
        this.rowClueAreaWidth = maxRowClueCount * rowClueCellWidth;
        this.columnClueAreaHeight = maxColumnClueCount * cellSize;

        getStyleClass().add("app-root");
        getStyleClass().add("game-view");
        setFocusTraversable(true);

        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null) {
                oldScene.removeEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPressed);
            }
            if (newScene != null) {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPressed);
            }
        });

        buildLayout();
        refreshAllUi();

        if (session.isSolved()) {
            showEndOverlay(true);
        } else if (session.isGameOver()) {
            showEndOverlay(false);
        }
    }

    private void handleKeyPressed(KeyEvent event) {
        boolean handled = false;

        if (session.isInteractionLocked()) {
            return;
        }

        if (event.getCode() == KeyCode.SPACE) {
            session.togglePrimaryMode();
            refreshAllUi();
            handled = true;
        } else if (event.isControlDown() && event.getCode() == KeyCode.Z && !event.isShiftDown()) {
            if (session.undo()) {
                refreshAllUi();
            }
            handled = true;
        } else if ((event.isControlDown() && event.getCode() == KeyCode.Y)
                || (event.isControlDown() && event.isShiftDown() && event.getCode() == KeyCode.Z)) {
            if (session.redo()) {
                refreshAllUi();
            }
            handled = true;
        }

        if (handled) {
            event.consume();
        }
    }

    private void buildLayout() {
        setPadding(new Insets(24));

        StackPane topBar = createTopBar();
        GridPane puzzleArea = createPuzzleArea();

        StackPane puzzleCard = new StackPane(puzzleArea);
        puzzleCard.getStyleClass().add("puzzle-card");
        puzzleCard.setAlignment(Pos.CENTER);
        puzzleCard.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        centerStack = new StackPane(puzzleCard);
        centerStack.setAlignment(Pos.CENTER);
        centerStack.getStyleClass().add("game-center");

        setTop(topBar);
        setCenter(centerStack);
    }

    private StackPane createTopBar() {
        Button backButton = new Button("Back");
        backButton.getStyleClass().addAll("button", "secondary-button");
        backButton.setOnAction(e -> onBack.run());

        Label titleLabel = new Label(session.getPuzzle().getTitle());
        titleLabel.getStyleClass().add("screen-title");
        titleLabel.setWrapText(true);
        titleLabel.setAlignment(Pos.CENTER);
        titleLabel.setMaxWidth(520);

        heartsBox = new HBox(10);
        heartsBox.getStyleClass().add("hearts-box");
        heartsBox.setAlignment(Pos.CENTER);

        runSummaryLabel = new Label();
        runSummaryLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #6C7684;");

        VBox titleBlock = new VBox(4, titleLabel, heartsBox, runSummaryLabel);
        titleBlock.getStyleClass().add("top-bar-center");
        titleBlock.setAlignment(Pos.CENTER);
        titleBlock.setFillWidth(true);
        titleBlock.setMaxWidth(540);
        titleBlock.setPickOnBounds(false);
        titleBlock.setMouseTransparent(true);

        modeButton = new Button();
        modeButton.getStyleClass().addAll("button", "primary-button", "toggle-button");
        modeButton.setOnAction(e -> {
            session.togglePrimaryMode();
            refreshAllUi();
        });

        Button restartButton = new Button("Restart");
        restartButton.getStyleClass().addAll("button", "secondary-button");
        restartButton.setOnAction(e -> {
            session.reset();
            hideEndOverlay();
            refreshAllUi();
        });

        HBox leftBox = new HBox(backButton);
        leftBox.getStyleClass().add("top-bar-left");
        leftBox.setAlignment(Pos.CENTER_LEFT);

        HBox rightBox = new HBox(10, modeButton, restartButton);
        rightBox.getStyleClass().add("top-bar-right");
        rightBox.setAlignment(Pos.CENTER_RIGHT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox overlayBar = new HBox(18, leftBox, spacer, rightBox);
        overlayBar.setAlignment(Pos.CENTER);

        StackPane topBar = new StackPane(overlayBar, titleBlock);
        topBar.getStyleClass().add("top-bar");
        topBar.setPadding(new Insets(0, 0, 24, 0));

        return topBar;
    }

    private GridPane createPuzzleArea() {
        GridPane puzzleGrid = new GridPane();
        puzzleGrid.setAlignment(Pos.CENTER);
        puzzleGrid.setHgap(0);
        puzzleGrid.setVgap(0);

        Pane topCluesView = createColumnCluesView();
        Pane leftCluesView = createRowCluesView();

        Region cornerSpacer = new Region();
        cornerSpacer.setMinSize(rowClueAreaWidth, columnClueAreaHeight);
        cornerSpacer.setPrefSize(rowClueAreaWidth, columnClueAreaHeight);
        cornerSpacer.setMaxSize(rowClueAreaWidth, columnClueAreaHeight);

        StackPane boardWrapper = new StackPane(boardView);
        boardWrapper.setAlignment(Pos.CENTER);
        boardWrapper.setMinSize(boardPixelWidth, boardPixelHeight);
        boardWrapper.setPrefSize(boardPixelWidth, boardPixelHeight);
        boardWrapper.setMaxSize(boardPixelWidth, boardPixelHeight);

        puzzleGrid.add(cornerSpacer, 0, 0);
        puzzleGrid.add(topCluesView, 1, 0);
        puzzleGrid.add(leftCluesView, 0, 1);
        puzzleGrid.add(boardWrapper, 1, 1);

        GridPane.setHalignment(topCluesView, HPos.CENTER);
        GridPane.setValignment(topCluesView, VPos.BOTTOM);

        GridPane.setHalignment(leftCluesView, HPos.RIGHT);
        GridPane.setValignment(leftCluesView, VPos.CENTER);

        GridPane.setHalignment(boardWrapper, HPos.CENTER);
        GridPane.setValignment(boardWrapper, VPos.CENTER);

        return puzzleGrid;
    }

    private Pane createRowCluesView() {
        Pane container = new Pane();
        container.setMinSize(rowClueAreaWidth, boardPixelHeight);
        container.setPrefSize(rowClueAreaWidth, boardPixelHeight);
        container.setMaxSize(rowClueAreaWidth, boardPixelHeight);

        List<List<Integer>> rowClues = session.getRowClues();

        for (int row = 0; row < rowClues.size(); row++) {
            List<Integer> clues = rowClues.get(row);
            List<ClueCellUi> cellsForRow = new ArrayList<>();

            if (clues.isEmpty()) {
                rowClueCells.add(cellsForRow);
                continue;
            }

            int startX = rowClueAreaWidth - (clues.size() * rowClueCellWidth);
            int y = row * cellSize;

            for (int i = 0; i < clues.size(); i++) {
                int x = startX + (i * rowClueCellWidth);

                ClueCellUi clueCell = createClueCell(
                        String.valueOf(clues.get(i)),
                        rowClueCellWidth,
                        cellSize
                );
                clueCell.root.setLayoutX(x);
                clueCell.root.setLayoutY(y);

                cellsForRow.add(clueCell);
                container.getChildren().add(clueCell.root);
            }

            rowClueCells.add(cellsForRow);
        }

        return container;
    }

    private Pane createColumnCluesView() {
        Pane container = new Pane();
        container.setMinSize(boardPixelWidth, columnClueAreaHeight);
        container.setPrefSize(boardPixelWidth, columnClueAreaHeight);
        container.setMaxSize(boardPixelWidth, columnClueAreaHeight);

        List<List<Integer>> columnClues = session.getColumnClues();

        for (int col = 0; col < columnClues.size(); col++) {
            List<Integer> clues = columnClues.get(col);
            List<ClueCellUi> cellsForColumn = new ArrayList<>();

            if (clues.isEmpty()) {
                columnClueCells.add(cellsForColumn);
                continue;
            }

            int startY = columnClueAreaHeight - (clues.size() * cellSize);
            int x = col * cellSize;

            for (int i = 0; i < clues.size(); i++) {
                int y = startY + (i * cellSize);

                ClueCellUi clueCell = createClueCell(
                        String.valueOf(clues.get(i)),
                        cellSize,
                        cellSize
                );
                clueCell.root.setLayoutX(x);
                clueCell.root.setLayoutY(y);

                cellsForColumn.add(clueCell);
                container.getChildren().add(clueCell.root);
            }

            columnClueCells.add(cellsForColumn);
        }

        return container;
    }

    private ClueCellUi createClueCell(String text, double width, double height) {
        Rectangle background = new Rectangle(width, height);
        background.setFill(Color.web(CLUE_BOX_FILL));
        background.setStroke(Color.web(CLUE_BOX_BORDER));
        background.setStrokeWidth(1.0);

        Label label = new Label(text);
        label.getStyleClass().add("clue-label");
        label.setMinSize(width, height);
        label.setPrefSize(width, height);
        label.setMaxSize(width, height);
        label.setAlignment(Pos.CENTER);

        StackPane cell = new StackPane(background, label);
        cell.setMinSize(width, height);
        cell.setPrefSize(width, height);
        cell.setMaxSize(width, height);

        return new ClueCellUi(cell, background, label);
    }

    private void refreshAllUi() {
        boardView.refreshBoardVisuals();
        refreshClueVisuals();
        refreshModeButtonText();
        refreshHearts();
        refreshRunSummary();
    }

    private void refreshModeButtonText() {
        modeButton.setText("Left Click: " + (session.getPrimaryMode() == InputMode.FILL ? "Fill" : "Cross"));
    }

    private void refreshHearts() {
        int currentHearts = session.getHeartsRemaining();
        boolean initialRender = heartsBox.getChildren().isEmpty();

        if (!initialRender && currentHearts == lastRenderedHearts) {
            return;
        }

        int lostHeartIndex = lastRenderedHearts > currentHearts ? currentHearts : -1;

        heartsBox.getChildren().clear();

        for (int i = 0; i < session.getMaxHearts(); i++) {
            Label heart = new Label();
            heart.setMinWidth(32);
            heart.setPrefWidth(32);
            heart.setAlignment(Pos.CENTER);

            if (i < currentHearts) {
                heart.setText("❤");
                applyHeartStyle(heart, true);
            } else if (i == lostHeartIndex) {
                heart.setText("❤");
                applyHeartStyle(heart, true);
            } else {
                heart.setText("♡");
                applyHeartStyle(heart, false);
            }

            heartsBox.getChildren().add(heart);

            if (i == lostHeartIndex) {
                animateLostHeart(heart);
            }
        }

        lastRenderedHearts = currentHearts;
    }

    private void applyHeartStyle(Label heart, boolean filled) {
        if (filled) {
            heart.setStyle("-fx-font-size: 30px; -fx-font-weight: bold; -fx-text-fill: #E25555;");
            heart.setOpacity(1.0);
        } else {
            heart.setStyle("-fx-font-size: 30px; -fx-font-weight: bold; -fx-text-fill: #D6DAE1;");
            heart.setOpacity(0.82);
        }
    }

    private void animateLostHeart(Label heart) {
        ScaleTransition shrink = new ScaleTransition(Duration.millis(160), heart);
        shrink.setFromX(1.0);
        shrink.setFromY(1.0);
        shrink.setToX(0.25);
        shrink.setToY(0.25);

        FadeTransition fadeOut = new FadeTransition(Duration.millis(160), heart);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.15);

        ParallelTransition disappear = new ParallelTransition(shrink, fadeOut);
        disappear.setOnFinished(event -> {
            heart.setText("♡");
            applyHeartStyle(heart, false);
            heart.setScaleX(0.35);
            heart.setScaleY(0.35);
            heart.setOpacity(0.15);

            ScaleTransition grow = new ScaleTransition(Duration.millis(180), heart);
            grow.setFromX(0.35);
            grow.setFromY(0.35);
            grow.setToX(1.0);
            grow.setToY(1.0);

            FadeTransition fadeIn = new FadeTransition(Duration.millis(180), heart);
            fadeIn.setFromValue(0.15);
            fadeIn.setToValue(0.82);

            new ParallelTransition(grow, fadeIn).play();
        });
        disappear.play();
    }

    private void refreshRunSummary() {
        runSummaryLabel.setText(
                "Time " + StatsService.formatDuration(session.getElapsedMillis())
                        + "  •  Mistakes " + session.getMistakesMadeThisPuzzle()
        );
    }

    private void refreshClueVisuals() {
        for (int row = 0; row < rowClueCells.size(); row++) {
            boolean complete = session.isRowComplete(row);
            boolean hovered = session.getHoveredRow() == row;

            for (ClueCellUi cell : rowClueCells.get(row)) {
                applyClueStyle(cell, complete, hovered);
            }
        }

        for (int col = 0; col < columnClueCells.size(); col++) {
            boolean complete = session.isColumnComplete(col);
            boolean hovered = session.getHoveredCol() == col;

            for (ClueCellUi cell : columnClueCells.get(col)) {
                applyClueStyle(cell, complete, hovered);
            }
        }
    }

    private void applyClueStyle(ClueCellUi cell, boolean complete, boolean hovered) {
        int fontSize = Math.max(14, cellSize / 3);

        if (complete) {
            cell.background.setFill(Color.web("#EEF1F4"));
            cell.background.setStroke(Color.web("#C8CFDA"));
            cell.label.setStyle("-fx-font-size: " + fontSize + "px; -fx-font-weight: bold; -fx-text-fill: #9097A5;");
            return;
        }

        if (hovered) {
            cell.background.setFill(Color.rgb(74, 144, 226, 0.18));
            cell.background.setStroke(Color.web("#7FA9E7"));
            cell.label.setStyle("-fx-font-size: " + fontSize + "px; -fx-font-weight: bold; -fx-text-fill: #336DB4;");
            return;
        }

        cell.background.setFill(Color.web(CLUE_BOX_FILL));
        cell.background.setStroke(Color.web(CLUE_BOX_BORDER));
        cell.label.setStyle("-fx-font-size: " + fontSize + "px; -fx-font-weight: bold; -fx-text-fill: #5A6270;");
    }

    private void playVictorySequence() {
        if (victorySequenceRunning) {
            return;
        }

        victorySequenceRunning = true;
        boardView.playCompletionReveal(() -> {
            victorySequenceRunning = false;
            showEndOverlay(true);
        });
    }

    private void showEndOverlay(boolean victory) {
        hideEndOverlay();

        StatsService.PlayerStats playerStats = statsService.loadStats();

        Label title = new Label(victory ? "Puzzle Completed" : "Game Over");
        title.getStyleClass().add("overlay-title");

        Label subtitle = new Label(victory
                ? "You solved " + session.getPuzzle().getTitle()
                : "You ran out of hearts.");
        subtitle.getStyleClass().add("overlay-subtitle");

        Label details = new Label(
                "Time " + StatsService.formatDuration(session.getElapsedMillis())
                        + " • Mistakes " + session.getMistakesMadeThisPuzzle()
        );
        details.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #6B7380;");

        Label progress = new Label(victory
                ? "Completed " + playerStats.getPuzzlesCompleted() + " • Streak " + playerStats.getWinStreak()
                : "Win streak reset • Total mistakes " + playerStats.getTotalMistakesMade());
        progress.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #8B93A1;");

        Button restartButton = new Button("Restart");
        restartButton.getStyleClass().addAll("button", "primary-button");
        restartButton.setOnAction(e -> {
            session.reset();
            hideEndOverlay();
            refreshAllUi();
        });

        Button backButton = new Button("Back to Menu");
        backButton.getStyleClass().addAll("button", "secondary-button");
        backButton.setOnAction(e -> onBack.run());

        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER);
        buttons.getChildren().addAll(restartButton, backButton);

        if (victory && onNextPuzzle != null) {
            Button nextButton = new Button("Next Puzzle");
            nextButton.getStyleClass().addAll("button", "primary-button");
            nextButton.setOnAction(e -> onNextPuzzle.run());
            buttons.getChildren().add(1, nextButton);
        }

        VBox overlayBox = new VBox(14, title, subtitle, details, progress, buttons);
        overlayBox.getStyleClass().add("overlay-card");
        overlayBox.setAlignment(Pos.CENTER);
        overlayBox.setPadding(new Insets(28));
        overlayBox.setMaxWidth(420);

        StackPane overlayWrapper = new StackPane(overlayBox);
        overlayWrapper.getStyleClass().add("overlay-backdrop");
        overlayWrapper.setPickOnBounds(true);

        centerStack.getChildren().add(overlayWrapper);
    }

    private void hideEndOverlay() {
        if (centerStack == null || centerStack.getChildren().size() <= 1) {
            return;
        }

        centerStack.getChildren().remove(centerStack.getChildren().size() - 1);
    }

    private static class ClueCellUi {
        private final StackPane root;
        private final Rectangle background;
        private final Label label;

        private ClueCellUi(StackPane root, Rectangle background, Label label) {
            this.root = root;
            this.background = background;
            this.label = label;
        }
    }
}