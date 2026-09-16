package com.wellrun.wellrun_backend.course;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TmapService {

    private final CourseRepository courseRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper(); // JSON 파싱용

    // TODO: 발급받으신 Tmap App Key를 여기에 넣어주세요!
    private static final String TMAP_APP_KEY = "ScSWi3m0f15CS0H944Sps9o8nI0UmDeYlbUgt4Z5";
    private static final String TMAP_PEDESTRIAN_URL = "https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1";

    public Course smoothPathAndSave(List<RouteNode> optimalPath, double targetDistance, double totalElevation) {
        if (optimalPath == null || optimalPath.size() < 2) return null;

        RouteNode startNode = optimalPath.get(0);
        RouteNode endNode = optimalPath.get(optimalPath.size() - 1);

        // 1. Tmap API 요청 바디 만들기
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("startX", String.valueOf(startNode.getLng()));
        requestBody.put("startY", String.valueOf(startNode.getLat()));
        requestBody.put("endX", String.valueOf(endNode.getLng()));
        requestBody.put("endY", String.valueOf(endNode.getLat()));
        requestBody.put("reqCoordType", "WGS84GEO");
        requestBody.put("resCoordType", "WGS84GEO");
        requestBody.put("startName", "출발지");
        requestBody.put("endName", "목적지");

        // 2. 경유지(passList) 추출: 최대 5개까지만 가능하므로 일정한 간격으로 샘플링!
        StringBuilder passList = new StringBuilder();
        if (optimalPath.size() > 2) {
            int maxWaypoints = Math.min(5, optimalPath.size() - 2);
            int step = optimalPath.size() / (maxWaypoints + 1);

            for (int i = 1; i <= maxWaypoints; i++) {
                int index = i * step;
                if (index >= optimalPath.size() - 1) break;

                RouteNode node = optimalPath.get(index);
                if (passList.length() > 0) passList.append("_");
                passList.append(node.getLng()).append(",").append(node.getLat());
            }
            requestBody.put("passList", passList.toString());
        }

        // 3. HTTP 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("appKey", TMAP_APP_KEY);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            // 4. Tmap API 호출
            log.info("🗺️ Tmap 보행자 API 호출 중...");
            String responseJson = restTemplate.postForObject(TMAP_PEDESTRIAN_URL, entity, String.class);
            JsonNode rootNode = objectMapper.readTree(responseJson);

            // 5. 실제 거리 추출 및 JSON 깎기 작업
            double totalDistanceMeters = rootNode.path("features").get(0).path("properties").path("totalDistance").asDouble();
            double actualDistanceKm = Math.round((totalDistanceMeters / 1000.0) * 100) / 100.0;

            List<Map<String, Double>> smoothCoordinates = new ArrayList<>();

            for (JsonNode feature : rootNode.path("features")) {
                JsonNode geometry = feature.path("geometry");
                if (geometry.path("type").asText().equals("LineString")) {
                    for (JsonNode coord : geometry.path("coordinates")) {
                        Map<String, Double> point = new HashMap<>();
                        point.put("lng", coord.get(0).asDouble()); // Tmap은 [경도, 위도] 순서로 줍니다
                        point.put("lat", coord.get(1).asDouble());
                        smoothCoordinates.add(point);
                    }
                }
            }

            // 배열을 DB에 저장하기 위해 문자열로 변환
            String coordinatesJson = objectMapper.writeValueAsString(smoothCoordinates);

            // 6. DB에 엔티티 저장
            // 6. DB에 엔티티 저장 (Setter 사용)
            Course course = new Course();
            course.setStartLat(startNode.getLat());
            course.setStartLng(startNode.getLng());
            course.setEndLat(endNode.getLat());
            course.setEndLng(endNode.getLng());
            course.setTargetDistance(targetDistance);
            course.setActualDistance(actualDistanceKm);
            course.setTotalElevation(totalElevation);
            course.setPathCoordinates(coordinatesJson);

            log.info("✅ Tmap 스무딩 완료 및 DB 저장 성공! 실제 뛸 거리: {}km", actualDistanceKm);
            return courseRepository.save(course);

        } catch (Exception e) {
            log.error("Tmap API 연동 또는 DB 저장 실패", e);
            return null;
        }
    }
}
