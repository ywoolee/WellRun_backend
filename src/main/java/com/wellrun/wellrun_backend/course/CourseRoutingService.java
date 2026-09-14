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

    // 노드 간의 간격 (0.0005도는 한국 기준 약 45~55m)
    private static final double GRID_SPACING = 0.001;

    // 경계 확장 마진 (출발/도착지가 너무 외곽에 붙지 않도록 사각형을 살짝 넓혀줌)
    private static final double MARGIN = GRID_SPACING * 2;

    /**
     * 출발지와 목적지를 감싸는 사각형 영역에 그리드(노드) 배열을 생성합니다.
     */
    public List<RouteNode> generateGrid(double startLat, double startLng, double endLat, double endLng) {
        List<RouteNode> gridNodes = new ArrayList<>();

        // 1. A와 B를 모두 포함하는 최소/최대 경계선(Bounding Box) 계산
        double minLat = Math.min(startLat, endLat) - MARGIN;
        double maxLat = Math.max(startLat, endLat) + MARGIN;
        double minLng = Math.min(startLng, endLng) - MARGIN;
        double maxLng = Math.max(startLng, endLng) + MARGIN;

        // 2. 경계선 내부를 GRID_SPACING 간격으로 순회하며 노드 생성
        for (double lat = minLat; lat <= maxLat; lat += GRID_SPACING) {
            for (double lng = minLng; lng <= maxLng; lng += GRID_SPACING) {
                gridNodes.add(new RouteNode(lat, lng));
            }
        }

        log.info("출발지({},{}), 목적지({},{}) 기준 총 {}개의 그리드 노드 생성 완료",
                startLat, startLng, endLat, endLng, gridNodes.size());

        return gridNodes;
    }
}
