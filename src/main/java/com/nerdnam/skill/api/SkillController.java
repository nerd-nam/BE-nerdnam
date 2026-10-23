package com.nerdnam.skill.api;

import com.nerdnam.skill.application.SkillCatalog;
import com.nerdnam.skill.domain.SkillCategory;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 표준 기술 사전 조회 (S02 자동완성). 공용 사전이라 로그인 없이 볼 수 있다 */
@RestController
@RequestMapping("/api/skills")
public class SkillController {

    private final SkillCatalog catalog;

    public SkillController(SkillCatalog catalog) {
        this.catalog = catalog;
    }

    /** 예: GET /api/skills?query=k8s → [{"id":19,"name":"Kubernetes","category":"INFRA"}] */
    @GetMapping
    public List<SkillResponse> autocomplete(@RequestParam(required = false) String query,
                                            @RequestParam(required = false) SkillCategory category) {
        return catalog.autocomplete(query, category).stream()
                .map(skill -> new SkillResponse(skill.id(), skill.name(), skill.category()))
                .toList();
    }

    public record SkillResponse(Long id, String name, SkillCategory category) {
    }
}
