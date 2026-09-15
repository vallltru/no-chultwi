package com.nochultwi.backend.domain.user.controller;

import com.nochultwi.backend.domain.user.dto.*;
import com.nochultwi.backend.domain.user.entity.User;
import com.nochultwi.backend.domain.user.service.UserService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@AllArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/signup")
    public ResponseEntity<SignUpResponseDto> signUp(@RequestBody SignUpRequestDto request){
        User user = userService.signUp(
                request.getLoginId(),
                request.getPassword(),
                request.getName(),
                request.getEmail(),
                request.getStudentNumber(),
                request.getRole()
        );

        String token = userService.login(request.getLoginId(), request.getPassword());

        return ResponseEntity.ok(new SignUpResponseDto("Bearer", token));

    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@RequestBody LoginRequestDto request){

        String token = userService.login(request.getLoginId(), request.getPassword());

        return ResponseEntity.ok(new LoginResponseDto("Bearer", token));
    }

    @PostMapping("/findid")
    public ResponseEntity<FindIdResponseDto> findId(@RequestBody FindIdRequestDto request){

        String loginId= userService.findId(request.getEmail(), request.getStudentNumber());

        return ResponseEntity.ok(new FindIdResponseDto(loginId));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ResetPasswordResponseDto> resetPassword(@RequestBody ResetPasswordRequestDto request){
        userService.resetPassword(
                request.getLoginId(),
                request.getEmail(),
                request.getStudentNumber(),
                request.getNewPassword()
        );

        return ResponseEntity.ok(new ResetPasswordResponseDto("비밀번호가 성공적으로 변경되었습니다."));
    }

}
