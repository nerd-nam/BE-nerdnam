package com.nerdnam.member.domain;

import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface SocialAccountRepository extends Repository<SocialAccount, Long> {

    SocialAccount save(SocialAccount account);

    Optional<SocialAccount> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);
}
