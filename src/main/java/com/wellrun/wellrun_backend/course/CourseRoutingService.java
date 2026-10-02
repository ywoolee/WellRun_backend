package com.wellrun.wellrun_backend.course;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseRoutingService {

    private final AStarRoutingService aStarRoutingService;
    private static final double GRID_SPACING = 0.001;
    // ✨ 방향을 지정하기 위한 Enum 추가 (클래스 내부 아무 데나 두시면 됩니다)
    public enum Direction {
        LEFT, RIGHT, ANY
    }

    public List<RouteNode> generateGrid(double startLat, double startLng, double endLat, double endLng, double targetDistance) {
        List<RouteNode> gridNodes = new ArrayList<>();

        double directDistance = LocationUtils.calculateDistance(startLat, startLng, endLat, endLng);

        // 1. 타원의 장축(최대 허용 거리) 설정
        // 꼬불꼬불 뛰는 걸 감안해서 목표 거리보다 15% 정도 넉넉하게 마진을 줍니다.
        double maxAllowedDistance = (targetDistance > 0) ? targetDistance * 1.15 : directDistance * 1.5;

        // 2. 일단 타원을 넉넉히 덮을 수 있는 사각형(Bounding Box)의 범위를 잡습니다.
        double offset = (maxAllowedDistance / 2.0) * 0.012;
        offset = Math.max(offset, GRID_SPACING * 4); // 최소 여백 보장

        double minLat = Math.min(startLat, endLat) - offset;
        double maxLat = Math.max(startLat, endLat) + offset;
        double minLng = Math.min(startLng, endLng) - offset;
        double maxLng = Math.max(startLng, endLng) + offset;

        // 3. 사각형 안을 탐색하되, 타원 밖에 있는 노드는 버립니다!
        for (double lat = minLat; lat <= maxLat; lat += GRID_SPACING) {
            for (double lng = minLng; lng <= maxLng; lng += GRID_SPACING) {

                // ✨ 타원 방정식 필터링 (두 초점으로부터의 거리 합)
                double distFromStart = LocationUtils.calculateDistance(startLat, startLng, lat, lng);
                double distFromEnd = LocationUtils.calculateDistance(endLat, endLng, lat, lng);

                // 두 거리의 합이 최대 허용 거리(장축)보다 작거나 같은 경우에만(타원 내부에만) 노드 추가!
                if ((distFromStart + distFromEnd) <= maxAllowedDistance) {
                    gridNodes.add(new RouteNode(lat, lng));
                }
            }
        }

        log.info("🎯 타원형 그리드 최적화 완료! 생성된 노드 수: {}", gridNodes.size());
        return gridNodes;
    }

    // ✨ [개편된 편도 엔진] 3방향(좌, 우, 자유)을 다 찔러보고 거리+고도를 종합 채점하는 심사위원 로직!
    public List<RouteNode> generateOneWayCourse(List<RouteNode> grid, RouteNode startNode, RouteNode endNode, double targetDistance, boolean isFlat) {
        double directDistance = LocationUtils.calculateDistance(startNode.getLat(), startNode.getLng(), endNode.getLat(), endNode.getLng());

        if (targetDistance <= directDistance * 1.2) {
            log.info("🎯 편도 최단거리 탐색: 목표 거리가 짧아 다이렉트로 연결합니다.");
            return aStarRoutingService.findOptimalPath(grid, startNode, endNode, isFlat);
        }

        List<RouteNode> bestPath = new ArrayList<>();
        // ✨ 변경점 1: minError 대신 '최저 점수(minScore)'를 추적합니다.
        double minScore = Double.MAX_VALUE;

        // 3가지 방향(좌, 우, 자유)을 돌면서 각각 코스를 만들어 봅니다.
        Direction[] directions = {Direction.LEFT, Direction.RIGHT, Direction.ANY};

        for (Direction dir : directions) {
            double currentTarget = targetDistance;
            List<RouteNode> currentFinalPath = new ArrayList<>();
            int maxAttempts = 3;
            double currentActualDist = 0.0;

            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                resetGridRoutingStates(grid);
                resetGridPenalties(grid);

                double adjustedTarget = currentTarget / 1.25;
                // 경유지를 찾을 때 '방향(dir)'을 던져줍니다!
                RouteNode wayPoint = getDetourWaypoint(grid, startNode, endNode, adjustedTarget, dir);

                if (wayPoint == null) break;

                List<RouteNode> firstHalf = aStarRoutingService.findOptimalPath(grid, startNode, wayPoint, isFlat);
                if (firstHalf.isEmpty()) break;

                int safeMargin = firstHalf.size() / 10;
                for (int i = safeMargin; i < firstHalf.size() - safeMargin; i++) {
                    RouteNode outNode = firstHalf.get(i);
                    for (RouteNode gridNode : grid) {
                        if (LocationUtils.calculateDistance(outNode.getLat(), outNode.getLng(), gridNode.getLat(), gridNode.getLng()) < 0.15) {
                            gridNode.addPenaltyCost(8.0);
                        }
                    }
                }

                resetGridRoutingStates(grid);
                List<RouteNode> secondHalf = aStarRoutingService.findOptimalPath(grid, wayPoint, endNode, isFlat);

                currentFinalPath = new ArrayList<>(firstHalf);
                if (!secondHalf.isEmpty()) {
                    for (int i = 1; i < secondHalf.size(); i++) {
                        currentFinalPath.add(secondHalf.get(i));
                    }
                }

                currentActualDist = 0.0;
                if (currentFinalPath.size() > 1) {
                    for (int i = 1; i < currentFinalPath.size(); i++) {
                        currentActualDist += LocationUtils.calculateDistance(currentFinalPath.get(i-1).getLat(), currentFinalPath.get(i-1).getLng(), currentFinalPath.get(i).getLat(), currentFinalPath.get(i).getLng());
                    }
                }

                double error = Math.abs(currentActualDist - targetDistance);
                if (error <= (targetDistance * 0.1) || error <= 0.4) {
                    break; // 오차 범위 내면 해당 방향의 탐색은 조기 종료
                }

                if (currentActualDist > 0) {
                    currentTarget = currentTarget * (targetDistance / currentActualDist);
                }
            }

            // -----------------------------------------------------
            // ✨ 변경점 2: [신규 심사 로직] 고도와 거리를 종합 채점합니다!
            // -----------------------------------------------------
            if (currentFinalPath.isEmpty()) continue;

            // 1) 누적 획득 고도 계산
            double totalAscent = 0.0;
            for (int i = 1; i < currentFinalPath.size(); i++) {
                double prevElev = currentFinalPath.get(i-1).getElevation();
                double currElev = currentFinalPath.get(i).getElevation();
                if (currElev > prevElev) {
                    totalAscent += (currElev - prevElev);
                }
            }

            // 2) 거리 오차 점수 (1km 오차 = 1000점 벌점)
            double distanceError = Math.abs(currentActualDist - targetDistance);
            double distanceScore = distanceError * 1000.0;

            // 3) 고도 점수 계산 (isFlat 옵션 반영)
            double elevationScore = 0.0;
            if (isFlat) {
                // 평지를 원할 때: 고도 1m 오를 때마다 5점 벌점 (언덕 극혐)
                elevationScore = totalAscent * 5.0;
            } else {
                // 언덕을 원할 때: 고도 1m 오를 때마다 2점 가산점 (벌점 차감)
                elevationScore = totalAscent * -2.0;
            }

            // 4) 최종 종합 점수
            double totalScore = distanceScore + elevationScore;

            // 5) 심사 결과 반영: 거리가 너무 터무니없지 않은 선(오차 15% 이내)에서 점수가 제일 낮은 놈 1등!
            if (distanceError <= targetDistance * 0.15 && totalScore < minScore) {
                minScore = totalScore;
                bestPath = new ArrayList<>(currentFinalPath);

                log.info("🏆 [심사 갱신] {} 방향 1등! (오차: {}km, 누적고도: {}m, 총점: {})",
                        dir, Math.round(distanceError*100)/100.0, Math.round(totalAscent), Math.round(totalScore));
            }
        }

        resetGridRoutingStates(grid);
        resetGridPenalties(grid);
        return bestPath;
    }

    // (기존 루프 생성 엔진 그대로 유지)
    public List<RouteNode> generateLoopCourse(List<RouteNode> grid, RouteNode startNode, double targetDistance, boolean isFlat) {
        double currentTarget = targetDistance;
        List<RouteNode> finalLoopCourse = new ArrayList<>();
        int maxAttempts = 3;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            resetGridRoutingStates(grid);
            resetGridPenalties(grid);

            double adjustedHalfDistance = (currentTarget / 1.25) / 2.0;
            RouteNode wayPoint = getMockWaypoint(grid, startNode, adjustedHalfDistance);

            if (wayPoint == null) break;

            List<RouteNode> outboundPath = aStarRoutingService.findOptimalPath(grid, startNode, wayPoint, isFlat);
            if (outboundPath.isEmpty()) break;

            int safeMargin = outboundPath.size() / 10;
            for (int i = safeMargin; i < outboundPath.size() - safeMargin; i++) {
                RouteNode outNode = outboundPath.get(i);
                for (RouteNode gridNode : grid) {
                    double dist = LocationUtils.calculateDistance(outNode.getLat(), outNode.getLng(), gridNode.getLat(), gridNode.getLng());
                    if (dist < 0.2) gridNode.addPenaltyCost(20.0);
                    else if (dist < 0.4) gridNode.addPenaltyCost(8.0);
                }
            }

            resetGridRoutingStates(grid);

            List<RouteNode> returnPath = aStarRoutingService.findOptimalPath(grid, wayPoint, startNode, isFlat);

            finalLoopCourse = new ArrayList<>(outboundPath);
            if (!returnPath.isEmpty()) {
                for (int i = 1; i < returnPath.size(); i++) {
                    finalLoopCourse.add(returnPath.get(i));
                }
            }

            double actualDist = 0.0;
            if (finalLoopCourse.size() > 1) {
                for (int i = 1; i < finalLoopCourse.size(); i++) {
                    actualDist += LocationUtils.calculateDistance(finalLoopCourse.get(i-1).getLat(), finalLoopCourse.get(i-1).getLng(), finalLoopCourse.get(i).getLat(), finalLoopCourse.get(i).getLng());
                }
            }

            log.info("🔄 [루프 자동 보정] 시도 {}/{} - 내부 타겟: {}km -> 실제 결과: {}km", attempt, maxAttempts, Math.round(currentTarget*100)/100.0, Math.round(actualDist*100)/100.0);

            double error = Math.abs(actualDist - targetDistance);
            if (error <= (targetDistance * 0.1) || error <= 0.4) {
                log.info("✅ 루프 오차 범위 통과! 탐색 종료.");
                break;
            }

            if (actualDist > 0) currentTarget = currentTarget * (targetDistance / actualDist);
        }

        resetGridRoutingStates(grid);
        resetGridPenalties(grid);
        return finalLoopCourse;
    }

    // 편도 전용 경유지 찾기 (출발지->경유지->목적지 합이 타겟 거리가 되도록)
    // ✨ 경유지 탐색 시 '방향(Direction)' 필터링 추가 (벡터의 외적 사용)
    private RouteNode getDetourWaypoint(List<RouteNode> grid, RouteNode startNode, RouteNode endNode, double targetTotalDist, Direction dir) {
        RouteNode bestNode = null;
        double minDiff = Double.MAX_VALUE;

        for (RouteNode node : grid) {
            double distFromStart = LocationUtils.calculateDistance(startNode.getLat(), startNode.getLng(), node.getLat(), node.getLng());
            double distToEnd = LocationUtils.calculateDistance(node.getLat(), node.getLng(), endNode.getLat(), endNode.getLng());
            double totalDist = distFromStart + distToEnd;
            double diff = Math.abs(totalDist - targetTotalDist);

            if (distFromStart > 0.3 && distToEnd > 0.3) {
                // 벡터 외적 (Cross Product) 공식을 사용해 노드가 선분 기준 왼쪽인지 오른쪽인지 판별!
                double crossProduct = (endNode.getLng() - startNode.getLng()) * (node.getLat() - startNode.getLat())
                        - (endNode.getLat() - startNode.getLat()) * (node.getLng() - startNode.getLng());

                boolean matchDir = true;
                if (dir == Direction.LEFT && crossProduct <= 0) matchDir = false;
                if (dir == Direction.RIGHT && crossProduct >= 0) matchDir = false;

                if (matchDir && diff < minDiff) {
                    minDiff = diff;
                    bestNode = node;
                }
            }
        }
        return bestNode;
    }

    private RouteNode getMockWaypoint(List<RouteNode> grid, RouteNode startNode, double targetDist) {
        RouteNode bestNode = null;
        double minDiff = Double.MAX_VALUE;
        for (RouteNode node : grid) {
            double dist = LocationUtils.calculateDistance(startNode.getLat(), startNode.getLng(), node.getLat(), node.getLng());
            double diff = Math.abs(dist - targetDist);
            if (diff < minDiff && dist > 0.2) {
                minDiff = diff;
                bestNode = node;
            }
        }
        return bestNode;
    }

    private void resetGridRoutingStates(List<RouteNode> grid) {
        for (RouteNode node : grid) {
            node.setGCost(Double.MAX_VALUE);
            node.setHCost(0);
            node.setFCost(0);
            node.setParent(null);
        }
    }

    private void resetGridPenalties(List<RouteNode> grid) {
        for (RouteNode node : grid) {
            node.resetPenaltyCost();
        }
    }
}