package com.wellrun.wellrun_backend.course;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/course")
@RequiredArgsConstructor
public class CourseController {

    private final CourseRoutingService courseRoutingService;
    private final ElevationService elevationService;
    private final AStarRoutingService aStarRoutingService;

    @GetMapping("/generate-course")
    public Map<String, Object> generateCourse(
            @RequestParam double startLat, @RequestParam double startLng,
            @RequestParam double endLat, @RequestParam double endLng) {

        Map<String, Object> response = new HashMap<>();

        try {
            // 1. 그리드 생성 및 고도 주입
            List<RouteNode> grid = courseRoutingService.generateGrid(startLat, startLng, endLat, endLng);
            elevationService.injectElevations(grid);

            // 2. 출발/도착지 맵핑 및 A* 알고리즘 탐색
            RouteNode startNode = findClosestNode(grid, startLat, startLng);
            RouteNode endNode = findClosestNode(grid, endLat, endLng);
            List<RouteNode> optimalPath = aStarRoutingService.findOptimalPath(grid, startNode, endNode);

            // ✨ 3. 알고리즘 고도 회피 성능 평가 지표 계산
            double pathElevationGain = 0.0; // 경로 누적 획득 고도 (오르막길 총합)
            double pathSumElev = 0.0;

            if (!optimalPath.isEmpty()) {
                pathSumElev += optimalPath.get(0).getElevation();
                for (int i = 1; i < optimalPath.size(); i++) {
                    double currentElev = optimalPath.get(i).getElevation();
                    double prevElev = optimalPath.get(i - 1).getElevation();
                    pathSumElev += currentElev;

                    // 오르막길인 경우에만 차이를 더함
                    if (currentElev > prevElev) {
                        pathElevationGain += (currentElev - prevElev);
                    }
                }
            }

            double pathAvgElev = optimalPath.isEmpty() ? 0 : pathSumElev / optimalPath.size();

            // 전체 지역(그리드)의 평균 고도 계산 (비교군)
            double gridSumElev = 0.0;
            for (RouteNode node : grid) {
                gridSumElev += node.getElevation();
            }
            double gridAvgElev = grid.isEmpty() ? 0 : gridSumElev / grid.size();

            // 4. 응답 데이터 포맷팅
            response.put("status", "SUCCESS");

            // 소수점 첫째 자리까지만 예쁘게 반올림하여 반환
            response.put("metrics", Map.of(
                    "1_grid_avg_elevation_m", Math.round(gridAvgElev * 10) / 10.0,
                    "2_path_avg_elevation_m", Math.round(pathAvgElev * 10) / 10.0,
                    "3_path_total_ascent_m", Math.round(pathElevationGain * 10) / 10.0,
                    "4_path_node_count", optimalPath.size()
            ));

            response.put("path", optimalPath);

        } catch (Exception e) {
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
        }

        return response;
    }

    private RouteNode findClosestNode(List<RouteNode> grid, double targetLat, double targetLng) {
        RouteNode closest = null;
        double minDistance = Double.MAX_VALUE;

        for (RouteNode node : grid) {
            double dist = LocationUtils.calculateDistance(node.getLat(), node.getLng(), targetLat, targetLng);
            if (dist < minDistance) {
                minDistance = dist;
                closest = node;
            }
        }
        return closest;
    }
}