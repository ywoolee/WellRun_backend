package com.wellrun.wellrun_backend.running;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RunningRecordRepository extends JpaRepository<RunningRecord, Long> {
    // 특정 유저의 모든 러닝 기록을 최신순으로 가져오는 메서드
    List<RunningRecord> findAllByUserIdOrderByCreatedAtDesc(String userId);
}