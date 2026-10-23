package com.nerdnam.skill.domain;

/**
 * 사전에 없어서 정규화하지 못한 기술명을 기록한다 (사전 보강용, 기획서 F4).
 * 구현은 infrastructure 에 있다 (SQL upsert).
 */
public interface UnregisteredSkillNameLog {

    enum Source { JOB_POSTING, PROFILE }

    void record(String rawName, SkillNameKey key, Source source);
}
