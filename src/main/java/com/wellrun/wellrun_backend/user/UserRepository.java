package com.wellrun.wellrun_backend.user;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email); // 이메일로 유저 찾기
    boolean existsByEmail(String email); // 이메일 중복 확인
}