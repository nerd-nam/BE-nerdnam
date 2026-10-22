package com.nerdnam.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 동의 기록 — 항목·약관 버전마다 한 번 (BE-03 "동의 기록"). 약관이 바뀌면 새 버전으로 다시 받는다 */
@Entity
@Table(name = "member_consent")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false, length = 30, updatable = false)
    private ConsentType consentType;

    @Column(name = "terms_version", nullable = false, length = 20, updatable = false)
    private String termsVersion;

    @Column(name = "agreed_at", nullable = false, updatable = false)
    private Instant agreedAt;

    public static MemberConsent agree(Long memberId, ConsentType type, String termsVersion) {
        MemberConsent c = new MemberConsent();
        c.memberId = memberId;
        c.consentType = type;
        c.termsVersion = termsVersion;
        c.agreedAt = Instant.now();
        return c;
    }
}
