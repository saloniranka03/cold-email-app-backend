package com.coldemail.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.HttpSessionIdResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.annotation.WebListener;

/**
 * Enhanced session configuration for proper session cookie handling
 * with CORS and session persistence
 */
@Configuration
public class SessionConfig implements WebMvcConfigurer {
    
    private static final Logger logger = LoggerFactory.getLogger(SessionConfig.class);

    /**
     * Configure HTTP session ID resolver to use cookies
     */
    @Bean
    public HttpSessionIdResolver httpSessionIdResolver() {
        logger.info("Configuring HTTP session ID resolver with cookies");
        return new CookieHttpSessionIdResolver();
    }

    /**
     * Configure session cookie properties for CORS compatibility
     */
    @Bean
    @WebListener
    public ServletContextListener servletContextListener() {
        return new ServletContextListener() {
            @Override
            public void contextInitialized(ServletContextEvent sce) {
                logger.info("Initializing servlet context with session cookie configuration");
                
                SessionCookieConfig sessionCookieConfig = sce.getServletContext().getSessionCookieConfig();
                
                // Configure session cookie for CORS
                sessionCookieConfig.setHttpOnly(true);  // Security: prevent XSS
                sessionCookieConfig.setSecure(false);   // Set to true in production with HTTPS
                sessionCookieConfig.setPath("/");       // Make cookie available to all paths
                sessionCookieConfig.setMaxAge(30 * 60); // 30 minutes
                sessionCookieConfig.setName("JSESSIONID"); // Standard name
                
                // Important for CORS: Set SameSite to Lax for cross-origin requests
                sessionCookieConfig.setAttribute("SameSite", "Lax");
                
                // Set session timeout
                sce.getServletContext().setSessionTimeout(30); // 30 minutes
                
                logger.info("Session cookie configuration completed:");
                logger.info("  HttpOnly: {}", sessionCookieConfig.isHttpOnly());
                logger.info("  Secure: {}", sessionCookieConfig.isSecure());
                logger.info("  Path: {}", sessionCookieConfig.getPath());
                logger.info("  MaxAge: {}", sessionCookieConfig.getMaxAge());
                logger.info("  Name: {}", sessionCookieConfig.getName());
                logger.info("  Session timeout: {} minutes", sce.getServletContext().getSessionTimeout());
            }
            
            @Override
            public void contextDestroyed(ServletContextEvent sce) {
                logger.info("Servlet context destroyed - cleaning up session configuration");
            }
        };
    }
}
