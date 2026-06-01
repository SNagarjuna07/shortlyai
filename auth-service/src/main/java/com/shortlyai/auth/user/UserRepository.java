package com.shortlyai.auth.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

// JpaRepository<Entity, PrimaryKeyType> — gives save, findById, delete, etc. for free
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    // Spring Data generates SELECT * FROM users WHERE email = ? automatically
    // Optional — forces caller to handle "user not found" case explicitly
    Optional<User> findByEmail(String email);

    // EXISTS query — cheaper than fetching full entity just to check existence
    boolean existsByEmail(String email);
}