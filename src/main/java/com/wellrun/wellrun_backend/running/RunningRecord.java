package com.wellrun.wellrun_backend.running;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Getter @Setter
@Table(name = "running_records")
public class RunningRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;

    private Double distance;
    private Integer durationSeconds;
    private String averagePace;
    private Integer averageHeartRate;

    // ✨ 앱에서 받을 누적 고도와 케이던스 컬럼을 추가합니다!
    private Double totalElevation;     // 누적 획득 고도 (m)
    private Integer averageCadence;    // 평균 케이던스 (spm)

    @Column(columnDefinition = "JSON")
    private String splitsJson;

    @Column(columnDefinition = "JSON", length = 10000)
    private String routeJson;

    private LocalDateTime createdAt = LocalDateTime.now();
}