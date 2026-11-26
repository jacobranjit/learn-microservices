package com.mc.oletrex.auth.repository;

import com.mc.oletrex.auth.model.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Limit;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    boolean existsByEmail(String email);

    Optional<UserAccount> findByEmail(String email);

    List<UserAccount> findByIdGreaterThanOrderByIdAsc(long id, Limit limit);
}
