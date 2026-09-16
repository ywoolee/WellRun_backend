package com.wellrun.wellrun_backend.course;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class AStarRoutingService {

    private static final double GRID_SPACING = 0.001;

    // ✨ [수정] 파라미터에 isFlat(평지 모드 여부) 추가
    public List<RouteNode> findOptimalPath(List<RouteNode> grid, RouteNode startNode, RouteNode endNode, boolean isFlat) {
        PriorityQueue<RouteNode> openSet = new PriorityQueue<>(Comparator.comparingDouble(RouteNode::getFCost));
        Set<RouteNode> closedSet = new HashSet<>();

        startNode.setGCost(0);
        startNode.setHCost(calculateHeuristic(startNode, endNode));
        startNode.setFCost(startNode.getGCost() + startNode.getHCost());
        openSet.add(startNode);

        while (!openSet.isEmpty()) {
            RouteNode current = openSet.poll();

            if (isDestination(current, endNode)) {
                log.info("🎉 A* 알고리즘 탐색 성공!");
                return reconstructPath(current);
            }

            closedSet.add(current);

            for (RouteNode neighbor : getNeighbors(current, grid)) {
                if (closedSet.contains(neighbor)) continue;

                double distanceCost = LocationUtils.calculateDistance(
                        current.getLat(), current.getLng(), neighbor.getLat(), neighbor.getLng());

                // ✨ [수정] isFlat이 true(평지 모드)일 때만 오르막 페널티 적용
                double elevationPenalty = isFlat ? calculateElevationPenalty(current.getElevation(), neighbor.getElevation()) : 0.0;

                // ✨ [핵심 수정] 용암(지나간 길) 페널티 추가!
                double lavaPenalty = neighbor.getPenaltyCost();

                double tentativeGCost = current.getGCost() + distanceCost + elevationPenalty + lavaPenalty;

                if (tentativeGCost < neighbor.getGCost()) {
                    neighbor.setParent(current);
                    neighbor.setGCost(tentativeGCost);
                    neighbor.setHCost(calculateHeuristic(neighbor, endNode));
                    neighbor.setFCost(neighbor.getGCost() + neighbor.getHCost());

                    if (!openSet.contains(neighbor)) {
                        openSet.add(neighbor);
                    }
                }
            }
        }
        return new ArrayList<>();
    }

    private double calculateHeuristic(RouteNode node, RouteNode endNode) {
        return LocationUtils.calculateDistance(node.getLat(), node.getLng(), endNode.getLat(), endNode.getLng());
    }

    private double calculateElevationPenalty(double currentAlt, double neighborAlt) {
        double diff = neighborAlt - currentAlt;
        return (diff > 0) ? diff * 0.05 : 0;
    }

    private boolean isDestination(RouteNode node, RouteNode endNode) {
        return LocationUtils.calculateDistance(node.getLat(), node.getLng(), endNode.getLat(), endNode.getLng()) <= 0.1;
    }

    private List<RouteNode> getNeighbors(RouteNode current, List<RouteNode> grid) {
        List<RouteNode> neighbors = new ArrayList<>();
        for (RouteNode node : grid) {
            if (node == current) continue;
            double latDiff = Math.abs(node.getLat() - current.getLat());
            double lngDiff = Math.abs(node.getLng() - current.getLng());
            if (latDiff <= GRID_SPACING * 1.5 && lngDiff <= GRID_SPACING * 1.5) {
                neighbors.add(node);
            }
        }
        return neighbors;
    }

    private List<RouteNode> reconstructPath(RouteNode current) {
        List<RouteNode> path = new ArrayList<>();
        while (current != null) {
            path.add(current);
            current = current.getParent();
        }
        Collections.reverse(path);
        return path;
    }
}