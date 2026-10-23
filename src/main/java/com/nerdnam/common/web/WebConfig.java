package com.nerdnam.common.web;

import com.nerdnam.common.auth.CurrentMemberIdArgumentResolver;
import com.nerdnam.common.auth.MemberIdentityProvider;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.format.FormatterRegistry;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import java.util.Locale;

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

    /** JSON 본문뿐 아니라 검색 조건의 enum도 계약에 맞춰 소문자로 보낼 수 있다. */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverterFactory(new ConverterFactory<String, Enum>() {
            @Override
            public <T extends Enum> Converter<String, T> getConverter(Class<T> targetType) {
                return source -> source.isBlank() ? null : (T) Enum.valueOf(targetType, source.strip().toUpperCase(Locale.ROOT));
            }
        });
    }
}
