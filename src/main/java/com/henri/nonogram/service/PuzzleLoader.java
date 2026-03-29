package com.henri.nonogram.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.henri.nonogram.model.Puzzle;

import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public class PuzzleLoader {

    private static final String PUZZLES_FOLDER = "puzzles";

    private final ObjectMapper mapper = new ObjectMapper();

    public Puzzle loadFromResource(String path) {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(path)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Resource not found: " + path);
            }

            return mapper.readValue(inputStream, Puzzle.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load puzzle resource: " + path, e);
        }
    }

    public List<Puzzle> loadAllPuzzles() {
        try {
            URL url = getClass().getClassLoader().getResource(PUZZLES_FOLDER);
            if (url == null) {
                throw new RuntimeException("Resource folder not found: " + PUZZLES_FOLDER);
            }

            return switch (url.getProtocol()) {
                case "file" -> loadFromFileSystem(url);
                case "jar" -> loadFromJar(url);
                default -> throw new RuntimeException(
                        "Unsupported resource protocol for puzzles folder: " + url.getProtocol()
                );
            };
        } catch (Exception e) {
            throw new RuntimeException("Failed to load puzzles", e);
        }
    }

    private List<Puzzle> loadFromFileSystem(URL url) throws Exception {
        List<Puzzle> puzzles = new ArrayList<>();

        Path folder = Paths.get(url.toURI());

        try (Stream<Path> paths = Files.list(folder)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(path -> {
                        try (InputStream inputStream = Files.newInputStream(path)) {
                            puzzles.add(mapper.readValue(inputStream, Puzzle.class));
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to read puzzle file: " + path, e);
                        }
                    });
        }

        return puzzles;
    }

    private List<Puzzle> loadFromJar(URL url) throws Exception {
        List<Puzzle> puzzles = new ArrayList<>();

        JarURLConnection connection = (JarURLConnection) url.openConnection();

        try (JarFile jarFile = connection.getJarFile()) {
            Enumeration<JarEntry> entries = jarFile.entries();
            List<String> puzzlePaths = new ArrayList<>();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                if (!entry.isDirectory()
                        && name.startsWith(PUZZLES_FOLDER + "/")
                        && name.endsWith(".json")) {
                    puzzlePaths.add(name);
                }
            }

            puzzlePaths.sort(String::compareTo);

            for (String puzzlePath : puzzlePaths) {
                try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(puzzlePath)) {
                    if (inputStream == null) {
                        throw new RuntimeException("Resource not found in jar: " + puzzlePath);
                    }

                    puzzles.add(mapper.readValue(inputStream, Puzzle.class));
                }
            }
        }

        return puzzles;
    }
}