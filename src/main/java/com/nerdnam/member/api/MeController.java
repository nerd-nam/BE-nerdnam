package com.nerdnam.member.api;

import com.nerdnam.common.auth.CurrentMemberId;
import com.nerdnam.member.application.MemberService;
import com.nerdnam.member.application.MemberService.MemberView;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 내 정보 (머리글), 탈퇴 (계정 메뉴) */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final MemberService members;

    public MeController(MemberService members) {
        this.members = members;
    }

    @GetMapping
    public MemberView me(@CurrentMemberId Long memberId) {
        return members.me(memberId);
    }

    /** S01 이용약관·개인정보 수집 동의 (지금 약관 버전) */
    @org.springframework.web.bind.annotation.PostMapping("/consents")
    public MemberView agree(@CurrentMemberId Long memberId) {
        return members.agreeToTerms(memberId);
    }

    /** 탈퇴 — 원본·벡터 즉시 삭제, 세션도 끝낸다 */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@CurrentMemberId Long memberId, jakarta.servlet.http.HttpServletRequest request) {
        members.withdraw(memberId);
        jakarta.servlet.http.HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
}
