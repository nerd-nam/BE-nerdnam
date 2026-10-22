package com.nerdnam.member.domain;

import org.springframework.data.repository.Repository;

public interface MemberConsentRepository extends Repository<MemberConsent, Long> {

    MemberConsent save(MemberConsent consent);

    boolean existsByMemberIdAndConsentTypeAndTermsVersion(Long memberId, ConsentType type, String termsVersion);
}
