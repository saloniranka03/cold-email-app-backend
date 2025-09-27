package com.coldemail.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.HttpSessionIdResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Simple session configuration for HTTP session ID resolution
 * Session cookie properties are configured in application.yml
 */
@Configuration
public class SessionConfig implements WebMvcConfigurer {
    
    private static final Logger logger = LoggerFactory.getLogger(SessionConfig.class);

    /**
     * Configure HTTP session ID resolver to use cookies
     * Cookie properties are configured in application.yml under server.servlet.session.cookie
     */
    @Bean
    public HttpSessionIdResolver httpSessionIdResolver() {
        logger.info("Configuring HTTP session ID resolver with cookies");
        return new CookieHttpSessionIdResolver();
    }
}
