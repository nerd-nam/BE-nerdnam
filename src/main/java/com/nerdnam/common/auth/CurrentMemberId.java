package com.nerdnam.common.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 파라미터에 붙이면 지금 요청한 회원의 id(Long)를 넣어 준다.
 *
 * <pre>
 * &#64;GetMapping("/api/me/skills")
 * List&lt;UserSkillResponse&gt; list(&#64;CurrentMemberId Long memberId) { ... }
 * </pre>
 *
 * 회원 id 를 어디서 읽는지는 {@link MemberIdentityProvider} 구현이 정한다.
 * 로그인(BE-03) 전에는 local 프로필의 요청 헤더 구현을 쓰고, 로그인이 생기면 세션 구현으로 바꾼다.
 * 컨트롤러 코드는 바꿀 필요가 없다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentMemberId {
}
