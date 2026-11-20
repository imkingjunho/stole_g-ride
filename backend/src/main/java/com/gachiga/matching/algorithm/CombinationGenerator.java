package com.gachiga.matching.algorithm;

import com.gachiga.contract.ride.WaitingRequest;
import java.util.*;

public class CombinationGenerator {

    private static final int MIN_COMBINATION_SIZE = 2;
    private static final int MAX_COMBINATION_SIZE = 4;

    public static List<List<WaitingRequest>> generateCombinations(List<List<WaitingRequest>> clusters) {
        return generateCombinations(clusters, MAX_COMBINATION_SIZE);
    }

    public static List<List<WaitingRequest>> generateCombinations(List<List<WaitingRequest>> clusters, int maxSize) {
        List<List<WaitingRequest>> allCombinations = new ArrayList<>();

        for (List<WaitingRequest> cluster : clusters) {
            for (int size = MIN_COMBINATION_SIZE; size <= maxSize; size++) {
                if (cluster.size() >= size) {
                    List<List<WaitingRequest>> combinations = chooseRiders(cluster, size);
                    allCombinations.addAll(combinations);
                }
            }
        }

        return allCombinations;
    }

    private static List<List<WaitingRequest>> chooseRiders(List<WaitingRequest> items, int size) {
        List<List<WaitingRequest>> result = new ArrayList<>();
        generateHelper(items, size, 0, new ArrayList<>(), result);
        return result;
    }

    private static void generateHelper(
            List<WaitingRequest> items,
            int size,
            int startIdx,
            List<WaitingRequest> current,
            List<List<WaitingRequest>> result) {
        
        if (current.size() == size) {
            result.add(new ArrayList<>(current));
            return;
        }

        for (int i = startIdx; i < items.size(); i++) {
            current.add(items.get(i));
            generateHelper(items, size, i + 1, current, result);
            current.remove(current.size() - 1);
        }
    }
}