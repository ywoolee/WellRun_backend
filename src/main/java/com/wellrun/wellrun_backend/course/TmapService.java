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

    private static final String TMAP_APP_KEY = "ScSWi3m0f15CS0H944Sps9o8nI0UmDeYlbUgt4Z5";
    private static final String TMAP_PEDESTRIAN_URL = "https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1";

    public Course smoothPathAndSave(List<RouteNode> optimalPath, double targetDistance, double totalElevation, boolean isFlat) {
        if (optimalPath == null || optimalPath.size() < 2) return null;

        int totalNodes = optimalPath.size();

        // ✨ 1. 목표 거리에 맞춰 구간 분할 갯수 계산 (약 1km당 1개 구간)
        int numChunks = (int) Math.max(1, Math.ceil(targetDistance / 1.0));

        // 🚨 [방어 로직] 뼈대가 너무 짧은 경우(노드가 30개 미만), 무조건 1개 구간(통짜)으로 처리하여 에러 방지
        if (totalNodes < 30 || numChunks == 1) {
            numChunks = 1;
        }

        int nodesPerChunk = (int) Math.ceil((double) totalNodes / numChunks);

        // 이어붙일 마스터 좌표 리스트 준비
        List<Map<String, Double>> masterCoordinates = new ArrayList<>();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("appKey", TMAP_APP_KEY);

        try {
            // ✨ 2. 쪼개진 구간별로 Tmap API 반복 호출
            for (int i = 0; i < numChunks; i++) {
                int chunkStart = i * nodesPerChunk;
                // 다음 구간과 선이 끊기지 않고 겹치도록 chunkEnd 설정 (마지막 구간은 전체 노드의 끝점)
                int chunkEnd = (i == numChunks - 1) ? totalNodes - 1 : Math.min((i + 1) * nodesPerChunk, totalNodes - 1);

                if (chunkStart >= chunkEnd) break;

                RouteNode startNode = optimalPath.get(chunkStart);
                RouteNode endNode = optimalPath.get(chunkEnd);

                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("startX", String.valueOf(startNode.getLng()));
                requestBody.put("startY", String.valueOf(startNode.getLat()));
                requestBody.put("endX", String.valueOf(endNode.getLng()));
                requestBody.put("endY", String.valueOf(endNode.getLat()));
                requestBody.put("reqCoordType", "WGS84GEO");
                requestBody.put("resCoordType", "WGS84GEO");
                requestBody.put("startName", "구간출발");
                requestBody.put("endName", "구간도착");

                // ✨ 3. 해당 1km 구간 내에서 징검다리 경유지(최대 5개) 촘촘하게 추출
                StringBuilder passList = new StringBuilder();
                int chunkLen = chunkEnd - chunkStart;
                if (chunkLen > 2) {
                    int maxWaypoints = Math.min(5, chunkLen - 1);
                    double wpStep = (double) chunkLen / (maxWaypoints + 1);
                    for (int w = 1; w <= maxWaypoints; w++) {
                        int wpIndex = chunkStart + (int)(w * wpStep);
                        if (wpIndex > chunkStart && wpIndex < chunkEnd) {
                            RouteNode wp = optimalPath.get(wpIndex);
                            if (passList.length() > 0) passList.append("_");
                            passList.append(wp.getLng()).append(",").append(wp.getLat());
                        }
                    }
                    if (passList.length() > 0) {
                        requestBody.put("passList", passList.toString());
                    }
                }

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

                // ✨ 4. API 호출 및 결과 파싱
                log.info("🗺️ Tmap 구간 분할 스무딩 진행 중... ({}/{} 구간)", (i + 1), numChunks);
                String responseJson = restTemplate.postForObject(TMAP_PEDESTRIAN_URL, entity, String.class);
                JsonNode rootNode = objectMapper.readTree(responseJson);

                // 해당 구간의 스무딩된 곡선 좌표를 마스터 리스트에 이어 붙이기
                for (JsonNode feature : rootNode.path("features")) {
                    JsonNode geometry = feature.path("geometry");
                    if (geometry.path("type").asText().equals("LineString")) {
                        for (JsonNode coord : geometry.path("coordinates")) {
                            Map<String, Double> point = new HashMap<>();
                            point.put("lng", coord.get(0).asDouble());
                            point.put("lat", coord.get(1).asDouble());
                            masterCoordinates.add(point);
                        }
                    }
                }

                // SK API Rate Limit(호출 제한)을 피하기 위한 0.1초 휴식 매너
                if (i < numChunks - 1) {
                    Thread.sleep(100);
                }
            }

            // ✨ 5. 스파이크(U턴 맹장) 제거 알고리즘 적용
            List<Map<String, Double>> cleanCoordinates = new ArrayList<>();
            if (!masterCoordinates.isEmpty()) {
                cleanCoordinates.add(masterCoordinates.get(0)); // 첫 번째 점은 무조건 추가

                int currentIndex = 0;
                while (currentIndex < masterCoordinates.size() - 1) {
                    int nextIndex = currentIndex + 1;

                    // 현재 위치에서 앞으로 최대 40개의 점(약 40~60m 거리)을 미리 내다봅니다.
                    for (int j = Math.min(masterCoordinates.size() - 1, currentIndex + 40); j > currentIndex + 2; j--) {
                        double lat1 = masterCoordinates.get(currentIndex).get("lat");
                        double lng1 = masterCoordinates.get(currentIndex).get("lng");
                        double lat2 = masterCoordinates.get(j).get("lat");
                        double lng2 = masterCoordinates.get(j).get("lng");

                        double distKm = LocationUtils.calculateDistance(lat1, lng1, lat2, lng2);

                        // 만약 미래의 위치(j)가 현재 위치와 20m(0.020km) 이내로 가깝다면 U턴 구간 통째로 건너뜀
                        if (distKm < 0.020) {
                            nextIndex = j;
                            break;
                        }
                    }
                    cleanCoordinates.add(masterCoordinates.get(nextIndex));
                    currentIndex = nextIndex;
                }
            }

            // ✨ 6. [핵심] 가위질이 끝난 후, 살아남은 점들을 기준으로 '진짜 최종 거리' 재계산
            double finalActualDistanceKm = 0.0;
            if (cleanCoordinates.size() > 1) {
                for (int i = 1; i < cleanCoordinates.size(); i++) {
                    double lat1 = cleanCoordinates.get(i - 1).get("lat");
                    double lng1 = cleanCoordinates.get(i - 1).get("lng");
                    double lat2 = cleanCoordinates.get(i).get("lat");
                    double lng2 = cleanCoordinates.get(i).get("lng");

                    finalActualDistanceKm += LocationUtils.calculateDistance(lat1, lng1, lat2, lng2);
                }
            }
            finalActualDistanceKm = Math.round(finalActualDistanceKm * 100) / 100.0; // 소수점 둘째 자리 반올림

            // 필터링이 완료된 깨끗한 배열을 JSON으로 변환
            String coordinatesJson = objectMapper.writeValueAsString(cleanCoordinates);

            Course course = new Course();
            // 코스의 진짜 시작점과 끝점
            course.setStartLat(optimalPath.get(0).getLat());
            course.setStartLng(optimalPath.get(0).getLng());
            course.setEndLat(optimalPath.get(totalNodes - 1).getLat());
            course.setEndLng(optimalPath.get(totalNodes - 1).getLng());

            course.setTargetDistance(targetDistance);
            course.setActualDistance(finalActualDistanceKm); // 재계산된 진짜 거리를 삽입
            course.setTotalElevation(totalElevation);
            course.setPathCoordinates(coordinatesJson);
            course.setIsFlat(isFlat);

            log.info("✅ 쪼개진 Tmap 구간 병합 및 맹장 제거 완료! DB 저장 성공! 최종 뛸 거리: {}km", finalActualDistanceKm);
            return courseRepository.save(course);

        } catch (Exception e) {
            log.error("Tmap 구간 분할 API 연동 또는 DB 저장 실패", e);
            return null;
        }
    }

    // 실패한 찌꺼기 코스를 DB에서 날려버리는 메서드
    public void deleteCourse(Course course) {
        if (course != null && course.getId() != null) {
            courseRepository.delete(course);
            log.info("🗑️ 오차 범위를 벗어난 임시 코스(ID: {})를 DB에서 삭제했습니다.", course.getId());
        }
    }
}