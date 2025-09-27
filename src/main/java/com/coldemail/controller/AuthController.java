package com.coldemail.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = {"http://localhost:3000", "https://cold-email-app.netlify.app"}, allowCredentials = "true"))
public class AuthController {

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${google.oauth.client-id}")
    private String clientId;

    @Value("${google.oauth.client-secret}")
    private String clientSecret;

    @Value("${google.oauth.redirect-uri}")
    private String redirectUri;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * Check if user is authenticated
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getAuthStatus(HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            HttpSession session = request.getSession(false);
            
            if (session != null) {
                String userEmail = (String) session.getAttribute("userEmail");
                String userId = (String) session.getAttribute("userId");
                String accessToken = (String) session.getAttribute("accessToken");
                
                if (userEmail != null && accessToken != null) {
                    response.put("authenticated", true);
                    response.put("email", userEmail);
                    response.put("userId", userId);
                    return ResponseEntity.ok(response);
                }
            }
            
            response.put("authenticated", false);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("authenticated", false);
            response.put("error", "Failed to check auth status");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Initiate OAuth login
     */
    @GetMapping("/login")
    public ResponseEntity<Map<String, String>> login() {
        try {
            String authUrl = buildGoogleAuthUrl();
            Map<String, String> response = new HashMap<>();
            response.put("authUrl", authUrl);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Failed to generate auth URL");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Handle OAuth callback
     */
    @GetMapping("/callback")
    public ResponseEntity<Void> handleCallback(
            @RequestParam String code,
            @RequestParam(required = false) String state,
            HttpServletRequest request,
            HttpServletResponse response) {
        
        try {
            // Exchange code for access token
            String accessToken = exchangeCodeForToken(code);
            
            // Get user info from Google
            Map<String, Object> userInfo = getUserInfo(accessToken);
            
            // Store in session
            HttpSession session = request.getSession(true);
            session.setAttribute("accessToken", accessToken);
            session.setAttribute("userEmail", userInfo.get("email"));
            session.setAttribute("userId", userInfo.get("id"));
            session.setAttribute("userName", userInfo.get("name"));
            
            // Set session timeout (30 minutes)
            session.setMaxInactiveInterval(30 * 60);
            
            // Redirect to frontend success page
            String redirectUrl = frontendUrl + "/auth/success";
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(redirectUrl))
                    .build();
                    
        } catch (Exception e) {
            // Redirect to frontend error page
            try {
                String redirectUrl = frontendUrl + "/auth/error?error=authentication_failed&message=" + 
                                   java.net.URLEncoder.encode(e.getMessage(), "UTF-8");
                return ResponseEntity.status(HttpStatus.FOUND)
                        .location(URI.create(redirectUrl))
                        .build();
            } catch (Exception encodingError) {
                String redirectUrl = frontendUrl + "/auth/error?error=authentication_failed";
                return ResponseEntity.status(HttpStatus.FOUND)
                        .location(URI.create(redirectUrl))
                        .build();
            }
        }
    }

    /**
     * Logout user
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        try {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Logged out successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Failed to logout");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Build Google OAuth authorization URL
     */
    private String buildGoogleAuthUrl() {
        String scope = "email profile https://www.googleapis.com/auth/gmail.compose";
        String responseType = "code";
        String accessType = "offline";
        
        return "https://accounts.google.com/o/oauth2/v2/auth" +
                "?client_id=" + clientId +
                "&redirect_uri=" + java.net.URLEncoder.encode(redirectUri, java.nio.charset.StandardCharsets.UTF_8) +
                "&scope=" + java.net.URLEncoder.encode(scope, java.nio.charset.StandardCharsets.UTF_8) +
                "&response_type=" + responseType +
                "&access_type=" + accessType +
                "&prompt=consent";
    }

    /**
     * Exchange authorization code for access token
     */
    private String exchangeCodeForToken(String code) throws IOException, InterruptedException {
        String tokenUrl = "https://oauth2.googleapis.com/token";
        
        String requestBody = "client_id=" + clientId +
                "&client_secret=" + clientSecret +
                "&code=" + code +
                "&grant_type=authorization_code" +
                "&redirect_uri=" + java.net.URLEncoder.encode(redirectUri, java.nio.charset.StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode jsonResponse = objectMapper.readTree(response.body());
            return jsonResponse.get("access_token").asText();
        } else {
            throw new RuntimeException("Failed to exchange code for token: " + response.body());
        }
    }

    /**
     * Get user info from Google
     */
    private Map<String, Object> getUserInfo(String accessToken) throws IOException, InterruptedException {
        String userInfoUrl = "https://www.googleapis.com/oauth2/v2/userinfo";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(userInfoUrl))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode jsonResponse = objectMapper.readTree(response.body());
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", jsonResponse.get("id").asText());
            userInfo.put("email", jsonResponse.get("email").asText());
            userInfo.put("name", jsonResponse.get("name").asText());
            return userInfo;
        } else {
            throw new RuntimeException("Failed to get user info: " + response.body());
        }
    }
}
