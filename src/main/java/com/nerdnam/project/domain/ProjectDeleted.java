package com.nerdnam.project.domain;

import java.util.List;

/** 프로젝트 삭제 트랜잭션에서 발행한다. 다른 모듈은 원본 대신 필요한 이름만 남긴다. */
public record ProjectDeleted(Long memberId, Long projectId, List<Long> episodeIds) {
    public ProjectDeleted {
        episodeIds = List.copyOf(episodeIds);
    }
}
