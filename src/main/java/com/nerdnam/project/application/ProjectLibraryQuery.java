package com.nerdnam.project.application;

import com.nerdnam.project.application.ProjectViews.SkillRef;
import com.nerdnam.project.domain.Affiliation;
import com.nerdnam.project.domain.Episode;
import com.nerdnam.project.domain.EpisodeContent;
import com.nerdnam.project.domain.Project;
import com.nerdnam.project.domain.ProjectRepository;
import com.nerdnam.project.domain.ProjectStatus;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** S06 내 경험 라이브러리의 프로젝트 카드. 재등록 중이면 이전 확정 사례를 보여준다 (검색에 계속 쓰이므로) */
@Service
@Transactional(readOnly = true)
public class ProjectLibraryQuery {

    private static final int SUMMARY_PART_MAX = 40;

    private final ProjectRepository projects;
    private final ProjectLookup lookup;

    public ProjectLibraryQuery(ProjectRepository projects, ProjectLookup lookup) {
        this.projects = projects;
        this.lookup = lookup;
    }

    /** @param summaryLine "문제 → 해결 → 결과" (서버가 잘라 붙인다, C-02 S06 2) */
    public record ConfirmedEpisode(Long id, String title, String summaryLine) {
    }

    public record LibraryProject(Long id, String title, ProjectStatus status, boolean reregistering,
                                 Affiliation affiliation, String careerCompanyName, YearMonth periodStart,
                                 YearMonth periodEnd, List<SkillRef> skills, List<ConfirmedEpisode> confirmedEpisodes) {
    }

    public List<LibraryProject> list(Long memberId) {
        return projects.findAllByMemberIdOrderByIdDesc(memberId).stream().map(this::card).toList();
    }

    private LibraryProject card(Project p) {
        List<ConfirmedEpisode> confirmed = p.confirmedEpisodes().stream().map(ProjectLibraryQuery::episode).toList();
        return new LibraryProject(p.getId(), p.getTitle(), p.getStatus(), p.isReregistering(), p.getAffiliation(),
                lookup.companyName(p), p.getPeriod().startMonth(), p.getPeriod().endMonth(),
                lookup.skills(p), confirmed);
    }

    private static ConfirmedEpisode episode(Episode e) {
        EpisodeContent c = e.current().content();
        return new ConfirmedEpisode(e.getId(), c.title(), c.summaryLine(SUMMARY_PART_MAX));
    }
}
