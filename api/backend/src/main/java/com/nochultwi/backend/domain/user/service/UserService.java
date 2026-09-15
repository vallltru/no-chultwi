package com.nochultwi.backend.domain.user.service;

import com.nochultwi.backend.domain.user.entity.Role;
import com.nochultwi.backend.domain.user.entity.User;
import com.nochultwi.backend.domain.user.repository.UserRepository;
import com.nochultwi.backend.global.security.jwt.JwtTokenProvider;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@AllArgsConstructor

public class UserService {
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public User signUp(String loginId, String password, String name, String email, Long studentNumber, Role role){

        if(userRepository.existsByLoginId(loginId)){
            throw new IllegalArgumentException("중복된 아이디");
        }

        if(userRepository.existsByEmail(email)){
            throw new IllegalArgumentException("중복된 이메일");

        }

        if(userRepository.existsByStudentNumber(studentNumber)){
            throw new IllegalArgumentException("중복된 학번");
        }
        Role userRole = (role != null) ? role : Role.ROLE_STUDENT;
        User newUser = User.builder()
                .loginId(loginId)
                .name(name)
                .role(userRole)
                .password(password)
                .email(email)
                .studentNumber(studentNumber)
                .build();

        return userRepository.save(newUser);
    }

    public String login(String loginId, String password){
       User user = userRepository.findByLoginId(loginId).orElseThrow(
               () -> new IllegalArgumentException("존재하지않는 아이디" ));

       if(!user.getPassword().equals(password)){
           throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
       }
       return jwtTokenProvider.createToken(user.getLoginId(), user.getRole());
    }

    public String findId(String email, Long studentNumber){
        User user = userRepository.findByEmail(email).orElseThrow(
                () -> new IllegalArgumentException("존재하지 않는 사용자입니다.")
        );

        if (!user.getStudentNumber().equals(studentNumber)) {
            throw new IllegalArgumentException("이메일 혹은 학번이 일치하지 않습니다.");
        }

        return user.getLoginId();
    }

    @Transactional
    public void resetPassword(String loginId, String email, Long studentNumber, String newPassword) {
        User user = userRepository.findByLoginId(loginId).orElseThrow(
                () -> new IllegalArgumentException("존재하지 않는 사용자입니다.")
        );

        if (!user.getEmail().equals(email) || !user.getStudentNumber().equals(studentNumber)) {
            throw new IllegalArgumentException("이메일 혹은 학번이 일치하지 않습니다.");
        }

        user.updatePassword(newPassword);
    }

}
