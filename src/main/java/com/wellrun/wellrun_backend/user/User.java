package com.wellrun.wellrun_backend.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter @Setter
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String nickname; // (안드로이드에서는 '이름'으로 사용 중)

    // ✨ 새로 추가되는 기본 정보 및 부상 데이터
    private Double height;
    private Double weight;
    private Integer age;
    private String gender;
    private String goal;
    private String injury;
}