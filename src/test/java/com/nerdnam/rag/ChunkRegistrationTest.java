package com.nerdnam.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nerdnam.infra.ai.Embeddings;
import com.nerdnam.infra.ai.StubAiServerClient;
import com.nerdnam.support.ApiTestSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * RAG 등록 (BE-10, F3): 기술·경력은 저장 즉시, 프로젝트는 확정할 때 청크가 생기고 임베딩된다.
 * 테스트 설정은 AI 작업 스레드가 바로 실행되므로 요청이 끝나면 임베딩도 끝나 있다(스텁 임베딩).
 */
class ChunkRegistrationTest extends ApiTestSupport {

    private static final String RAW_TEXT = """
            결제 승인 응답이 1.2초까지 느려졌다. Redis 캐시를 붙였다. 평균 응답이 0.3초가 됐다.

            배포가 자주 실패했다. 배포 절차를 고쳤다. 실패가 줄었다.""";

    @Test
    void 기술과_경력은_저장_즉시_청크가_되고_사용처가_기술_청크에_들어간다() throws Exception {
        Long member = newMember();
        Long redis = addSkill(member, "Redis");
        addCareer(member, "OO커머스", redis);

        assertThat(chunks(member)).extracting(Chunk::type).containsExactlyInAnyOrder("SKILL", "CAREER");
        assertThat(chunks(member)).allSatisfy(c -> {
            assertThat(c.status()).isEqualTo("READY");
            assertThat(c.model()).isEqualTo(StubAiServerClient.MODEL);
        });
        assertThat(chunk(member, "SKILL").content()).contains("기술: Redis", "사용처: OO커머스 경력");
        assertThat(chunk(member, "CAREER").content()).contains("경력: OO커머스", "사용 기술: Redis");
    }

    @Test
    void 프로젝트를_확정하면_확정한_사례와_요약만_청크가_된다() throws Exception {
        Long member = newMember();
        Long project = confirmProjectWithFirstEpisode(member);

        List<Chunk> projectChunks = chunks(member).stream().filter(c -> project.equals(c.projectId())).toList();
        assertThat(projectChunks).extracting(Chunk::type).containsExactlyInAnyOrder("PROJECT_SUMMARY", "EPISODE");
        assertThat(projectChunks).allSatisfy(c -> assertThat(c.status()).isEqualTo("READY"));
        assertThat(chunk(member, "EPISODE").content()).contains("프로젝트: 결제 승인 API 성능 개선", "문제: 결제 승인 응답이");
        assertThat(chunk(member, "EPISODE").content()).doesNotContain("배포").as("제외한 사례는 등록하지 않는다");
        assertThat(chunk(member, "SKILL").content()).contains("결제 승인 API 성능 개선 프로젝트");
        Integer dims = jdbc.sql("SELECT vector_dims(embedding) FROM rag.chunk WHERE member_id = :m AND chunk_type = 'EPISODE'")
                .param("m", member).query(Integer.class).single();
        assertThat(dims).isEqualTo(Embeddings.DIMENSIONS);   // DB 열과 같은 차원 (ADR-0007)
    }

    @Test
    void 바뀐_청크만_다시_만들고_그대로인_청크는_다시_임베딩하지_않는다() throws Exception {
        Long member = newMember();
        Long redis = addSkill(member, "Redis");
        Long kafka = addSkill(member, "Kafka");
        long kafkaChunkBefore = chunkIdOf(member, "SKILL", kafka);
        long redisChunkBefore = chunkIdOf(member, "SKILL", redis);

        mvc.perform(patch("/api/me/skills/{id}", redis).header(MEMBER_HEADER, member)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"level\": 4}"))
                .andExpect(status().isOk());

