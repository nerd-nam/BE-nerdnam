package com.nerdnam.rag.application;

import java.time.YearMonth;
import java.util.List;

/** 청크를 만드는 재료 — 원본 테이블에서 읽은 값 (rag 는 원본을 고치지 않고 읽기만 한다) */
public final class ChunkSources {

    private ChunkSources() {
    }

    /** @param usages 사용처 이름 ("OO커머스 경력", "결제 승인 API 성능 개선 프로젝트") */
    public record SkillSource(long userSkillId, String name, String category, int level, List<String> usages) {
    }

    public record CareerSource(long careerId, int version, String companyName, String teamName, String jobTitle,
                               String positionTitle, String employmentType, YearMonth start, YearMonth end,
                               List<String> duties, List<String> skills) {
    }

    public record ProjectSource(long projectId, int version, String title, YearMonth start, YearMonth end,
                                List<String> skills, List<EpisodeSource> confirmedEpisodes) {
    }

    public record EpisodeSource(long episodeId, int versionNo, String title, String role, String problem,
                                String solution, String result, List<String> skills, boolean hasMetric) {
    }
}
