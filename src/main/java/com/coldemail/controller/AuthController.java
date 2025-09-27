package com.coldemail.controller;

import com.coldemail.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

            // Set secure HTTP-only cookie
            Cookie sessionCookie = new Cookie("session_id", sessionId);
            sessionCookie.setHttpOnly(true);
            sessionCookie.setSecure(false); // Set to true in production with HTTPS
            sessionCookie.setPath("/");
            sessionCookie.setMaxAge(3600); // 1 hour
            response.addCookie(sessionCookie);

            // Redirect to frontend with success
            String frontendUrl = System.getenv().getOrDefault("FRONTEND_URL", "http://localhost:3000");
            String redirectUrl = frontendUrl + "/auth/success";
            logger.info("Redirecting to frontend: {}", redirectUrl);

            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", redirectUrl)
                    .body("Authentication successful. Redirecting...");

        } catch (IOException e) {
            logger.error("Authentication callback failed: {}", e.getMessage(), e);
            String frontendUrl = System.getenv().getOrDefault("FRONTEND_URL", "http://localhost:3000");
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
            @CookieValue(value = "session_id", required = false) String sessionId) {

        Map<String, Object> response = new HashMap<>();

        if (sessionId == null) {
            response.put("authenticated", false);
            response.put("message", "No session found");
            return ResponseEntity.ok(response);
        }

        AuthService.UserSession session = authService.getUserSession(sessionId);
        if (session == null) {
            response.put("authenticated", false);
            response.put("message", "Invalid or expired session");
            return ResponseEntity.ok(response);
        }

        response.put("authenticated", true);
        response.put("email", session.getEmail());
        response.put("userId", session.getUserId());

        return ResponseEntity.ok(response);
    }

    /**
     * Logs out user
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @CookieValue(value = "session_id", required = false) String sessionId,
            HttpServletResponse response) {

        if (sessionId != null) {
            authService.logout(sessionId);

            // Clear session cookie
            Cookie sessionCookie = new Cookie("session_id", "");
            sessionCookie.setHttpOnly(true);
            sessionCookie.setSecure(false); // Set to true in production
            sessionCookie.setPath("/");
            sessionCookie.setMaxAge(0); // Delete cookie
            response.addCookie(sessionCookie);
        }

        Map<String, String> responseBody = new HashMap<>();
        responseBody.put("message", "Logged out successfully");

        return ResponseEntity.ok(responseBody);
    }
}
