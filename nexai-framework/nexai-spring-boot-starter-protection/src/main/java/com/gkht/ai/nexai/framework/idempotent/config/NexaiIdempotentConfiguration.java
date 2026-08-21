package com.gkht.ai.nexai.framework.idempotent.config;

import com.gkht.ai.nexai.framework.idempotent.core.aop.IdempotentAspect;
import com.gkht.ai.nexai.framework.idempotent.core.keyresolver.impl.DefaultIdempotentKeyResolver;
import com.gkht.ai.nexai.framework.idempotent.core.keyresolver.impl.ExpressionIdempotentKeyResolver;
import com.gkht.ai.nexai.framework.idempotent.core.keyresolver.IdempotentKeyResolver;
import com.gkht.ai.nexai.framework.idempotent.core.keyresolver.impl.UserIdempotentKeyResolver;
import com.gkht.ai.nexai.framework.idempotent.core.redis.IdempotentRedisDAO;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import com.gkht.ai.nexai.framework.redis.config.NexaiRedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

@AutoConfiguration(after = NexaiRedisAutoConfiguration.class)
public class NexaiIdempotentConfiguration {

    @Bean
    public IdempotentAspect idempotentAspect(List<IdempotentKeyResolver> keyResolvers, IdempotentRedisDAO idempotentRedisDAO) {
        return new IdempotentAspect(keyResolvers, idempotentRedisDAO);
    }

    @Bean
    public IdempotentRedisDAO idempotentRedisDAO(StringRedisTemplate stringRedisTemplate) {
        return new IdempotentRedisDAO(stringRedisTemplate);
    }

    // ========== 各种 IdempotentKeyResolver Bean ==========

    @Bean
    public DefaultIdempotentKeyResolver defaultIdempotentKeyResolver() {
        return new DefaultIdempotentKeyResolver();
    }

    @Bean
    public UserIdempotentKeyResolver userIdempotentKeyResolver() {
        return new UserIdempotentKeyResolver();
    }

    @Bean
    public ExpressionIdempotentKeyResolver expressionIdempotentKeyResolver() {
        return new ExpressionIdempotentKeyResolver();
    }

}
