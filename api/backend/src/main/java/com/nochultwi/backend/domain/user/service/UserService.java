package com.nochultwi.backend.domain.user.service;

import com.nochultwi.backend.domain.user.entity.Role;
import com.nochultwi.backend.domain.user.entity.User;
import com.nochultwi.backend.domain.user.repository.UserRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@AllArgsConstructor

public class UserService {
    private final UserRepository userRepository;

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
        User newUser = User.builder().
                loginId(loginId)
                .name(name)
                .role(userRole)
                .password(password)
                .email(email)
                .studentNumber(studentNumber).build();

        return userRepository.save(newUser);

    }

    public User login(String loginId, String password){
       User user = userRepository.findByLoginId(loginId).orElseThrow(
               () -> new IllegalArgumentException("존재하지않는 아이디" ));

       if(!user.getPassword().equals(password)){
           throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
       }
       return user;
    }

}
