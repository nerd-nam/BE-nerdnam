package com.nerdnam.common.web;

import com.nerdnam.common.auth.CurrentMemberIdArgumentResolver;
import com.nerdnam.common.auth.MemberIdentityProvider;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final MemberIdentityProvider memberIdentityProvider;

    public WebConfig(MemberIdentityProvider memberIdentityProvider) {
        this.memberIdentityProvider = memberIdentityProvider;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentMemberIdArgumentResolver(memberIdentityProvider));
    }
}
