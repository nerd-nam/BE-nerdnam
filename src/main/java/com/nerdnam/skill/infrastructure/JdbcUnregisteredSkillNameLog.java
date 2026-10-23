package com.nerdnam.skill.infrastructure;

import com.nerdnam.skill.domain.SkillNameKey;
import com.nerdnam.skill.domain.UnregisteredSkillNameLog;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class JdbcUnregisteredSkillNameLog implements UnregisteredSkillNameLog {

    private final JdbcClient jdbc;

    JdbcUnregisteredSkillNameLog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 같은 키·출처가 이미 있으면 횟수만 올린다 */
    @Override
    public void record(String rawName, SkillNameKey key, Source source) {
        jdbc.sql("""
                        INSERT INTO app.unregistered_skill_name (raw_name, name_key, source)
                        VALUES (:rawName, :nameKey, :source)
                        ON CONFLICT (name_key, source) DO UPDATE
                           SET occurrence_count = app.unregistered_skill_name.occurrence_count + 1,
                               last_seen_at     = now(),
                               raw_name         = EXCLUDED.raw_name
                        """)
                .param("rawName", rawName.strip())
                .param("nameKey", key.value())
                .param("source", source.name())
                .update();
    }
}
