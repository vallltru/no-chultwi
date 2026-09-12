package com.nochultwi.backend.domain.user.controller;

import com.nochultwi.backend.domain.user.dto.LoginRequestDto;
import com.nochultwi.backend.domain.user.dto.SignUpRequestDto;
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
    public ResponseEntity<User> signUp(@RequestBody SignUpRequestDto request){
        User user = userService.signUp(
                request.getLoginId(),
                request.getPassword(),
                request.getName(),
                request.getEmail(),
                request.getStudentNumber(),
                request.getRole()
        );
        return ResponseEntity.ok(user);

    }

    @PostMapping("/login")
    public ResponseEntity<User> login(@RequestBody LoginRequestDto request){

        User user = userService.login(request.getLoginId(), request.getPassword());

        return ResponseEntity.ok(user);
    }


}
