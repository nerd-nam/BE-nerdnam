package com.nerdnam.project.domain;

import com.nerdnam.common.error.BusinessException;
import com.nerdnam.common.error.ErrorCode;
import com.nerdnam.common.jpa.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사례(에피소드) — Project 애그리거트 안의 엔티티. 상태를 바꾸는 메서드는 package-private 이라
 * 같은 패키지의 {@link Project}만 부를 수 있다. 밖에서는 Project 의 메서드로만 바꾼다.
 */
@Entity
@Table(name = "episode")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Episode extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    @Column(name = "split_round", nullable = false, updatable = false)
    private int splitRound;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 20, updatable = false)
    private EpisodeOrigin origin;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EpisodeStatus status;

    /**
     * 지금 쓰는 버전. <b>가장 큰 번호가 아닐 수 있다</b> — "이 버전에서 이어서 하기"(BE #29)로
     * 이전 버전을 다시 쓰면 번호가 내려간다. 화면·RAG·판정은 모두 이 버전의 내용을 쓴다.
     */
    @Column(name = "current_version", nullable = false)
    private int currentVersion;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @OneToMany(mappedBy = "episode", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("versionNo")
    private List<EpisodeVersion> versions = new ArrayList<>();

    static Episode create(Project project, int splitRound, int displayOrder, EpisodeOrigin origin,
                          EpisodeContent content, ChangeType changeType, String feedback, AiTrace trace) {
        Episode e = new Episode();
        e.project = project;
        e.splitRound = splitRound;
        e.displayOrder = displayOrder;
        e.origin = origin;
        e.status = EpisodeStatus.REVIEW;
        e.currentVersion = 0;
        e.addVersion(content, changeType, null, feedback, trace);
        return e;
    }

    public EpisodeVersion current() {
        return version(currentVersion);
    }

    public EpisodeVersion version(int versionNo) {
        return versions.stream()
                .filter(v -> v.getVersionNo() == versionNo)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "사례 버전을 찾을 수 없어요."));
    }

    public List<EpisodeVersion> getVersions() {
        return Collections.unmodifiableList(versions);
    }

    public boolean isConfirmed() {
        return status == EpisodeStatus.CONFIRMED;
    }

    /**
     * P5: 버전 번호는 1부터 1씩 증가, 만든 버전은 바꾸지 않는다.
     *
     * <p>번호는 <b>가장 큰 번호 + 1</b>이다. {@code currentVersion + 1}로 세면, 이전 버전을 쓰다가 고칠 때
     * 이미 있는 번호와 겹쳐 {@code uk_episode_version}을 위반한다 (BE #29).
     */
    void addVersion(EpisodeContent content, ChangeType changeType, Integer restoredFrom, String feedback, AiTrace trace) {
        int next = maxVersionNo() + 1;
        versions.add(EpisodeVersion.of(this, next, content, changeType, restoredFrom, feedback, trace));
        currentVersion = next;
    }

    /** "이 버전에서 이어서 하기" (P6) — 새 버전을 만들지 않고 지금 쓰는 버전만 바꾼다 */
    void useVersion(int versionNo) {
        version(versionNo);   // 없는 번호면 404
        currentVersion = versionNo;
    }

    private int maxVersionNo() {
        return versions.stream().mapToInt(EpisodeVersion::getVersionNo).max().orElse(0);
    }

    void confirm() {
        status = EpisodeStatus.CONFIRMED;
        decidedAt = Instant.now();
    }

    void exclude() {
        status = EpisodeStatus.EXCLUDED;
        decidedAt = Instant.now();
    }

    void backToReview() {
        status = EpisodeStatus.REVIEW;
        decidedAt = null;
    }

    void supersede() {
        status = EpisodeStatus.SUPERSEDED;
    }

    void discard() {
        status = EpisodeStatus.DISCARDED;
    }
}
