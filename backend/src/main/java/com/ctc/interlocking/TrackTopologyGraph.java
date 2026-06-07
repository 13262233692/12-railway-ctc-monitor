package com.ctc.interlocking;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TrackTopologyGraph {

    private final Map<String, Set<String>> adjacencyList = new ConcurrentHashMap<>();
    private final Map<String, double[]> nodeCoordinates = new ConcurrentHashMap<>();

    public void addNode(String nodeId, double x, double y) {
        adjacencyList.computeIfAbsent(nodeId, k -> ConcurrentHashMap.newKeySet());
        nodeCoordinates.put(nodeId, new double[]{x, y});
    }

    public void addDirectedEdge(String from, String to) {
        adjacencyList.computeIfAbsent(from, k -> ConcurrentHashMap.newKeySet());
        adjacencyList.computeIfAbsent(to, k -> ConcurrentHashMap.newKeySet());
        adjacencyList.get(from).add(to);
    }

    public void addBidirectionalEdge(String a, String b) {
        addDirectedEdge(a, b);
        addDirectedEdge(b, a);
    }

    public Set<String> getNeighbors(String nodeId) {
        return adjacencyList.getOrDefault(nodeId, Set.of());
    }

    public double[] getCoordinates(String nodeId) {
        return nodeCoordinates.get(nodeId);
    }

    public Set<String> getAllNodes() {
        return adjacencyList.keySet();
    }

    public List<String> dfsFrom(String startNode, int maxDepth) {
        List<String> visited = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        Map<String, Integer> depthMap = new HashMap<>();

        stack.push(startNode);
        depthMap.put(startNode, 0);

        while (!stack.isEmpty()) {
            String current = stack.pop();
            if (seen.contains(current)) continue;
            if (depthMap.getOrDefault(current, 0) > maxDepth) continue;

            seen.add(current);
            visited.add(current);

            int currentDepth = depthMap.getOrDefault(current, 0);
            for (String neighbor : getNeighbors(current)) {
                if (!seen.contains(neighbor)) {
                    stack.push(neighbor);
                    depthMap.put(neighbor, currentDepth + 1);
                }
            }
        }

        return visited;
    }

    public List<String> findPath(String start, String end) {
        if (!adjacencyList.containsKey(start) || !adjacencyList.containsKey(end)) {
            return List.of();
        }

        Map<String, String> parent = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();

        queue.add(start);
        visited.add(start);
        parent.put(start, null);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (current.equals(end)) {
                List<String> path = new ArrayList<>();
                String node = end;
                while (node != null) {
                    path.add(0, node);
                    node = parent.get(node);
                }
                return path;
            }

            for (String neighbor : getNeighbors(current)) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    parent.put(neighbor, current);
                    queue.add(neighbor);
                }
            }
        }

        return List.of();
    }

    public Set<String> getReachableNodes(String start, int maxDepth) {
        return new HashSet<>(dfsFrom(start, maxDepth));
    }
}
