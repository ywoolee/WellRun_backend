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
            // 임시로 평문 비밀번호 비교 (실무에서는 추후 BCrypt 등으로 암호화해야 함)
            if (user.getPassword().equals(loginUser.getPassword())) {
                return ResponseEntity.ok("로그인 성공! 환영합니다, " + user.getNickname() + "님");
            } else {
                return ResponseEntity.status(401).body("비밀번호가 틀렸습니다.");
            }
        } else {
            return ResponseEntity.status(404).body("가입되지 않은 아이디입니다.");
        }
    }
}
