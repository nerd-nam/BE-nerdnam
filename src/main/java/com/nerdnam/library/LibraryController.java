package com.nerdnam.library;

import com.nerdnam.common.auth.CurrentMemberId;
import com.nerdnam.profile.application.CareerService;
import com.nerdnam.profile.application.CareerView;
import com.nerdnam.profile.application.UserSkillService;
import com.nerdnam.profile.application.UserSkillView;
import com.nerdnam.project.application.ProjectLibraryQuery;
import com.nerdnam.project.application.ProjectLibraryQuery.LibraryProject;
import com.nerdnam.project.domain.ProjectStatus;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** S06 라이브러리 한 번에 받기 (C-02 S06 1), 온보딩 진행 상태 (C-02 공통 조회 2) */
@RestController
@RequestMapping("/api/me")
public class LibraryController {

    private final UserSkillService skills;
    private final CareerService careers;
    private final ProjectLibraryQuery projects;

    public LibraryController(UserSkillService skills, CareerService careers, ProjectLibraryQuery projects) {
        this.skills = skills;
        this.careers = careers;
        this.projects = projects;
    }

    public record Stats(int skillCount, int careerCount, int projectCount, int confirmedProjectCount,
                        int confirmedEpisodeCount) {
    }

    public record SkillSummary(Long id, String name, int level) {
    }

    public record CareerSummary(Long id, String companyName, String teamName, String jobTitle, YearMonth startMonth,
                                YearMonth endMonth) {
    }

    public record Library(Stats stats, List<LibraryProject> projects, List<CareerSummary> careers,
                          List<SkillSummary> skills) {
    }

    /**
     * 온보딩 진행 상태. nextStep 은 로그인 직후 보낼 화면: skills → career(건너뛸 수 있음) → project → library (값은 소문자, D-10).
     * reviewingProjectId 가 있으면 S02·S03 에 "검토로 돌아가기"를 보여주고, S04 에 들어갈 때 경고한다.
     */
    public record Onboarding(Stats stats, Long reviewingProjectId, String nextStep) {
    }

    @GetMapping("/library")
    public Library library(@CurrentMemberId Long memberId) {
        List<UserSkillView> mySkills = skills.list(memberId);
        List<CareerView> myCareers = careers.list(memberId);
        List<LibraryProject> myProjects = projects.list(memberId);
        return new Library(stats(mySkills, myCareers, myProjects), myProjects,
                myCareers.stream().map(c -> new CareerSummary(c.id(), c.companyName(), c.teamName(), c.jobTitle(),
                        c.startMonth(), c.endMonth())).toList(),
                mySkills.stream().map(s -> new SkillSummary(s.id(), s.name(), s.proficiency().level())).toList());
    }

    @GetMapping("/onboarding")
    public Onboarding onboarding(@CurrentMemberId Long memberId) {
        List<UserSkillView> mySkills = skills.list(memberId);
        List<CareerView> myCareers = careers.list(memberId);
        List<LibraryProject> myProjects = projects.list(memberId);
        Stats stats = stats(mySkills, myCareers, myProjects);
        Long reviewing = myProjects.stream()
                .filter(p -> p.status() == ProjectStatus.REVIEW)
                .map(LibraryProject::id)
                .max(Comparator.naturalOrder())
                .orElse(null);
        String next;
        if (stats.skillCount() == 0) {
            next = "skills";
        } else if (stats.careerCount() == 0 && stats.projectCount() == 0) {
            next = "career";
        } else if (stats.confirmedProjectCount() == 0) {
            next = "project";
        } else {
            next = "library";
        }
        return new Onboarding(stats, reviewing, next);
    }

    private static Stats stats(List<UserSkillView> skills, List<CareerView> careers, List<LibraryProject> projects) {
        int confirmedProjects = (int) projects.stream().filter(p -> !p.confirmedEpisodes().isEmpty()).count();
        int confirmedEpisodes = projects.stream().mapToInt(p -> p.confirmedEpisodes().size()).sum();
        return new Stats(skills.size(), careers.size(), projects.size(), confirmedProjects, confirmedEpisodes);
    }
}
