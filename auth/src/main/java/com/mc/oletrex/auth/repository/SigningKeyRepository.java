package com.mc.oletrex.auth.repository;

import com.mc.oletrex.auth.model.SigningKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SigningKeyRepository extends JpaRepository<SigningKey, String> {

    Optional<SigningKey> findFirstByOrderByCreatedAtDesc();
}
