package com.nochultwi.backend.domain.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ResetPasswordRequestDto {
    private String loginId;
    private String email;
    private Long studentNumber;
    private String newPassword;
}
