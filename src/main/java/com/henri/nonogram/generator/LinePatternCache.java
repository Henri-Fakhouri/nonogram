package com.henri.nonogram.generator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LinePatternCache {

    private final Map<String, List<boolean[]>> cache = new HashMap<>();

    public List<boolean[]> getPatterns(int length, List<Integer> clue) {
        String key = buildKey(length, clue);
        return cache.computeIfAbsent(key, ignored -> generatePatterns(length, normalizeClue(clue)));
    }

    private String buildKey(int length, List<Integer> clue) {
        StringBuilder builder = new StringBuilder();
        builder.append(length).append(':');
        for (int i = 0; i < clue.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(clue.get(i));
        }
        return builder.toString();
    }

    private List<Integer> normalizeClue(List<Integer> clue) {
        if (clue.size() == 1 && clue.get(0) == 0) {
            return Collections.emptyList();
        }
        return clue;
    }

    private List<boolean[]> generatePatterns(int length, List<Integer> clue) {
        List<boolean[]> results = new ArrayList<>();

        if (clue.isEmpty()) {
            results.add(new boolean[length]);
            return results;
        }

        int[] groups = clue.stream().mapToInt(Integer::intValue).toArray();
        boolean[] line = new boolean[length];
        backtrack(results, line, length, groups, 0, 0);
        return results;
    }

    private void backtrack(List<boolean[]> results, boolean[] line, int length, int[] groups, int groupIndex, int position) {
        if (groupIndex >= groups.length) {
            Arrays.fill(line, position, length, false);
            results.add(Arrays.copyOf(line, length));
            return;
        }

        int remainingLength = 0;
        for (int i = groupIndex; i < groups.length; i++) {
            remainingLength += groups[i];
        }
        remainingLength += (groups.length - groupIndex - 1);

        int maxStart = length - remainingLength;
        for (int start = position; start <= maxStart; start++) {
            Arrays.fill(line, position, start, false);

            int end = start + groups[groupIndex];
            Arrays.fill(line, start, end, true);

            if (groupIndex == groups.length - 1) {
                backtrack(results, line, length, groups, groupIndex + 1, end);
            } else {
                line[end] = false;
                backtrack(results, line, length, groups, groupIndex + 1, end + 1);
            }
        }
    }
}