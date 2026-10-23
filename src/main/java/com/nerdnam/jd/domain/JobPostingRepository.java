package com.nerdnam.jd.domain;

import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface JobPostingRepository extends Repository<JobPosting, Long> {

    JobPosting save(JobPosting posting);

    JobPosting saveAndFlush(JobPosting posting);

    void delete(JobPosting posting);

    void flush();

    Optional<JobPosting> findByIdAndMemberId(Long id, Long memberId);
}
