package com.wellrun.wellrun_backend.running;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/running")
@RequiredArgsConstructor
public class RunningRecordController {

    private final RunningRecordRepository repository;

    // 1. 달리기 종료 후 기록 저장 API
    @PostMapping("/record")
    public String saveRunningRecord(@RequestBody RunningRecord record) {
        repository.save(record);
        return "러닝 기록이 성공적으로 저장되었습니다. (거리: " + record.getDistance() + "km)";
    }

    // 2. 마이페이지/리포트에서 내 기록 불러오기 API
    @GetMapping("/history/{userId}")
    public List<RunningRecord> getRunningHistory(@PathVariable String userId) {
        return repository.findAllByUserIdOrderByCreatedAtDesc(userId);
    }
}