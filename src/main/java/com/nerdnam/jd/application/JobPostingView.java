package com.nerdnam.jd.application;

import com.nerdnam.jd.domain.JobPosting;
import com.nerdnam.jd.domain.JobRequirement;
import com.nerdnam.jd.domain.RequirementType;
import java.time.Instant;
import java.util.List;

/** S07 양식 조회 결과 */
public record JobPostingView(Long id, String companyName, String positionName, String postingUrl, String companyIntro,
                             List<RequirementView> required, List<RequirementView> preferred, List<String> tasks,
                             long version, Instant createdAt, Instant updatedAt) {

    /** skills·minYears 는 분석을 한 번 하면 채워진다 (AI 추출 + 정규화) */
    public record RequirementView(Long id, String text, List<String> skills, Integer minYears) {

        static RequirementView from(JobRequirement r) {
            return new RequirementView(r.getId(), r.getText(), r.getSkills(), r.getMinYears());
        }
    }

    static JobPostingView from(JobPosting p) {
        return new JobPostingView(p.getId(), p.getCompanyName(), p.getPositionName(), p.getPostingUrl(),
                p.getCompanyIntro(),
                p.requirementsOf(RequirementType.REQUIRED).stream().map(RequirementView::from).toList(),
                p.requirementsOf(RequirementType.PREFERRED).stream().map(RequirementView::from).toList(),
                List.copyOf(p.getTasks()), p.getVersion(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
