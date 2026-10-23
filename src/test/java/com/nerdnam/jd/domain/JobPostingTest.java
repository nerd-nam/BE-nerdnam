package com.nerdnam.jd.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nerdnam.common.error.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 공고 임시 저장·분석 전 검사 (기획서 F4, S07 메시지 표) */
class JobPostingTest {

    private static PostingContent content(String company, String position, List<String> required, List<String> preferred) {
        return new PostingContent(company, position, null, "", required, preferred, List.of());
    }

    @Test
    void 임시_저장은_규칙을_검사하지_않고_빈_줄만_버린다() {
        JobPosting posting = JobPosting.createDraft(1L, content("", "", List.of("", "가".repeat(200), "  "), null));

        assertThat(posting.lines(RequirementType.REQUIRED)).containsExactly("가".repeat(200));
        assertThat(posting.getCompanyName()).isEmpty();
    }

    @Test
    void 분석_전_검사는_위반을_모두_모아_돌려준다() {
        JobPosting posting = JobPosting.createDraft(1L, content(" ", "", List.of(),
                List.of("Kafka 운영 경험", "가".repeat(151), "kafka  운영 경험")));

        assertThat(posting.violations())
                .extracting(PostingViolation::code, PostingViolation::field, PostingViolation::index)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(PostingViolation.Code.COMPANY_NAME_REQUIRED, "companyName", null),
                        org.assertj.core.groups.Tuple.tuple(PostingViolation.Code.POSITION_REQUIRED, "positionName", null),
                        org.assertj.core.groups.Tuple.tuple(PostingViolation.Code.REQUIRED_REQUIREMENT_MISSING, "required", null),
                        org.assertj.core.groups.Tuple.tuple(PostingViolation.Code.LINE_TOO_LONG, "preferred", 1),
                        org.assertj.core.groups.Tuple.tuple(PostingViolation.Code.DUPLICATE_LINE, "preferred", 2));
        assertThatThrownBy(posting::validateForAnalysis)
                .isInstanceOf(BusinessException.class)
                .hasMessage("고쳐야 할 항목이 5개 있어요. 빨간색으로 표시된 곳을 확인해 주세요.");
    }

    @Test
    void 정확히_150자는_통과한다() {
        JobPosting posting = JobPosting.createDraft(1L, content("회사", "백엔드", List.of("가".repeat(150)), null));

        assertThat(posting.violations()).isEmpty();
    }

    @Test
    void 다시_저장해도_문구가_같은_요건은_그대로_두고_순서만_맞춘다() {
        JobPosting posting = JobPosting.createDraft(1L, content("회사", "백엔드", List.of("Java 3년", "RDBMS 설계"), null));
        JobRequirement rdbms = posting.requirementsOf(RequirementType.REQUIRED).get(1);
        rdbms.applyExtraction(List.of("RDBMS"), List.of("RDBMS"), null);

        posting.update(content("회사", "백엔드", List.of("RDBMS 설계", "Kafka 운영"), null));

        List<JobRequirement> required = posting.requirementsOf(RequirementType.REQUIRED);
        assertThat(required).extracting(JobRequirement::getText).containsExactlyInAnyOrder("RDBMS 설계", "Kafka 운영");
        assertThat(rdbms.getLineNo()).isZero();
        assertThat(rdbms.getSkills()).as("추출 결과 유지").containsExactly("RDBMS");
        assertThat(posting.getRequirements()).hasSize(2);
    }

    @Test
    void 분석_순서는_필수_다음_우대() {
        JobPosting posting = JobPosting.createDraft(1L, content("회사", "백엔드", List.of("필수1"), List.of("우대1")));

        assertThat(posting.orderedRequirements()).extracting(JobRequirement::getType)
                .containsExactly(RequirementType.REQUIRED, RequirementType.PREFERRED);
    }
}
