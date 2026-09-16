package com.nochultwi.backend.domain.user.dto;

import com.nochultwi.backend.domain.user.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public class SignUpRequestDto {

    private String loginId;
    private String name;
    private String email;
    private String password;
    private Long studentNumber;
    private Role role;

}
