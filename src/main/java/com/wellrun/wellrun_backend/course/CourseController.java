package com.wellrun.wellrun_backend.course;

import com.fasterxml.jackson.databind.ObjectMapper; // ✨ JSON 파싱용 추가
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/course")
@RequiredArgsConstructor
public class CourseController {

    private final CourseRoutingService courseRoutingService;
    private final ElevationService elevationService;
    private final AStarRoutingService aStarRoutingService;
    private final TmapService tmapService; // ✨ Tmap 연동 서비스 주입

    @GetMapping("/generate-course")
    public Map<String, Object> generateCourse(
            @RequestParam double startLat, @RequestParam double startLng,
            @RequestParam double endLat, @RequestParam double endLng,
            @RequestParam(defaultValue = "0.0") double targetDistance,
            @RequestParam(defaultValue = "true") boolean isFlat) {

        Map<String, Object> response = new HashMap<>();

        try {
            double directDistance = LocationUtils.calculateDistance(startLat, startLng, endLat, endLng);

            // 1. 도화지(그리드)와 고도 데이터는 딱 한 번만 세팅합니다!
            List<RouteNode> grid = courseRoutingService.generateGrid(startLat, startLng, endLat, endLng, targetDistance);
            elevationService.injectElevations(grid);

            RouteNode startNode = findClosestNode(grid, startLat, startLng);
            RouteNode endNode = findClosestNode(grid, endLat, endLng);

            Course savedCourse = null;

            // ✨ [신규] Tmap 피드백 루프 설정
            int maxTmapAttempts = 3; // 최대 3번까지만 재시도 (무한 루프 방지)
            double currentAStarTarget = targetDistance; // A* 엔진에게 던져줄 유동적인 목표 거리

            for (int attempt = 1; attempt <= maxTmapAttempts; attempt++) {
                List<RouteNode> optimalPath;

                // 2. A* 엔진으로 뼈대 생성
                if (directDistance > 0.1) {
                    optimalPath = courseRoutingService.generateOneWayCourse(grid, startNode, endNode, currentAStarTarget, isFlat);
                } else {
                    optimalPath = courseRoutingService.generateLoopCourse(grid, startNode, currentAStarTarget, isFlat);
                }

                // 3. 고도 계산
                double pathElevationGain = 0.0;
                if (!optimalPath.isEmpty()) {
                    for (int i = 1; i < optimalPath.size(); i++) {
                        double currElev = optimalPath.get(i).getElevation();
                        double prevElev = optimalPath.get(i - 1).getElevation();
                        if (currElev > prevElev) {
                            pathElevationGain += (currElev - prevElev);
                        }
                    }
                }

                // 4. Tmap API 호출 및 스무딩
                savedCourse = tmapService.smoothPathAndSave(optimalPath, targetDistance, pathElevationGain);

                if (savedCourse != null) {
                    double actualTmapDist = savedCourse.getActualDistance();
                    double error = Math.abs(actualTmapDist - targetDistance);

                    log.info("🔄 [Tmap 검증 {}회차] 목표: {}km / Tmap 실제: {}km (오차: {}km)",
                            attempt, targetDistance, actualTmapDist, Math.round(error * 100) / 100.0);

                    // 5. 합격 기준: 오차가 목표 거리의 10% 이내거나 0.2km 이내면 즉시 통과!
                    if (error <= (targetDistance * 0.1) || error <= 0.2) {
                        log.info("✅ 오차 범위 내 합격! 코스 생성을 확정합니다.");
                        break; // for문 탈출
                    }

                    // 6. 불합격 시 보정: 거리가 초과/미달된 비율만큼 다음 A* 목표 거리를 조정
                    if (attempt < maxTmapAttempts) {
                        currentAStarTarget = currentAStarTarget * (targetDistance / actualTmapDist);
                        log.info("🛠️ 오차 보정을 위해 다음 A* 목표 거리를 {}km로 조정하여 재도전합니다.", Math.round(currentAStarTarget * 100) / 100.0);

                        // DB에 잘못 저장된 이전 코스는 삭제 (찌꺼기 데이터 방지)
                        // courseRepository.delete(savedCourse); // 필요하다면 이 줄의 주석을 풀고 사용하세요.
                    }
                } else {
                    break; // Tmap 에러 발생 시 탈출
                }
            }

            // 7. 최종 응답 내려주기
            if (savedCourse != null) {
                response.put("status", "SUCCESS");
                response.put("message", "코스가 성공적으로 생성되고 DB에 저장되었습니다.");
                response.put("courseId", savedCourse.getId());
                response.put("metrics", Map.of(
                        "targetDistance_km", savedCourse.getTargetDistance(),
                        "actualDistance_km", savedCourse.getActualDistance(),
                        "total_ascent_m", savedCourse.getTotalElevation()
                ));

                ObjectMapper mapper = new ObjectMapper();
                response.put("path", mapper.readValue(savedCourse.getPathCoordinates(), List.class));
            } else {
                response.put("status", "ERROR");
                response.put("message", "Tmap 코스 생성에 실패했습니다.");
            }

        } catch (Exception e) {
            log.error("코스 생성 중 에러 발생", e);
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