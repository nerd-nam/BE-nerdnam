package com.nerdnam.project.domain;

import java.util.List;

/** 사용자가 직접 쓰는 사례 내용 (직접 편집·사례 직접 추가). metrics 가 null 이면 편집 때 기존 값을 그대로 둔다 */
public record EpisodeEdit(String title, String role, String problem, String solution, String result, List<Metric> metrics) {

    EpisodeContent toNewContent() {
        return new EpisodeContent(title, role, problem, solution, result, List.of(), metrics, List.of(), List.of());
    }
}
