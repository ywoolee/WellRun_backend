package com.wellrun.wellrun_backend.user;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    // ✨ 회원가입 API
    @PostMapping("/signup")
    public ResponseEntity<String> signup(@RequestBody User user) {
        if (userRepository.existsByEmail(user.getEmail())) {
            return ResponseEntity.badRequest().body("이미 존재하는 아이디(이메일)입니다.");
        }
        userRepository.save(user);
        return ResponseEntity.ok("회원가입 성공!");
    }

    // ✨ 로그인 API
    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody User loginUser) {
        Optional<User> userOptional = userRepository.findByEmail(loginUser.getEmail());

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            if (user.getPassword().equals(loginUser.getPassword())) {
                return ResponseEntity.ok("로그인 성공! 환영합니다, " + user.getNickname() + "님");
            } else {
                return ResponseEntity.status(401).body("비밀번호가 틀렸습니다.");
            }
        } else {
            return ResponseEntity.status(404).body("가입되지 않은 아이디입니다.");
        }
    }

    // ✨ 마이페이지용 유저 프로필 정보 조회 API
    @GetMapping("/{email}")
    public ResponseEntity<User> getUserProfile(@PathVariable String email) {
        return userRepository.findByEmail(email)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ✨ [신규 추가] 회원정보 수정 API
    @PutMapping("/{email}")
    public ResponseEntity<User> updateUserProfile(@PathVariable String email, @RequestBody User updatedUser) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        User user = userOptional.get();
        if (updatedUser.getNickname() != null && !updatedUser.getNickname().isBlank()) {
            user.setNickname(updatedUser.getNickname());
        }
        user.setHeight(updatedUser.getHeight());
        user.setWeight(updatedUser.getWeight());
        user.setAge(updatedUser.getAge());
        user.setGender(updatedUser.getGender());
        user.setGoal(updatedUser.getGoal());

        User saved = userRepository.save(user);
        return ResponseEntity.ok(saved);
    }
}