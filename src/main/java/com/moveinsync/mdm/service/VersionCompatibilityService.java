package com.moveinsync.mdm.service;

import com.moveinsync.mdm.dto.request.CompatibilityRuleRequest;
import com.moveinsync.mdm.dto.response.CompatibilityCheckResponse;
import com.moveinsync.mdm.entity.VersionCompatibility;
import com.moveinsync.mdm.exception.DowngradeNotAllowedException;
import com.moveinsync.mdm.repository.VersionCompatibilityRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Manages version compatibility rules and performs BFS-based
 * upgrade path validation.
 *
 * The compatibility rules form a directed graph where:
 * - Nodes = version codes
 * - Edges = allowed direct upgrades
 *
 * To check if version A → Z is valid, we perform BFS from A
 * and see if Z is reachable, then return the shortest path.
 */
@Service
@RequiredArgsConstructor
public class VersionCompatibilityService {

    private static final Logger log = LoggerFactory.getLogger(VersionCompatibilityService.class);
    private final VersionCompatibilityRepository compatibilityRepository;

    @Transactional
    public String createRule(CompatibilityRuleRequest request) {
        // Downgrade prevention at rule level
        if (request.getToVersionCode() <= request.getFromVersionCode()) {
            throw new DowngradeNotAllowedException(
                    "Cannot define a compatibility rule where target version (" + request.getToVersionCode() +
                            ") is lower than or equal to source version (" + request.getFromVersionCode() + ").");
        }

        if (compatibilityRepository.existsByFromVersionCodeAndToVersionCode(
                request.getFromVersionCode(), request.getToVersionCode())) {
            throw new IllegalArgumentException(
                    "Compatibility rule from " + request.getFromVersionCode() +
                            " to " + request.getToVersionCode() + " already exists.");
        }

        VersionCompatibility rule = VersionCompatibility.builder()
                .fromVersionCode(request.getFromVersionCode())
                .toVersionCode(request.getToVersionCode())
                .requiresIntermediate(
                        request.getRequiresIntermediate() != null ? request.getRequiresIntermediate() : false)
                .intermediateVersionCode(request.getIntermediateVersionCode())
                .build();

        compatibilityRepository.save(rule);

        log.info("Compatibility rule created: {} → {} (intermediate: {})",
                request.getFromVersionCode(), request.getToVersionCode(),
                request.getRequiresIntermediate());

        String message = "Compatibility rule created. Version " + request.getFromVersionCode() +
                " → " + request.getToVersionCode();
        if (Boolean.TRUE.equals(request.getRequiresIntermediate()) && request.getIntermediateVersionCode() != null) {
            message += " requires intermediate upgrade through " + request.getIntermediateVersionCode() + ".";
        } else {
            message += " (direct upgrade allowed).";
        }
        return message;
    }

    /**
     * Check if an upgrade path exists from source to target using BFS.
     * Returns the shortest valid path through the compatibility graph.
     *
     * Time complexity: O(V + E) where V = versions, E = compatibility rules
     * Space complexity: O(V) for BFS queue and visited set
     */
    @Transactional(readOnly = true)
    public CompatibilityCheckResponse checkUpgradePath(Integer fromVersionCode, Integer toVersionCode) {
        // Downgrade check
        if (toVersionCode <= fromVersionCode) {
            return CompatibilityCheckResponse.builder()
                    .allowed(false)
                    .requiresIntermediate(false)
                    .upgradePath(null)
                    .message("Downgrade from version " + fromVersionCode + " to " + toVersionCode + " is not allowed.")
                    .build();
        }

        // Build adjacency list from all compatibility rules
        List<VersionCompatibility> allRules = compatibilityRepository.findAll();
        Map<Integer, List<Integer>> adjacencyList = new HashMap<>();

        for (VersionCompatibility rule : allRules) {
            adjacencyList.computeIfAbsent(rule.getFromVersionCode(), k -> new ArrayList<>())
                    .add(rule.getToVersionCode());
        }

        // BFS to find shortest path
        List<Integer> path = bfsShortestPath(adjacencyList, fromVersionCode, toVersionCode);

        if (path == null) {
            return CompatibilityCheckResponse.builder()
                    .allowed(false)
                    .requiresIntermediate(false)
                    .upgradePath(null)
                    .message("No valid upgrade path defined from version " + fromVersionCode +
                            " to " + toVersionCode + ".")
                    .build();
        }

        boolean requiresIntermediate = path.size() > 2;

        String message;
        if (requiresIntermediate) {
            message = "Version " + fromVersionCode + " must follow upgrade path: " +
                    path.stream().map(String::valueOf).reduce((a, b) -> a + " → " + b).orElse("");
        } else {
            message = "Direct upgrade from version " + fromVersionCode + " to " + toVersionCode + " is allowed.";
        }

        return CompatibilityCheckResponse.builder()
                .allowed(true)
                .requiresIntermediate(requiresIntermediate)
                .upgradePath(path)
                .message(message)
                .build();
    }

    /**
     * BFS shortest path in directed graph.
     * Returns the path as a list of version codes, or null if no path exists.
     */
    private List<Integer> bfsShortestPath(Map<Integer, List<Integer>> graph,
            Integer source, Integer target) {
        if (source.equals(target)) {
            return List.of(source);
        }

        Queue<List<Integer>> queue = new LinkedList<>();
        Set<Integer> visited = new HashSet<>();

        queue.add(List.of(source));
        visited.add(source);

        while (!queue.isEmpty()) {
            List<Integer> currentPath = queue.poll();
            Integer currentNode = currentPath.get(currentPath.size() - 1);

            List<Integer> neighbors = graph.getOrDefault(currentNode, Collections.emptyList());

            for (Integer neighbor : neighbors) {
                if (neighbor.equals(target)) {
                    List<Integer> completePath = new ArrayList<>(currentPath);
                    completePath.add(neighbor);
                    return completePath;
                }

                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    List<Integer> newPath = new ArrayList<>(currentPath);
                    newPath.add(neighbor);
                    queue.add(newPath);
                }
            }
        }

        return null; // No path found
    }
}
