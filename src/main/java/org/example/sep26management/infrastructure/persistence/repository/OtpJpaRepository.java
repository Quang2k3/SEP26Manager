package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.domain.enums.OtpType;
import org.example.sep26management.infrastructure.persistence.entity.OtpEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OtpJpaRepository extends JpaRepository<OtpEntity, Long> {

    @Query("SELECT o FROM OtpEntity o WHERE " +
            "o.email = :email AND " +
            "o.otpType = :otpType AND " +
            "o.isUsed = false AND " +
            "o.expiresAt > :now " +
            "ORDER BY o.createdAt DESC")
    Optional<OtpEntity> findValidOtp(
            @Param("email") String email,
            @Param("otpType") OtpType otpType,
            @Param("now") LocalDateTime now
    );

    List<OtpEntity> findByEmailAndOtpTypeOrderByCreatedAtDesc(String email, OtpType otpType);

    @Query("SELECT COUNT(o) FROM OtpEntity o WHERE " +
            "o.email = :email AND " +
            "o.otpType = :otpType AND " +
            "o.createdAt > :since")
    long countRecentOtps(
            @Param("email") String email,
            @Param("otpType") OtpType otpType,
            @Param("since") LocalDateTime since
    );
}