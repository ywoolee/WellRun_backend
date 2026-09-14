package com.wellrun.wellrun_backend.course;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ElevationService {

    private final RestTemplate restTemplate = new RestTemplate();
    private static final int CHUNK_SIZE = 100;

    @Data
    public static class OpenTopoResponse {
        private List<Result> results;
        private String status;
    }

    @Data
    public static class Result {
        private Double elevation;
    }

    // ✨ 1. 삭제되었던 단건 조회 메서드 복구 (컨트롤러 에러 해결)
    public Double getElevation(double lat, double lng) {
        String url = UriComponentsBuilder.fromUriString("https://api.opentopodata.org/v1/srtm30m")
                .queryParam("locations", lat + "," + lng)
                .build()
                .toUriString();

        try {
            OpenTopoResponse response = restTemplate.getForObject(url, OpenTopoResponse.class);
            if (response != null && response.getResults() != null && !response.getResults().isEmpty()) {
                return response.getResults().get(0).getElevation();
            }
        } catch (Exception e) {
            log.error("단건 고도 데이터 파싱 실패", e);
        }
        return 0.0;
    }

    // ✨ 2. 방금 추가했던 벌크(일괄) 주입 메서드 유지
    public void injectElevations(List<RouteNode> nodes) {
        log.info("총 {}개 노드의 고도 데이터 벌크 요청 시작...", nodes.size());

        for (int i = 0; i < nodes.size(); i += CHUNK_SIZE) {
            int end = Math.min(nodes.size(), i + CHUNK_SIZE);
            List<RouteNode> chunk = nodes.subList(i, end);

            String locationsParam = chunk.stream()
                    .map(node -> node.getLat() + "," + node.getLng())
                    .collect(Collectors.joining("|"));

            String url = UriComponentsBuilder.fromUriString("https://api.opentopodata.org/v1/srtm30m")
                    .queryParam("locations", locationsParam)
                    .build()
                    .toUriString();

            try {
                OpenTopoResponse response = restTemplate.getForObject(url, OpenTopoResponse.class);

                if (response != null && response.getResults() != null) {
                    List<Result> results = response.getResults();
                    for (int j = 0; j < results.size(); j++) {
                        Double elevation = results.get(j).getElevation();
                        chunk.get(j).setElevation(elevation != null ? elevation : 0.0);
                    }
                }

                Thread.sleep(1000); // 무료 API Rate Limit 차단 방지용 1초 대기

            } catch (Exception e) {
                log.error("고도 데이터 벌크 처리 중 에러 발생 (인덱스: {} ~ {})", i, end, e);
            }
        }

        log.info("모든 노드의 고도 주입 완료!");
    }
}