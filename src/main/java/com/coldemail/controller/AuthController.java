package com.coldemail.controller;

import com.coldemail.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = {"http://localhost:3000", "${app.frontend.url}"}, allowCredentials = "true")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private AuthService authService;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    /**
     * Initiates OAuth flow by returning authorization URL
     */
    @GetMapping("/login")
    public ResponseEntity<Map<String, String>> login() {
        try {
            String authUrl = authService.getAuthorizationUrl();

            Map<String, String> response = new HashMap<>();
            response.put("authUrl", authUrl);
            response.put("message", "Redirect to this URL to authenticate with Google");

            logger.info("Generated auth URL: {}", authUrl);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to generate auth URL: {}", e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to generate authentication URL");
            error.put("details", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Handles OAuth callback from Google
     */
    @GetMapping("/callback")
    public ResponseEntity<String> handleCallback(
            @RequestParam("code") String authorizationCode,
            @RequestParam(value = "state", required = false) String state,
            HttpServletResponse response) {

        try {
            String sessionId = authService.handleCallback(authorizationCode);
            logger.info("OAuth callback successful, session created: {}", sessionId);

            // Configure cookie based on environment
            Cookie sessionCookie = createSessionCookie(sessionId);
            response.addCookie(sessionCookie);

            // For production deployment, also try setting via response header
            log.info("Active profile in AuthContrller : {}", activeProfile);
            if (!"local".equals(activeProfile)) {
                String cookieHeader = buildCookieHeader(sessionId);
                response.setHeader("Set-Cookie", cookieHeader);
                logger.info("Set session cookie via header for production: {}", cookieHeader);
            }

            // Redirect to frontend with success
            String redirectUrl = frontendUrl + "/auth/success";
            logger.info("Redirecting to frontend: {}", redirectUrl);

            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", redirectUrl)
                    .body("Authentication successful. Redirecting...");

        } catch (IOException e) {
            logger.error("Authentication callback failed: {}", e.getMessage(), e);
            String redirectUrl = frontendUrl + "/auth/error?message=" + e.getMessage();

            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", redirectUrl)
                    .body("Authentication failed. Redirecting...");
        }
    }

    /**
     * Creates session cookie with appropriate settings for environment
     */
    private Cookie createSessionCookie(String sessionId) {
        Cookie sessionCookie = new Cookie("session_id", sessionId);
        sessionCookie.setHttpOnly(true);
        sessionCookie.setPath("/");
        sessionCookie.setMaxAge(3600); // 1 hour

        // Environment-specific settings
        if ("local".equals(activeProfile)) {
            // Local development settings
            sessionCookie.setSecure(false);
            // No SameSite needed for localhost
        } else {
            // Production settings for cross-domain
            sessionCookie.setSecure(true); // Required for SameSite=None
            // Note: SameSite attribute needs to be set via header in production
        }

        return sessionCookie;
    }

    /**
     * Builds cookie header string with SameSite attribute for production
     */
    private String buildCookieHeader(String sessionId) {
        StringBuilder cookieHeader = new StringBuilder();
        cookieHeader.append("session_id=").append(sessionId);
        cookieHeader.append("; Path=/");
        cookieHeader.append("; Max-Age=3600");
        cookieHeader.append("; HttpOnly");

        if (!"local".equals(activeProfile)) {
            cookieHeader.append("; Secure");
            cookieHeader.append("; SameSite=None");
        }

        return cookieHeader.toString();
    }

    /**
     * Alternative endpoint for session validation via URL parameter
     * Use this as fallback if cookies don't work
     */
    @GetMapping("/callback-with-session")
    public ResponseEntity<String> handleCallbackWithSession(
            @RequestParam("code") String authorizationCode,
            @RequestParam(value = "state", required = false) String state) {

        try {
            String sessionId = authService.handleCallback(authorizationCode);
            logger.info("OAuth callback successful, session created: {}", sessionId);

            // Redirect to frontend with session ID in URL as fallback
            String redirectUrl = frontendUrl + "/auth/success?session=" + sessionId;
            logger.info("Redirecting to frontend with session parameter: {}", redirectUrl);

            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", redirectUrl)
                    .body("Authentication successful. Redirecting...");

        } catch (IOException e) {
            logger.error("Authentication callback failed: {}", e.getMessage(), e);
            String redirectUrl = frontendUrl + "/auth/error?message=" + e.getMessage();

            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", redirectUrl)
                    .body("Authentication failed. Redirecting...");
        }
    }

    /**
     * Checks authentication status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getAuthStatus(
            @CookieValue(value = "session_id", required = false) String sessionId,
            @RequestParam(value = "session", required = false) String sessionParam) {

        Map<String, Object> response = new HashMap<>();

        // Try cookie first, then URL parameter as fallback
        String effectiveSessionId = sessionId != null ? sessionId : sessionParam;

        if (effectiveSessionId == null) {
            response.put("authenticated", false);
            response.put("message", "No session found");
            logger.info("No session found in cookie or parameter");
            return ResponseEntity.ok(response);
        }

        AuthService.UserSession session = authService.getUserSession(effectiveSessionId);
        if (session == null) {
            response.put("authenticated", false);
            response.put("message", "Invalid or expired session");
            logger.info("Invalid session: {}", effectiveSessionId);
            return ResponseEntity.ok(response);
        }

        response.put("authenticated", true);
        response.put("email", session.getEmail());
        response.put("userId", session.getUserId());
        logger.info("Session valid for user: {}", session.getEmail());

        return ResponseEntity.ok(response);
    }

    /**
     * Logs out user
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @CookieValue(value = "session_id", required = false) String sessionId,
            @RequestParam(value = "session", required = false) String sessionParam,
            HttpServletResponse response) {

        String effectiveSessionId = sessionId != null ? sessionId : sessionParam;
        logger.info("effectiveSessionId : {}", effectiveSessionId);
        if (effectiveSessionId != null) {
            authService.logout(effectiveSessionId);

            // Clear session cookie
            Cookie sessionCookie = new Cookie("session_id", "");
            sessionCookie.setHttpOnly(true);
            sessionCookie.setSecure(!"local".equals(activeProfile));
            sessionCookie.setPath("/");
            sessionCookie.setMaxAge(0); // Delete cookie
            response.addCookie(sessionCookie);
        }

        Map<String, String> responseBody = new HashMap<>();
        responseBody.put("message", "Logged out successfully");

        return ResponseEntity.ok(responseBody);
    }
}