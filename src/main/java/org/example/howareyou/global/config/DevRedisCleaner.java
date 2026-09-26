// src/main/java/.../config/DevRedisCleaner.java
package org.example.howareyou.global.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@Slf4j
@Configuration
@Profile("dev")              // ⭐ dev 프로필에서만 활성
@RequiredArgsConstructor
public class DevRedisCleaner {

    private final RedisConnectionFactory cf;

    /** 애플리케이션이 뜰 때마다 FLUSHDB */
    @Bean
    public CommandLineRunner flushRedisOnBoot() {
        return args -> {
            try (var conn = cf.getConnection()) {
                conn.flushDb();
                log.info("🧹  Redis DB flushed (dev profile)");
            }
        };
    }
}