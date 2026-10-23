package com.nerdnam.jd.application;

import com.nerdnam.common.error.BusinessException;
import com.nerdnam.common.error.ErrorCode;
import com.nerdnam.jd.domain.JobPosting;
import com.nerdnam.jd.domain.JobPostingRepository;
import com.nerdnam.jd.domain.JobRequirement;
import com.nerdnam.jd.domain.PostingContent;
import com.nerdnam.skill.application.SkillCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공고 입력 (S07, BE-11) — jd 모듈의 공개 입구. matching 은 분석할 때 이 서비스만 쓴다 */
@Service
@Transactional
public class JobPostingService {

    private final JobPostingRepository postings;
    private final SkillCatalog skillCatalog;

    public JobPostingService(JobPostingRepository postings, SkillCatalog skillCatalog) {
        this.postings = postings;
        this.skillCatalog = skillCatalog;
    }

    @Transactional(readOnly = true)
    public JobPostingView get(Long memberId, Long postingId) {
        return JobPostingView.from(load(memberId, postingId));
    }

    /** 새 공고의 첫 임시 저장 — 여기서 id 가 생긴다 (웹 주소 /apply/new/jd → /apply/{id}/jd) */
    public JobPostingView create(Long memberId, PostingContent content) {
        return JobPostingView.from(postings.saveAndFlush(JobPosting.createDraft(memberId, content)));
    }

    /** 자동 임시 저장. 화면이 받아 간 version 과 다르면 409 */
    public JobPostingView update(Long memberId, Long postingId, PostingContent content, Long expectedVersion) {
        JobPosting posting = load(memberId, postingId);
        if (expectedVersion == null || expectedVersion != posting.getVersion()) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        posting.update(content);
        postings.flush();
        return JobPostingView.from(posting);
    }

    /** 공고 삭제 — 분석·작성 방향도 함께 지워진다 (DB CASCADE) */
    public void delete(Long memberId, Long postingId) {
        postings.delete(load(memberId, postingId));
    }

    /** 분석 시작 전 검사 (S07 메시지 표). 통과하면 분석에 쓸 내용을 돌려준다 */
    public PostingForAnalysis prepareAnalysis(Long memberId, Long postingId) {
        JobPosting posting = load(memberId, postingId);
        posting.validateForAnalysis();
        return toAnalysis(posting);
    }

    /**
     * AI 가 뽑은 기술명 원문·최소 연차를 요건에 붙인다. 기술명은 표준 사전으로 정규화한다 (사전에 없으면 원문 그대로 + 기록).
     *
     * @param extracted 요건 id → (기술명 원문, 최소 연차)
     */
    public PostingForAnalysis applyExtraction(Long memberId, Long postingId, Map<Long, Extracted> extracted) {
        JobPosting posting = load(memberId, postingId);
        for (JobRequirement r : posting.orderedRequirements()) {
            Extracted e = extracted.get(r.getId());
            if (e != null) {
                List<String> raw = e.skillsRaw() == null ? List.of() : e.skillsRaw();
                posting.applyExtraction(r.getId(), raw, skillCatalog.normalizeForPosting(raw), e.minYears());
            }
        }
        postings.flush();
        return toAnalysis(posting);
    }

    public record Extracted(List<String> skillsRaw, Integer minYears) {
    }

    private JobPosting load(Long memberId, Long postingId) {
        return postings.findByIdAndMemberId(postingId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "공고를 찾을 수 없어요."));
    }

    private static PostingForAnalysis toAnalysis(JobPosting posting) {
        List<PostingForAnalysis.Line> lines = new ArrayList<>();
        int order = 1;
        for (JobRequirement r : posting.orderedRequirements()) {
            lines.add(new PostingForAnalysis.Line(r.getId(), r.getType().name(), r.getText(), order++, r.getSkills(),
                    r.getMinYears()));
        }
        return new PostingForAnalysis(posting.getId(), posting.getCompanyName(), posting.getPositionName(),
                posting.getCompanyIntro(), List.copyOf(posting.getTasks()), lines);
    }
}
