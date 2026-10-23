package com.nerdnam.profile.api;

import com.nerdnam.common.auth.CurrentMemberId;
import com.nerdnam.profile.application.UserSkillService;
import com.nerdnam.profile.application.UserSkillView;
import com.nerdnam.skill.domain.SkillCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 내 기술스택 (S02, S04 의 새 기술 추가) */
@RestController
@RequestMapping("/api/me/skills")
public class UserSkillController {

    private final UserSkillService service;

    public UserSkillController(UserSkillService service) {
        this.service = service;
    }

    @GetMapping
    public List<UserSkillResponse> list(@CurrentMemberId Long memberId) {
        return service.list(memberId).stream().map(UserSkillResponse::from).toList();
    }

    /** 새로 추가하면 201, 이미 있던 기술이면 200 + 기존 항목 */
    @PostMapping
    public ResponseEntity<UserSkillResponse> add(@CurrentMemberId Long memberId,
                                                 @Valid @RequestBody AddUserSkillRequest request) {
        UserSkillService.AddResult result = service.add(memberId, request.name(), request.category(), request.level());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(UserSkillResponse.from(result.userSkill()));
    }

    @PatchMapping("/{userSkillId}")
    public UserSkillResponse changeLevel(@CurrentMemberId Long memberId, @PathVariable Long userSkillId,
                                         @Valid @RequestBody ChangeLevelRequest request) {
        return UserSkillResponse.from(service.changeLevel(memberId, userSkillId, request.level()));
    }

    @DeleteMapping("/{userSkillId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentMemberId Long memberId, @PathVariable Long userSkillId) {
        service.delete(memberId, userSkillId);
    }

    /** category 는 사전에 없는 기술을 새로 추가할 때만 필요하다. level 을 비우면 1(학습 중) */
    public record AddUserSkillRequest(
            @NotBlank(message = "기술명을 입력해 주세요.") @Size(max = 100) String name,
            SkillCategory category,
            @Min(1) @Max(4) Integer level) {
    }

    public record ChangeLevelRequest(@NotNull @Min(1) @Max(4) Integer level) {
    }

    public record UserSkillResponse(Long id, Long skillId, String name, SkillCategory category,
                                    int level, String levelName) {

        static UserSkillResponse from(UserSkillView v) {
            return new UserSkillResponse(v.id(), v.skillId(), v.name(), v.category(),
                    v.proficiency().level(), v.proficiency().label());
        }
    }
}
