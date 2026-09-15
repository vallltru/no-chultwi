package com.nochultwi.backend.domain.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@Getter
@NoArgsConstructor
public class SignUpResponseDto {

    private String tokenType;
    private String accessToken;
}
