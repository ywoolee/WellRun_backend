package com.wellrun.wellrun_backend.course;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class AStarRoutingService {

    private static final double GRID_SPACING = 0.001;

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
                log.info("🎉 A* 알고리즘 탐색 성공! (U턴 방지 적용됨)");
                return reconstructPath(current);
            }

            closedSet.add(current);

            for (RouteNode neighbor : getNeighbors(current, grid)) {
                if (closedSet.contains(neighbor)) continue;

                double distanceCost = LocationUtils.calculateDistance(
                        current.getLat(), current.getLng(), neighbor.getLat(), neighbor.getLng());

                // ✨ [신규 핵심 로직] U턴 및 지그재그 급커브 방지 페널티 (직진 관성)
                double turnPenalty = 0.0;
                if (current.getParent() != null) {
                    // 이전 방향 벡터 (부모 -> 현재)
                    double dx1 = current.getLng() - current.getParent().getLng();
                    double dy1 = current.getLat() - current.getParent().getLat();

                    // 다음 방향 벡터 (현재 -> 이웃)
                    double dx2 = neighbor.getLng() - current.getLng();
                    double dy2 = neighbor.getLat() - current.getLat();

                    double len1 = Math.sqrt(dx1 * dx1 + dy1 * dy1);
                    double len2 = Math.sqrt(dx2 * dx2 + dy2 * dy2);

                    if (len1 > 0 && len2 > 0) {
                        // 두 벡터의 코사인 유사도 계산
                        // cosTheta 값이 1이면 직진, 0이면 90도 직각, -1이면 180도 완전 U턴을 의미합니다.
                        double cosTheta = (dx1 * dx2 + dy1 * dy2) / (len1 * len2);

                        if (cosTheta < -0.3) {
                            // 약 107도 이상 꺾이는 뾰족한 U턴이나 역주행: 절대 안 가도록 초강력 페널티 부과! (5km 추가 효과)
                            turnPenalty = 5.0;
                        } else if (cosTheta < 0.5) {
                            // 약 60도 ~ 107도 꺾이는 자잘한 지그재그 골목길: 가벼운 벌점으로 웬만하면 직진을 유도
                            turnPenalty = 0.5;
                        }
                    }
                }

                // 지형(고도) 페널티 계산
                double terrainCost = calculateTerrainCost(current.getElevation(), neighbor.getElevation(), isFlat);
                double lavaPenalty = neighbor.getPenaltyCost();

                // 턴 페널티(turnPenalty)까지 모두 합산
                double finalMoveCost = Math.max(distanceCost + terrainCost, 0.01);
                double tentativeGCost = current.getGCost() + finalMoveCost + lavaPenalty + turnPenalty;

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

    private double calculateTerrainCost(double currentAlt, double neighborAlt, boolean isFlat) {
        double elevationChange = neighborAlt - currentAlt;
        if (isFlat) {
            if (elevationChange > 0) {
                return elevationChange * 0.05;
            }
        } else {
            if (elevationChange > 0) {
                return -(elevationChange * 0.02);
            } else {
                return 0.02;
            }
        }
        return 0.0;
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