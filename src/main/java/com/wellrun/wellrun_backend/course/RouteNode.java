package com.wellrun.wellrun_backend.course;

import com.fasterxml.jackson.annotation.JsonIgnore; // ✨ 추가
import lombok.Data;

@Data
public class RouteNode {
    private double lat;
    private double lng;
    private double elevation;

    private double gCost;
    private double hCost;
    private double fCost;

    @JsonIgnore // ✨ 핵심: JSON으로 변환할 때 이 꼬리표는 무시하고 위경도/고도만 보내라!
    private RouteNode parent;

    public RouteNode(double lat, double lng) {
        this.lat = lat;
        this.lng = lng;
        this.elevation = 0.0;
        this.gCost = Double.MAX_VALUE;
    }
}