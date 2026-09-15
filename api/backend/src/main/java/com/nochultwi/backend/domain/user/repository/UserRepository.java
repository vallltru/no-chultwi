package com.nochultwi.backend.domain.user.repository;

import com.nochultwi.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long > {

    Optional<User> findByLoginId(String loginId);

    Optional<User> findByEmail(String email);


    Boolean existsByLoginId(String LoginId);

    Boolean existsByEmail(String email);

    boolean existsByStudentNumber(Long studentNumber);
}


