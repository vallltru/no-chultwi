package com.nochultwi.backend.domain.user.entity;

import jakarta.persistence.*;
import lombok.*;


@Table(name = "Users")
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true, length = 30)
    private String loginId;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, unique = true, length = 10)
    private Long studentNumber;

    @Enumerated()
    private Role role;

    public void updatePassword(String newPassword) {
        this.password = newPassword;
    }
}

