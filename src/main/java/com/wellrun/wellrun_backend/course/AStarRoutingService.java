package com.wellrun.wellrun_backend.course;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class AStarRoutingService {

    // 노드 간 간격 (우리가 설정한 0.001도)
    private static final double GRID_SPACING = 0.001;

    /**
     * A* 알고리즘을 사용하여 출발지에서 목적지까지의 최적 러닝 코스를 찾습니다.
     */
    public List<RouteNode> findOptimalPath(List<RouteNode> grid, RouteNode startNode, RouteNode endNode) {
        // fCost(총 예상 비용)가 가장 낮은 노드를 먼저 꺼내기 위한 우선순위 큐 (열린 목록)
        PriorityQueue<RouteNode> openSet = new PriorityQueue<>(Comparator.comparingDouble(RouteNode::getFCost));
        // 이미 탐색을 완료한 노드들 (닫힌 목록)
        Set<RouteNode> closedSet = new HashSet<>();

        // 시작점 세팅
        startNode.setGCost(0);
        startNode.setHCost(calculateHeuristic(startNode, endNode));
        startNode.setFCost(startNode.getGCost() + startNode.getHCost());
        openSet.add(startNode);

        while (!openSet.isEmpty()) {
            RouteNode current = openSet.poll();

            // 목적지 근방 100m 이내로 들어오면 탐색 성공!
            if (isDestination(current, endNode)) {
                log.info("🎉 A* 알고리즘: 최적 러닝 코스 탐색 완료!");
                return reconstructPath(current);
            }

            closedSet.add(current);

            // 주변 8방향 이웃 노드 탐색
            for (RouteNode neighbor : getNeighbors(current, grid)) {
                if (closedSet.contains(neighbor)) continue;

                // ✨ [핵심] 이동 비용 계산 = 실제 거리 + 오르막길 페널티
                double distanceCost = LocationUtils.calculateDistance(
                        current.getLat(), current.getLng(), neighbor.getLat(), neighbor.getLng());
                double elevationPenalty = calculateElevationPenalty(current.getElevation(), neighbor.getElevation());

                double tentativeGCost = current.getGCost() + distanceCost + elevationPenalty;

                // 더 효율적인 경로를 발견했거나, 처음 밟아보는 노드인 경우
                if (tentativeGCost < neighbor.getGCost()) {
                    neighbor.setParent(current); // 나중에 길을 되짚어가기 위해 발자국 남기기
                    neighbor.setGCost(tentativeGCost);
                    neighbor.setHCost(calculateHeuristic(neighbor, endNode));
                    neighbor.setFCost(neighbor.getGCost() + neighbor.getHCost());

                    if (!openSet.contains(neighbor)) {
                        openSet.add(neighbor);
                    }
                }
            }
        }

        log.warn("A* 알고리즘: 목적지까지의 경로를 찾을 수 없습니다.");
        return new ArrayList<>(); // 길을 못 찾은 경우 빈 리스트 반환
    }

    // 휴리스틱 (H-Cost): 현재 위치에서 목적지까지의 직선 거리
    private double calculateHeuristic(RouteNode node, RouteNode endNode) {
        return LocationUtils.calculateDistance(node.getLat(), node.getLng(), endNode.getLat(), endNode.getLng());
    }

    // ✨ 오르막길 페널티 계산 (경사가 가파를수록 비용을 대폭 증가시켜 알고리즘이 우회하게 만듦)
    private double calculateElevationPenalty(double currentAlt, double neighborAlt) {
        double diff = neighborAlt - currentAlt;
        if (diff > 0) {
            // 오르막길이면 고도차의 5%를 이동 거리에 가산 (이 수치를 높이면 평지만 고집하게 됨)
            return diff * 0.05;
        }
        return 0; // 내리막길이나 평지는 페널티 없음
    }

    // 목적지 도착 판별 (100m 반경)
    private boolean isDestination(RouteNode node, RouteNode endNode) {
        return LocationUtils.calculateDistance(node.getLat(), node.getLng(), endNode.getLat(), endNode.getLng()) <= 0.1;
    }

    // 그리드 내에서 내 주변에 있는 인접 노드 찾기
    private List<RouteNode> getNeighbors(RouteNode current, List<RouteNode> grid) {
        List<RouteNode> neighbors = new ArrayList<>();
        for (RouteNode node : grid) {
            if (node == current) continue;

            double latDiff = Math.abs(node.getLat() - current.getLat());
            double lngDiff = Math.abs(node.getLng() - current.getLng());

            // 위도/경도 차이가 0.0015 이하인 점들만 주변 이웃으로 간주 (대각선 포함)
            if (latDiff <= GRID_SPACING * 1.5 && lngDiff <= GRID_SPACING * 1.5) {
                neighbors.add(node);
            }
        }
        return neighbors;
    }

    // 부모 노드를 역추적하여 최종 출발지~도착지 배열을 완성
    private List<RouteNode> reconstructPath(RouteNode current) {
        List<RouteNode> path = new ArrayList<>();
        while (current != null) {
            path.add(current);
            current = current.getParent(); // 내가 밟고 온 이전 노드로 거슬러 올라감
        }
        Collections.reverse(path); // 역추적했으니 순서를 다시 뒤집어줌
        return path;
    }
}