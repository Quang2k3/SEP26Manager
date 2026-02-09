package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.domain.enums.UserStatus;
import org.example.sep26management.infrastructure.persistence.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {

        Optional<UserEntity> findByEmail(String email);

        boolean existsByEmail(String email);

        @Query("SELECT DISTINCT u FROM UserEntity u " +
                        "LEFT JOIN FETCH u.roles r " +
                        "LEFT JOIN FETCH r.permissions " +
                        "WHERE u.email = :email")
        Optional<UserEntity> findByEmailWithRolesAndPermissions(@Param("email") String email);

        @Query("SELECT DISTINCT u FROM UserEntity u " +
                        "LEFT JOIN FETCH u.roles r " +
                        "LEFT JOIN FETCH r.permissions " +
                        "WHERE u.userId = :userId")
        Optional<UserEntity> findByIdWithRolesAndPermissions(@Param("userId") Long userId);

        @Query("SELECT u FROM UserEntity u WHERE " +
                        "(:keyword IS NULL OR :keyword = '' OR " +
                        "LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                        "LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
                        "(:status IS NULL OR u.status = :status)")
        Page<UserEntity> searchUsers(
                        @Param("keyword") String keyword,
                        @Param("status") UserStatus status,
                        Pageable pageable);

        long countByStatus(UserStatus status);
}