package com.henri.nonogram;

import com.henri.nonogram.generator.GeneratedDifficulty;
import com.henri.nonogram.generator.GeneratedPuzzleService;
import com.henri.nonogram.model.Puzzle;
import com.henri.nonogram.service.PuzzleLoader;
import com.henri.nonogram.ui.GameView;
import com.henri.nonogram.ui.PuzzleSelectionView;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class Main extends Application {

    private static final double INITIAL_WIDTH = 820;
    private static final double INITIAL_HEIGHT = 680;

    private static final int RECENT_HASHES_TO_REMEMBER = 20;
    private static final int DISTINCT_GENERATION_ATTEMPTS = 60;
    private static final long SEED_GAMMA = 0x9E3779B97F4A7C15L;

    private Stage primaryStage;
    private Scene mainScene;

    private final PuzzleLoader puzzleLoader = new PuzzleLoader();
    private final GeneratedPuzzleService generatedPuzzleService = new GeneratedPuzzleService();

    private final Map<GeneratedDifficulty, Long> endlessSeeds = new EnumMap<>(GeneratedDifficulty.class);
    private final Map<GeneratedDifficulty, Puzzle> endlessPreviewPuzzles = new EnumMap<>(GeneratedDifficulty.class);
    private final Map<GeneratedDifficulty, Deque<Long>> recentSolutionHashes = new EnumMap<>(GeneratedDifficulty.class);

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;

        initializeEndlessMode();

        Parent initialRoot = buildMenuRoot();
        this.mainScene = new Scene(initialRoot, INITIAL_WIDTH, INITIAL_HEIGHT);
        this.mainScene.getStylesheets().add(
                getClass().getResource("/styles/app.css").toExternalForm()
        );

        primaryStage.setMinWidth(720);
        primaryStage.setMinHeight(620);
        primaryStage.setScene(mainScene);
        primaryStage.setTitle("Select Puzzle");
        primaryStage.show();
    }

    private void initializeEndlessMode() {
        long baseSeed = System.currentTimeMillis();

        for (GeneratedDifficulty difficulty : GeneratedDifficulty.values()) {
            recentSolutionHashes.put(difficulty, new ArrayDeque<>());
            long seedOffset = 11L + (difficulty.ordinal() * 18L);
            endlessSeeds.put(difficulty, mix64(baseSeed + seedOffset));
        }

        for (GeneratedDifficulty difficulty : GeneratedDifficulty.values()) {
            endlessPreviewPuzzles.put(difficulty, createDistinctGeneratedPuzzle(difficulty));
        }
    }

    private void showMenu() {
        Parent menuRoot = buildMenuRoot();
        mainScene.setRoot(menuRoot);
        primaryStage.setTitle("Select Puzzle");
    }

    private Parent buildMenuRoot() {
        List<Puzzle> puzzles = loadSortedPuzzles();

        return new PuzzleSelectionView(
                puzzles,
                this::showGame
        );
    }

    private void showGame(Puzzle puzzle) {
        Runnable nextPuzzleAction = isGeneratedEndlessPuzzle(puzzle)
                ? buildNextGeneratedPuzzleAction(resolveGeneratedDifficulty(puzzle))
                : buildNextStaticPuzzleAction(puzzle);

        GameView gameView = new GameView(
                puzzle,
                this::showMenu,
                nextPuzzleAction
        );

        mainScene.setRoot(gameView);
        primaryStage.setTitle(puzzle.getTitle());
    }

    private Runnable buildNextStaticPuzzleAction(Puzzle puzzle) {
        List<Puzzle> puzzles = loadSortedPuzzles();
        int currentIndex = findPuzzleIndex(puzzles, puzzle);

        if (currentIndex < 0 || currentIndex >= puzzles.size() - 1) {
            return null;
        }

        Puzzle nextPuzzle = puzzles.get(currentIndex + 1);
        return () -> showGame(nextPuzzle);
    }

    private Runnable buildNextGeneratedPuzzleAction(GeneratedDifficulty difficulty) {
        if (difficulty == null) {
            return null;
        }

        return () -> {
            Puzzle nextPuzzle = createDistinctGeneratedPuzzle(difficulty);
            endlessPreviewPuzzles.put(difficulty, nextPuzzle);
            showGame(nextPuzzle);
        };
    }

    private List<Puzzle> loadSortedPuzzles() {
        List<Puzzle> puzzles = puzzleLoader.loadAllPuzzles();
        addGeneratedPuzzles(puzzles);

        puzzles.sort(Comparator
                .comparing((Puzzle p) -> p.getDisplayPack().toLowerCase())
                .thenComparing(p -> p.getDisplayDifficulty().toLowerCase())
                .thenComparing(p -> p.getTitle().toLowerCase()));

        return puzzles;
    }

    private void addGeneratedPuzzles(List<Puzzle> puzzles) {
        for (GeneratedDifficulty difficulty : GeneratedDifficulty.values()) {
            addPreviewIfPresent(puzzles, difficulty);
        }
    }

    private void addPreviewIfPresent(List<Puzzle> puzzles, GeneratedDifficulty difficulty) {
        Puzzle preview = endlessPreviewPuzzles.get(difficulty);
        if (preview != null) {
            puzzles.add(preview);
        }
    }

    private Puzzle createDistinctGeneratedPuzzle(GeneratedDifficulty difficulty) {
        long seed = endlessSeeds.get(difficulty);
        Puzzle fallbackPuzzle = null;

        for (int attempt = 0; attempt < DISTINCT_GENERATION_ATTEMPTS; attempt++) {
            Puzzle candidate = generatedPuzzleService.generate(difficulty, seed);
            long solutionHash = computeSolutionHash(candidate.getSolution());

            if (fallbackPuzzle == null) {
                fallbackPuzzle = candidate;
            }

            endlessSeeds.put(difficulty, advanceSeed(seed));

            if (!hasSeenRecently(difficulty, solutionHash)) {
                rememberSolutionHash(difficulty, solutionHash);
                return candidate;
            }

            seed = endlessSeeds.get(difficulty);
        }

        if (fallbackPuzzle != null) {
            rememberSolutionHash(difficulty, computeSolutionHash(fallbackPuzzle.getSolution()));
            return fallbackPuzzle;
        }

        throw new IllegalStateException("Failed to generate endless puzzle for " + difficulty.getDisplayName());
    }

    private boolean hasSeenRecently(GeneratedDifficulty difficulty, long solutionHash) {
        return recentSolutionHashes.get(difficulty).contains(solutionHash);
    }

    private void rememberSolutionHash(GeneratedDifficulty difficulty, long solutionHash) {
        Deque<Long> hashes = recentSolutionHashes.get(difficulty);
        hashes.addLast(solutionHash);

        while (hashes.size() > RECENT_HASHES_TO_REMEMBER) {
            hashes.removeFirst();
        }
    }

    private long computeSolutionHash(boolean[][] solution) {
        long hash = 1469598103934665603L;

        for (boolean[] row : solution) {
            for (boolean cell : row) {
                hash ^= cell ? 1L : 0L;
                hash *= 1099511628211L;
            }
        }

        return hash;
    }

    private long advanceSeed(long currentSeed) {
        return mix64(currentSeed + SEED_GAMMA);
    }

    private long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    private boolean isGeneratedEndlessPuzzle(Puzzle puzzle) {
        return puzzle.getId() != null
                && puzzle.getId().startsWith("generated_")
                && "Endless".equalsIgnoreCase(puzzle.getDisplayPack());
    }

    private GeneratedDifficulty resolveGeneratedDifficulty(Puzzle puzzle) {
        try {
            return GeneratedDifficulty.valueOf(puzzle.getDisplayDifficulty().toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }

    private int findPuzzleIndex(List<Puzzle> puzzles, Puzzle puzzle) {
        for (int i = 0; i < puzzles.size(); i++) {
            if (puzzles.get(i).getId().equals(puzzle.getId())) {
                return i;
            }
        }
        return -1;
    }

    public static void main(String[] args) {
        launch();
    }
}