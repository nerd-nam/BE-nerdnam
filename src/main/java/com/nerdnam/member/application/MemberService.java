package com.nerdnam.member.application;

import com.nerdnam.common.error.BusinessException;
import com.nerdnam.common.error.ErrorCode;
import com.nerdnam.member.auth.AuthProperties;
import com.nerdnam.member.auth.SocialProfile;
import com.nerdnam.member.domain.ConsentType;
import com.nerdnam.member.domain.Member;
import com.nerdnam.member.domain.MemberConsent;
import com.nerdnam.member.domain.MemberConsentRepository;
import com.nerdnam.member.domain.MemberRepository;
import com.nerdnam.member.domain.SocialAccount;
import com.nerdnam.member.domain.SocialAccountRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 로그인(소셜)·동의 기록·내 정보·탈퇴 (공통, BE-03) */
@Service
@Transactional
public class MemberService {

    private final MemberRepository members;
    private final SocialAccountRepository socialAccounts;
    private final MemberConsentRepository consents;
    private final AuthProperties auth;
    private final ApplicationEventPublisher events;

    public MemberService(MemberRepository members, SocialAccountRepository socialAccounts,
                         MemberConsentRepository consents, AuthProperties auth, ApplicationEventPublisher events) {
        this.members = members;
        this.socialAccounts = socialAccounts;
        this.consents = consents;
        this.auth = auth;
        this.events = events;
    }

    /** @param consentRequired 지금 약관 버전에 아직 동의하지 않음 → 웹이 동의 화면을 띄운다 */
    public record MemberView(Long id, String displayName, String email, boolean consentRequired, String termsVersion) {
    }

    @Transactional(readOnly = true)
    public MemberView me(Long memberId) {
        Member m = get(memberId);
        return new MemberView(m.getId(), m.getDisplayName(), m.getEmail(), !agreedToCurrentTerms(memberId),
                auth.termsVersion());
    }

    /**
     * 소셜 로그인 — 처음이면 회원을 만들고 소셜 계정을 연결한다. 돌려주는 값은 회원 id.
     *
     * <p>다른 소셜 계정으로 이미 쓰인 이메일이면 자동으로 합치지 않고 이메일 없이 새 회원을 만든다.
     * 제공자가 이메일 소유를 확인하지 않았을 수 있어, 이메일만 보고 합치면 남의 계정에 들어갈 수 있기 때문이다.
     */
    public Long loginWithSocial(SocialProfile profile) {
        Long memberId = socialAccounts.findByProviderAndProviderUserId(profile.provider(), profile.providerUserId())
                .map(SocialAccount::getMemberId)
                .orElseGet(() -> {
                    String email = profile.email() != null && members.existsByEmailIgnoreCase(profile.email())
                            ? null : profile.email();
                    Member joined = members.save(Member.join(email, profile.name()));
                    socialAccounts.save(SocialAccount.link(joined.getId(), profile.provider(), profile.providerUserId()));
                    return joined.getId();
                });
        get(memberId).loggedIn();
        return memberId;
    }

    /** S01 동의 — 지금 약관 버전으로 이용약관·개인정보 수집 동의를 기록한다 (이미 있으면 그대로) */
    public MemberView agreeToTerms(Long memberId) {
        get(memberId);
        for (ConsentType type : ConsentType.values()) {
            if (!consents.existsByMemberIdAndConsentTypeAndTermsVersion(memberId, type, auth.termsVersion())) {
                consents.save(MemberConsent.agree(memberId, type, auth.termsVersion()));
            }
        }
        return me(memberId);
    }

    private boolean agreedToCurrentTerms(Long memberId) {
        for (ConsentType type : ConsentType.values()) {
            if (!consents.existsByMemberIdAndConsentTypeAndTermsVersion(memberId, type, auth.termsVersion())) {
                return false;
            }
        }
        return true;
    }

    /** 탈퇴 — 원본·벡터를 즉시 삭제한다 (DB CASCADE, 기획서 8장) */
    public void withdraw(Long memberId) {
        members.delete(get(memberId));
        events.publishEvent(new MemberWithdrawn(memberId));
    }

    private Member get(Long memberId) {
        return members.findById(memberId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }
}
