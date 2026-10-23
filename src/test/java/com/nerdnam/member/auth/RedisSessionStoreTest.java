package com.nerdnam.member.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.nerdnam.TestcontainersConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.redis.RedisSessionRepository;

/**
 * 세션이 서버 메모리가 아니라 Redis 에 저장되는지 (기획서 5.3).
 * 메모리에 두면 앱을 재시작하거나 서버가 2대가 되면 로그인이 풀린다.
 */
@SpringBootTest(properties = "nerdnam.auth.mode=session")
@Import(TestcontainersConfiguration.class)
class RedisSessionStoreTest {

    /** RedisSessionRepository(repository-type: default)의 키 모양 — 운영에서 키를 찾을 때 쓴다 */
    private static final String KEY_PREFIX = "nerdnam:session:sessions:";

    @Autowired
    RedisSessionRepository sessions;

    @Autowired
    ApplicationContext context;

    @Autowired
    StringRedisTemplate redis;

    @Test
    void 세션_저장소는_Redis다() {
        assertThat(context.getBean(SessionRepository.class)).isInstanceOf(RedisSessionRepository.class);
    }

    @Test
    void 저장한_세션은_Redis에서_다시_읽힌다() {
        String id = save(sessions, "회원", 7L);

        // 앱을 재시작해도 남아 있다는 뜻 — 저장소에서 다시 꺼내 읽는다
        Session loaded = sessions.findById(id);

        assertThat(loaded).isNotNull();
        assertThat(loaded.<Long>getAttribute("회원")).isEqualTo(7L);
        assertThat(redis.hasKey(KEY_PREFIX + id))
                .as("키 앞머리는 nerdnam:session (다른 서비스와 섞이지 않게)")
                .isTrue();
        assertThat(redis.getExpire(KEY_PREFIX + id))
                .as("7일 뒤 자동으로 지워진다 (spring.session.timeout)")
                .isGreaterThan(Duration.ofDays(6).toSeconds());
    }

    @Test
    void 지운_세션은_다시_읽히지_않는다() {
        String id = save(sessions, "회원", 7L);

        sessions.deleteById(id);   // 로그아웃·탈퇴 때 일어나는 일

        assertThat(sessions.findById(id)).isNull();
        assertThat(redis.hasKey(KEY_PREFIX + id)).isFalse();
    }

    /** 저장소가 쓰는 세션 타입(S)이 무엇이든 만들고 저장한다 */
    private static <S extends Session> String save(SessionRepository<S> repository, String name, Object value) {
        S session = repository.createSession();
        session.setAttribute(name, value);
        repository.save(session);
        return session.getId();
    }
}
