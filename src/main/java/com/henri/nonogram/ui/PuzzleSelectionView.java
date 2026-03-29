package com.henri.nonogram.ui;

import com.henri.nonogram.model.Puzzle;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class PuzzleSelectionView extends BorderPane {

    private final List<Puzzle> allPuzzles;
    private final Consumer<Puzzle> onSelect;

    private final ComboBox<String> packFilter = new ComboBox<>();
    private final ComboBox<String> difficultyFilter = new ComboBox<>();
    private final VBox puzzleListBox = new VBox(12);

    public PuzzleSelectionView(List<Puzzle> puzzles, Consumer<Puzzle> onSelect) {
        this.allPuzzles = new ArrayList<>(puzzles);
        this.onSelect = onSelect;

        this.allPuzzles.sort(Comparator
                .comparing((Puzzle p) -> p.getDisplayPack().toLowerCase())
                .thenComparing(p -> p.getDisplayDifficulty().toLowerCase())
                .thenComparing(p -> p.getTitle().toLowerCase()));

        getStyleClass().add("app-root");
        getStyleClass().add("menu-view");

        buildLayout();
        refreshPuzzleList();
    }

    private void buildLayout() {
        setPadding(new Insets(24));

        Label title = new Label("Select Puzzle");
        title.getStyleClass().add("menu-title");

        Label subtitle = new Label("Choose a pack, a difficulty, then start playing.");
        subtitle.getStyleClass().add("menu-subtitle");

        packFilter.setItems(FXCollections.observableArrayList(buildPackOptions()));
        packFilter.setValue("All Packs");
        packFilter.getStyleClass().add("menu-combo");
        packFilter.setMaxWidth(Double.MAX_VALUE);

        difficultyFilter.setItems(FXCollections.observableArrayList(buildDifficultyOptions()));
        difficultyFilter.setValue("All Difficulties");
        difficultyFilter.getStyleClass().add("menu-combo");
        difficultyFilter.setMaxWidth(Double.MAX_VALUE);

        packFilter.setOnAction(e -> refreshPuzzleList());
        difficultyFilter.setOnAction(e -> refreshPuzzleList());

        VBox packBox = new VBox(6, buildFilterLabel("Pack"), packFilter);
        packBox.getStyleClass().add("filter-group");
        packBox.setAlignment(Pos.CENTER_LEFT);
        packBox.setFillWidth(true);
        packBox.setPrefWidth(220);
        packBox.setMaxWidth(220);

        VBox difficultyBox = new VBox(6, buildFilterLabel("Difficulty"), difficultyFilter);
        difficultyBox.getStyleClass().add("filter-group");
        difficultyBox.setAlignment(Pos.CENTER_LEFT);
        difficultyBox.setFillWidth(true);
        difficultyBox.setPrefWidth(220);
        difficultyBox.setMaxWidth(220);

        VBox filtersBox = new VBox(12, packBox, difficultyBox);
        filtersBox.setAlignment(Pos.CENTER);
        filtersBox.setFillWidth(false);

        VBox header = new VBox(10, title, subtitle);
        header.getStyleClass().add("menu-header");
        header.setAlignment(Pos.CENTER);

        puzzleListBox.getStyleClass().add("menu-list");
        puzzleListBox.setAlignment(Pos.TOP_CENTER);
        puzzleListBox.setPadding(new Insets(4));
        puzzleListBox.setFillWidth(true);
        puzzleListBox.setMaxWidth(Double.MAX_VALUE);

        ScrollPane scrollPane = new ScrollPane(puzzleListBox);
        scrollPane.getStyleClass().add("menu-scroll");
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(420);
        scrollPane.setMaxWidth(Double.MAX_VALUE);

        VBox content = new VBox(18, header, filtersBox, scrollPane);
        content.setAlignment(Pos.TOP_CENTER);
        content.setFillWidth(true);
        content.setMaxWidth(520);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        StackPane centeredContent = new StackPane(content);
        centeredContent.setAlignment(Pos.TOP_CENTER);

        setCenter(centeredContent);
    }

    private Label buildFilterLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("filter-label");
        return label;
    }

    private List<String> buildPackOptions() {
        Set<String> values = new LinkedHashSet<>();
        values.add("All Packs");

        for (Puzzle puzzle : allPuzzles) {
            values.add(puzzle.getDisplayPack());
        }

        return new ArrayList<>(values);
    }

    private List<String> buildDifficultyOptions() {
        Set<String> values = new LinkedHashSet<>();
        values.add("All Difficulties");

        for (Puzzle puzzle : allPuzzles) {
            values.add(puzzle.getDisplayDifficulty());
        }

        return new ArrayList<>(values);
    }

    private void refreshPuzzleList() {
        puzzleListBox.getChildren().clear();

        List<Puzzle> filtered = allPuzzles.stream()
                .filter(this::matchesPack)
                .filter(this::matchesDifficulty)
                .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            Label empty = new Label("No puzzles match these filters.");
            empty.getStyleClass().add("menu-subtitle");
            puzzleListBox.getChildren().add(empty);
            return;
        }

        for (Puzzle puzzle : filtered) {
            VBox card = createPuzzleCard(puzzle);
            puzzleListBox.getChildren().add(card);
        }
    }

    private VBox createPuzzleCard(Puzzle puzzle) {
        Button button = new Button(puzzle.getTitle());
        button.getStyleClass().addAll("button", "primary-button", "puzzle-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(e -> onSelect.accept(puzzle));

        Label metaLabel = new Label(
                puzzle.getDisplayPack() + " • " +
                puzzle.getDisplayDifficulty() + " • " +
                puzzle.getWidth() + "x" + puzzle.getHeight()
        );
        metaLabel.getStyleClass().add("menu-meta");

        VBox card = new VBox(6, button, metaLabel);
        card.getStyleClass().add("menu-card");
        card.setAlignment(Pos.CENTER);
        card.setFillWidth(true);
        card.setMaxWidth(Double.MAX_VALUE);

        return card;
    }

    private boolean matchesPack(Puzzle puzzle) {
        String selected = packFilter.getValue();
        return selected == null || selected.equals("All Packs") || puzzle.getDisplayPack().equals(selected);
    }

    private boolean matchesDifficulty(Puzzle puzzle) {
        String selected = difficultyFilter.getValue();
        return selected == null || selected.equals("All Difficulties") || puzzle.getDisplayDifficulty().equals(selected);
    }
}