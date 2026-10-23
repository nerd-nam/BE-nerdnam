package com.nerdnam.project.domain;

/** 사례 버전이 만들어진 이유 (03 문서 3.3). KPI(2회 이내 확정 비율)는 AI_* 버전 수로 계산한다 */
public enum ChangeType {
    AI_SPLIT,       // 나누기·다시 나누기
    AI_REVISION,    // 수정 요청·확인 질문 답변 반영
    USER_EDIT,      // 직접 편집·"빼고 진행"
    USER_CREATED,   // 사례 직접 추가
    RESTORED;       // 이 버전으로 바꾸기 (고른 버전을 복사)

    public boolean isAi() {
        return this == AI_SPLIT || this == AI_REVISION;
    }
}
