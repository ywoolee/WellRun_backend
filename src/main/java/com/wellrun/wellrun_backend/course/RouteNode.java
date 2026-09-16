package com.wellrun.wellrun_backend.course;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode; // ✨ 추가
import lombok.ToString; // ✨ 추가

@Data
// ✨ 핵심 1: 노드를 비교할 때 위도와 경도만 사용하도록 강제하여 StackOverflow(무한 재귀) 방지!
@EqualsAndHashCode(of = {"lat", "lng"})
// ✨ 핵심 2: 로그를 찍을 때도 부모 노드는 무시하도록 처리
@ToString(exclude = "parent")
public class RouteNode {
    private double lat;
    private double lng;
    private double elevation;

    private double gCost;
    private double hCost;
    private double fCost;

    private double penaltyCost = 0.0;

    @JsonIgnore
    private RouteNode parent;

    public RouteNode(double lat, double lng) {
        this.lat = lat;
        this.lng = lng;
        this.elevation = 0.0;
        this.gCost = Double.MAX_VALUE;
    }

    public void addPenaltyCost(double penalty) {
        this.penaltyCost += penalty;
    }

    public void resetPenaltyCost() {
        this.penaltyCost = 0.0;
    }
}