        assertThat(chunkIdOf(member, "SKILL", kafka)).isEqualTo(kafkaChunkBefore);
        assertThat(chunkIdOf(member, "SKILL", redis)).isNotEqualTo(redisChunkBefore);
        assertThat(chunk(member, "SKILL", redis).content()).contains("숙련도: 전문가");
    }

    @Test
    void 경력을_지우면_경력_청크도_지워진다() throws Exception {
        Long member = newMember();
        Long careerId = addCareer(member, "OO커머스", addSkill(member, "Redis"));

        mvc.perform(delete("/api/me/careers/{id}", careerId).header(MEMBER_HEADER, member))
                .andExpect(status().isNoContent());

        assertThat(chunks(member)).extracting(Chunk::type).containsExactly("SKILL");
        assertThat(chunk(member, "SKILL").content()).doesNotContain("사용처");
    }

    @Test
    void 재등록_중에는_이전_청크로_검색하고_다시_확정하면_통째로_바뀐다() throws Exception {
        Long member = newMember();
        Long project = confirmProjectWithFirstEpisode(member);
        List<Long> before = projectChunkIds(member, project);

        postJson(member, null, "/api/projects/{id}/reopen", project).andExpect(status().isOk());
        postJson(member, null, "/api/projects/{id}/split", project).andExpect(status().isAccepted());
        assertThat(projectChunkIds(member, project)).as("다시 확정할 때까지 그대로 (Q4)").isEqualTo(before);

        ResultActions review = getJson(member, "/api/projects/{id}/review", project);
        postJson(member, null, "/api/projects/{id}/episodes/{e}/confirm", project, readLong(review, "$.episodes[0].id"));
        Long second = readLong(review, "$.episodes[1].id");
        postJson(member, null, "/api/projects/{id}/episodes/{e}/clarifications/{k}/drop", project, second, "result-metric");
        postJson(member, null, "/api/projects/{id}/episodes/{e}/confirm", project, second);
        postJson(member, null, "/api/projects/{id}/confirm", project).andExpect(status().isOk());

        List<Chunk> after = chunks(member).stream().filter(c -> project.equals(c.projectId())).toList();
        assertThat(after).extracting(Chunk::type).containsExactlyInAnyOrder("PROJECT_SUMMARY", "EPISODE", "EPISODE");
        assertThat(after).extracting(Chunk::id).doesNotContainAnyElementsOf(before);
    }

    @Test
    void 청크는_회원별로_격리된다() throws Exception {
        Long owner = newMember();
        Long other = newMember();
        addSkill(owner, "Redis");

        assertThat(chunks(other)).isEmpty();
        assertThat(chunks(owner)).hasSize(1);
    }

    // ------------------------------------------------------------------ 준비

    record Chunk(long id, String type, long sourceId, Long projectId, String content, String status, String model) {
    }

    private List<Chunk> chunks(Long member) {
        return jdbc.sql("""
                        SELECT id, chunk_type, source_id, project_id, content, embedding_status, embedding_model
                        FROM rag.chunk WHERE member_id = :m ORDER BY id
                        """)
                .param("m", member)
                .query((rs, n) -> new Chunk(rs.getLong(1), rs.getString(2), rs.getLong(3),
                        (Long) rs.getObject(4), rs.getString(5), rs.getString(6), rs.getString(7)))
                .list();
    }

    private Chunk chunk(Long member, String type) {
        return chunks(member).stream().filter(c -> c.type().equals(type)).findFirst().orElseThrow();
    }

    private Chunk chunk(Long member, String type, Long sourceId) {
        return chunks(member).stream().filter(c -> c.type().equals(type) && c.sourceId() == sourceId)
                .findFirst().orElseThrow();
    }

    private long chunkIdOf(Long member, String type, Long sourceId) {
        return chunk(member, type, sourceId).id();
    }

    private List<Long> projectChunkIds(Long member, Long project) {
        return chunks(member).stream().filter(c -> project.equals(c.projectId())).map(Chunk::id).toList();
    }

    private Long addCareer(Long member, String company, Long userSkillId) throws Exception {
        return readLong(postJson(member, """
                {"companyName": "%s", "teamName": "결제플랫폼팀", "jobTitle": "백엔드 개발자", "employmentType": "FULL_TIME",
                 "startMonth": "2023-03", "duties": ["결제 API 설계·운영"], "userSkillIds": [%d]}
                """.formatted(company, userSkillId), "/api/me/careers")
                .andExpect(status().isCreated()), "$.id");
    }

    /** 사례 2개 중 첫 번째만 확정, 두 번째(확인 질문 있음)는 제외 */
    private Long confirmProjectWithFirstEpisode(Long member) throws Exception {
        Long redis = addSkill(member, "Redis");
        Long project = readLong(postJson(member, """
                {"title": "결제 승인 API 성능 개선", "periodStart": "2024-05", "periodEnd": "2024-09",
                 "rawText": "%s", "userSkillIds": [%d]}
                """.formatted(RAW_TEXT.replace("\n", "\\n"), redis), "/api/projects"), "$.id");
        postJson(member, null, "/api/projects/{id}/split", project).andExpect(status().isAccepted());
        ResultActions review = getJson(member, "/api/projects/{id}/review", project);
        postJson(member, null, "/api/projects/{id}/episodes/{e}/confirm", project, readLong(review, "$.episodes[0].id"))
                .andExpect(status().isOk());
        postJson(member, null, "/api/projects/{id}/episodes/{e}/exclude", project, readLong(review, "$.episodes[1].id"))
                .andExpect(status().isOk());
        postJson(member, null, "/api/projects/{id}/confirm", project).andExpect(status().isOk());
        return project;
    }
}
