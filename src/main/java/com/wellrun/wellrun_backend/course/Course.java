package com.wellrun.wellrun_backend.course;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Getter @Setter
@Table(name = "courses")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Double startLat;
    private Double startLng;
    private Double endLat;
    private Double endLng;

    private Double targetDistance; // 유저가 요청한 목표 거리
    private Double actualDistance; // Tmap이 깎아준 진짜 거리
    private Double totalElevation; // 누적 고도

    // Tmap이 깎아준 수많은 곡선 좌표들을 JSON 문자열로 통째로 저장합니다!
    @Column(columnDefinition = "LONGTEXT")
    private String pathCoordinates;

    private LocalDateTime createdAt = LocalDateTime.now();
}