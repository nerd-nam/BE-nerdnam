package com.nerdnam.common.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * created_at / updated_at 컬럼을 가진 엔티티의 공통 부모.
 * 값은 Hibernate 가 저장·수정 시점에 채운다. 도메인 코드에서 직접 바꾸지 않는다.
 */
@Getter
@MappedSuperclass
public abstract class BaseTimeEntity {

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
