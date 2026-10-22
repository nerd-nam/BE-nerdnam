package com.nerdnam.profile.domain;

import com.nerdnam.common.jpa.BaseTimeEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 경력 — 애그리거트 루트. 주요 업무 줄과 사용 기술 id 를 값 컬렉션으로 함께 관리한다.
 * 사용 기술은 기술스택(UserSkill)의 id 로만 참조한다.
 */
@Entity
@Table(name = "career")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Career extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "company_name", nullable = false, length = 100)
    private String companyName;

    @Column(name = "team_name", nullable = false, length = 100)
    private String teamName;

    @Column(name = "job_title", nullable = false, length = 100)
    private String jobTitle;

    @Column(name = "position_title", length = 50)
    private String positionTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 20)
    private EmploymentType employmentType;

    @Embedded
    private EmploymentPeriod period;

    @ElementCollection
    @CollectionTable(name = "career_duty", joinColumns = @JoinColumn(name = "career_id"))
    @OrderColumn(name = "line_no")
    @Column(name = "content", nullable = false, length = 300)
    private List<String> duties = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "career_skill", joinColumns = @JoinColumn(name = "career_id"))
    @Column(name = "user_skill_id", nullable = false)
    private Set<Long> userSkillIds = new HashSet<>();

    @Version
    private long version;

    public static Career create(Long memberId, CareerContent content) {
        Career career = new Career();
        career.memberId = Objects.requireNonNull(memberId);
        career.apply(content);
        return career;
    }

    public void update(CareerContent content) {
        apply(content);
    }

    public boolean usesSkill(Long userSkillId) {
        return userSkillIds.contains(userSkillId);
    }

    public List<String> getDuties() {
        return Collections.unmodifiableList(duties);
    }

    public Set<Long> getUserSkillIds() {
        return Collections.unmodifiableSet(userSkillIds);
    }

    private void apply(CareerContent content) {
        this.companyName = content.companyName();
        this.teamName = content.teamName();
        this.jobTitle = content.jobTitle();
        this.positionTitle = content.positionTitle();
        this.employmentType = content.employmentType();
        this.period = content.period();
        // 컬렉션은 새 객체로 바꾸지 않고 내용만 바꾼다 (Hibernate 가 변경을 추적하는 컬렉션이라)
        this.duties.clear();
        this.duties.addAll(content.duties());
        this.userSkillIds.clear();
        this.userSkillIds.addAll(content.userSkillIds());
    }
}
