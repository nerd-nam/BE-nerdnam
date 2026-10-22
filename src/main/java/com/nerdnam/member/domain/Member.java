package com.nerdnam.member.domain;

import com.nerdnam.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 회원 — 모든 데이터의 주인. 탈퇴 = 이 행 삭제 → 원본·벡터가 DB CASCADE 로 함께 지워진다 (기획서 8장) */
@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    public static Member join(String email, String displayName) {
        Member m = new Member();
        m.email = email == null || email.isBlank() ? null : email.strip();
        m.displayName = displayName == null || displayName.isBlank() ? "회원" : cut(displayName.strip());
        return m;
    }

    public void loggedIn() {
        this.lastLoginAt = Instant.now();
    }

    private static String cut(String value) {
        return value.length() <= 50 ? value : value.substring(0, 50);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Member other && id != null && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
