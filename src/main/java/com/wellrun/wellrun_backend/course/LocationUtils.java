package com.wellrun.wellrun_backend.course;

public class LocationUtils {

    // 지구의 평균 반지름 (단위: km)
    private static final int EARTH_RADIUS = 6371;

    /**
     * 두 위도/경도 좌표 간의 직선 거리를 계산합니다. (Haversine 공식 적용)
     * @param startLat 출발지 위도
     * @param startLng 출발지 경도
     * @param endLat 도착지 위도
     * @param endLng 도착지 경도
     * @return 두 지점 사이의 직선 거리 (단위: km)
     */
    public static double calculateDistance(double startLat, double startLng, double endLat, double endLng) {

        // 위도와 경도의 차이를 라디안(Radian)으로 변환
        double dLat = Math.toRadians(endLat - startLat);
        double dLng = Math.toRadians(endLng - startLng);

        // 하버사인 공식 계산
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(startLat)) * Math.cos(Math.toRadians(endLat)) *
                        Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        // 최종 거리 계산 (km)
        return EARTH_RADIUS * c;
    }
}