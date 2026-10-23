package com.nerdnam.jd.domain;

import com.nerdnam.common.error.BusinessException;
import com.nerdnam.common.error.ErrorCode;
import com.nerdnam.common.jpa.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 채용공고 — 애그리거트 루트 (사용자가 양식에 옮겨 적은 공고, F4).
 * 임시 저장은 규칙을 검사하지 않고, "적합도 분석하기"를 누를 때 {@link #validateForAnalysis()}가 한꺼번에 검사한다.
 */
@Entity
@Table(name = "job_posting")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobPosting extends BaseTimeEntity {

    public static final int LINE_MAX = 150;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(name = "position_name", nullable = false, length = 200)
    private String positionName;

    @Column(name = "posting_url", length = 1000)
    private String postingUrl;

    @Column(name = "company_intro", nullable = false)
    private String companyIntro;

    @Version
    private long version;

    @OneToMany(mappedBy = "posting", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("type, lineNo")
    private List<JobRequirement> requirements = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "job_posting_task", joinColumns = @JoinColumn(name = "job_posting_id"))
    @OrderColumn(name = "line_no")
    @Column(name = "content", nullable = false, length = 1000)
    private List<String> tasks = new ArrayList<>();

    public static JobPosting createDraft(Long memberId, PostingContent content) {
        JobPosting posting = new JobPosting();
        posting.memberId = Objects.requireNonNull(memberId);
        posting.apply(content);
        return posting;
    }

    /** 임시 저장. 문구가 같은 요건 줄은 그대로 두어(추출 결과 유지) 순서만 맞추고, 바뀐 줄은 새로 만든다 */
    public void update(PostingContent content) {
        apply(content);
    }

    /** "적합도 분석하기" 전 검사 — 위반을 모두 모아 한꺼번에 돌려준다 (S07 메시지 표) */
    public List<PostingViolation> violations() {
        List<PostingViolation> v = new ArrayList<>();
        if (companyName.isBlank()) {
            v.add(PostingViolation.of(PostingViolation.Code.COMPANY_NAME_REQUIRED, "companyName", null));
        }
        if (positionName.isBlank()) {
            v.add(PostingViolation.of(PostingViolation.Code.POSITION_REQUIRED, "positionName", null));
        }
        List<String> required = lines(RequirementType.REQUIRED);
        if (required.isEmpty()) {
            v.add(PostingViolation.of(PostingViolation.Code.REQUIRED_REQUIREMENT_MISSING, "required", null));
        }
        checkLines(v, "required", required);
        checkLines(v, "preferred", lines(RequirementType.PREFERRED));
        checkLines(v, "tasks", tasks);
        return v;
    }

    /** 위반이 있으면 400 + violations 목록. 메시지는 화면 위 요약 문구 */
    public void validateForAnalysis() {
        List<PostingViolation> violations = violations();
        if (!violations.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "고쳐야 할 항목이 " + violations.size() + "개 있어요. 빨간색으로 표시된 곳을 확인해 주세요.",
                    Map.of("violations", violations));
        }
    }

    /**
     * AI 가 뽑은 기술명 원문·최소 연차와 API 가 정규화한 기술명을 요건에 붙인다. 문구·유형은 바꾸지 않는다 (F4).
     */
    public void applyExtraction(Long requirementId, List<String> skillsRaw, List<String> normalizedSkills, Integer minYears) {
        requirements.stream()
                .filter(r -> Objects.equals(r.getId(), requirementId))
                .findFirst()
                .ifPresent(r -> r.applyExtraction(skillsRaw, normalizedSkills, minYears));
    }

    public List<String> lines(RequirementType type) {
        return requirementsOf(type).stream().map(JobRequirement::getText).toList();
    }

    public List<JobRequirement> requirementsOf(RequirementType type) {
        return requirements.stream().filter(r -> r.getType() == type)
                .sorted(java.util.Comparator.comparingInt(JobRequirement::getLineNo)).toList();
    }

    /** 분석 순서: 필수 → 우대, 각각 줄 순서 */
    public List<JobRequirement> orderedRequirements() {
        List<JobRequirement> ordered = new ArrayList<>(requirementsOf(RequirementType.REQUIRED));
        ordered.addAll(requirementsOf(RequirementType.PREFERRED));
        return ordered;
    }

    public List<JobRequirement> getRequirements() {
        return Collections.unmodifiableList(requirements);
    }

    public List<String> getTasks() {
        return Collections.unmodifiableList(tasks);
    }

    private void apply(PostingContent c) {
        this.companyName = c.companyName();
        this.positionName = c.positionName();
        this.postingUrl = c.postingUrl();
        this.companyIntro = c.companyIntro();
        replaceLines(RequirementType.REQUIRED, c.required());
        replaceLines(RequirementType.PREFERRED, c.preferred());
        this.tasks.clear();
        this.tasks.addAll(c.tasks());
    }

    private void replaceLines(RequirementType type, List<String> lines) {
        List<JobRequirement> existing = new ArrayList<>(requirementsOf(type));
        Set<JobRequirement> kept = new HashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            String text = lines.get(i);
            JobRequirement reuse = null;
            for (Iterator<JobRequirement> it = existing.iterator(); it.hasNext(); ) {
                JobRequirement r = it.next();
                if (r.getText().equals(text)) {
                    reuse = r;
                    it.remove();
                    break;
                }
            }
            if (reuse != null) {
                reuse.moveTo(i);
                kept.add(reuse);
            } else {
                JobRequirement created = JobRequirement.of(this, type, i, text);
                requirements.add(created);
                kept.add(created);
            }
        }
        requirements.removeIf(r -> r.getType() == type && !kept.contains(r));
    }

    private static void checkLines(List<PostingViolation> v, String field, List<String> lines) {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.length() > LINE_MAX) {
                v.add(PostingViolation.of(PostingViolation.Code.LINE_TOO_LONG, field, i));
            }
            if (!seen.add(line.replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT))) {
                v.add(PostingViolation.of(PostingViolation.Code.DUPLICATE_LINE, field, i));
            }
        }
    }
}
