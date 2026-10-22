package com.nerdnam.jd.domain;

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
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 공고의 요건 한 줄. 문구·유형은 사용자가 쓴 그대로이고 AI·API 가 절대 바꾸지 않는다 (F4).
 * 추출 결과(기술명 원문·정규화한 기술명·최소 연차)만 붙는다.
 */
@Entity
@Table(name = "job_requirement")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_posting_id", nullable = false, updatable = false)
    private JobPosting posting;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private RequirementType type;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "text", nullable = false, length = 1000)
    private String text;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skills_raw")
    private List<String> skillsRaw;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skills")
    private List<String> skills;

    @Column(name = "min_years")
    private Integer minYears;

    @Column(name = "extracted_at")
    private Instant extractedAt;

    static JobRequirement of(JobPosting posting, RequirementType type, int lineNo, String text) {
        JobRequirement r = new JobRequirement();
        r.posting = posting;
        r.type = type;
        r.lineNo = lineNo;
        r.text = text;
        return r;
    }

    void moveTo(int lineNo) {
        this.lineNo = lineNo;
    }

    void applyExtraction(List<String> skillsRaw, List<String> normalizedSkills, Integer minYears) {
        this.skillsRaw = skillsRaw == null ? List.of() : List.copyOf(skillsRaw);
        this.skills = normalizedSkills == null ? List.of() : List.copyOf(normalizedSkills);
        this.minYears = minYears == null || minYears < 0 ? null : minYears;
        this.extractedAt = Instant.now();
    }

    public List<String> getSkills() {
        return skills == null ? List.of() : skills;
    }
}
