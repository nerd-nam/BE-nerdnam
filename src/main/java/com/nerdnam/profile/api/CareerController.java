package com.nerdnam.profile.api;

import com.nerdnam.common.auth.CurrentMemberId;
import com.nerdnam.profile.application.CareerCommand;
import com.nerdnam.profile.application.CareerService;
import com.nerdnam.profile.application.CareerView;
import com.nerdnam.profile.domain.EmploymentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 내 경력 (S03) */
@RestController
@RequestMapping("/api/me/careers")
public class CareerController {

    private final CareerService service;

    public CareerController(CareerService service) {
        this.service = service;
    }

    /** 최신순 */
    @GetMapping
    public List<CareerResponse> list(@CurrentMemberId Long memberId) {
        return service.list(memberId).stream().map(CareerResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CareerResponse create(@CurrentMemberId Long memberId, @Valid @RequestBody CareerRequest request) {
        return CareerResponse.from(service.create(memberId, request.toCommand()));
    }

    /** 요청의 version 은 조회 때 받은 값. 그사이 다른 곳에서 고쳤으면 409 */
    @PutMapping("/{careerId}")
    public CareerResponse update(@CurrentMemberId Long memberId, @PathVariable Long careerId,
                                 @Valid @RequestBody CareerRequest request) {
        return CareerResponse.from(service.update(memberId, careerId, request.toCommand()));
    }

    @DeleteMapping("/{careerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentMemberId Long memberId, @PathVariable Long careerId) {
        service.delete(memberId, careerId);
    }

    /** startMonth·endMonth 는 "2023-03" 형식. endMonth 를 비우면 재직 중 */
    public record CareerRequest(
            @NotBlank(message = "회사명을 입력해 주세요.") @Size(max = 100) String companyName,
            @NotBlank(message = "부서를 입력해 주세요.") @Size(max = 100) String teamName,
            @NotBlank(message = "직무를 입력해 주세요.") @Size(max = 100) String jobTitle,
            @Size(max = 50) String positionTitle,
            @NotNull(message = "고용형태를 골라 주세요.") EmploymentType employmentType,
            @NotNull(message = "입사 월을 입력해 주세요.") YearMonth startMonth,
            YearMonth endMonth,
            List<String> duties,
            Set<Long> userSkillIds,
            Long version) {

        CareerCommand toCommand() {
            return new CareerCommand(companyName, teamName, jobTitle, positionTitle, employmentType,
                    startMonth, endMonth, duties, userSkillIds, version);
        }
    }

    public record CareerResponse(
            Long id,
            String companyName,
            String teamName,
            String jobTitle,
            String positionTitle,
            EmploymentType employmentType,
            String startMonth,
            String endMonth,
            boolean current,
            List<String> duties,
            Set<Long> userSkillIds,
            long version) {

        static CareerResponse from(CareerView v) {
            return new CareerResponse(v.id(), v.companyName(), v.teamName(), v.jobTitle(), v.positionTitle(),
                    v.employmentType(), v.startMonth().toString(),
                    v.endMonth() == null ? null : v.endMonth().toString(), v.endMonth() == null,
                    v.duties(), v.userSkillIds(), v.version());
        }
    }
}
