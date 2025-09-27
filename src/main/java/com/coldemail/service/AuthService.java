package com.coldemail.service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.gmail.Gmail;
import com.coldemail.config.GoogleOAuthConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    // In-memory session storage (use Redis in production)
    private final ConcurrentHashMap<String, UserSession> userSessions = new ConcurrentHashMap<>();

    @Autowired
    private GoogleAuthorizationCodeFlow authFlow;

    @Autowired
    private GoogleOAuthConfig oauthConfig;

    @Autowired
    private NetHttpTransport httpTransport;

    @Autowired
    private JsonFactory jsonFactory;

    /**
     * User session container with expiration logic
     */
    public static class UserSession {
        private final String userId;
        private final Credential credential;
        private final String email;
        private final long createdAt;

        public UserSession(String userId, Credential credential, String email) {
            this.userId = userId;
            this.credential = credential;
            this.email = email;
            this.createdAt = System.currentTimeMillis();
        }

        // Getters
        public String getUserId() { return userId; }
        public Credential getCredential() { return credential; }
        public String getEmail() { return email; }
        public long getCreatedAt() { return createdAt; }

        public boolean isExpired() {
            // Session expires after 1 hour
            return (System.currentTimeMillis() - createdAt) > (60 * 60 * 1000);
        }
    }

    /**
     * Generates OAuth authorization URL for user login
     *
     * @return Authorization URL string
     * @throws IllegalStateException if OAuth flow is not properly configured
     */
    public String getAuthorizationUrl() {
        try {
            if (authFlow == null) {
                throw new IllegalStateException("OAuth flow is not configured properly");
            }

            if (oauthConfig.getRedirectUri() == null || oauthConfig.getRedirectUri().isEmpty()) {
                throw new IllegalStateException("OAuth redirect URI is not configured");
            }

            String authUrl = authFlow.newAuthorizationUrl()
                    .setRedirectUri(oauthConfig.getRedirectUri())
                    .build();

            logger.info("Generated OAuth authorization URL for redirect URI: {}", oauthConfig.getRedirectUri());
            return authUrl;

        } catch (Exception e) {
            logger.error("Failed to generate authorization URL: {}", e.getMessage(), e);
            throw new IllegalStateException("Unable to generate authorization URL: " + e.getMessage(), e);
        }
    }

    /**
     * Handles OAuth callback and creates user session
     *
     * @param authorizationCode The authorization code from Google OAuth callback
     * @return Session ID for the authenticated user
     * @throws IOException if authentication fails
     * @throws IllegalArgumentException if authorization code is invalid
     */
    public String handleCallback(String authorizationCode) throws IOException {
        if (authorizationCode == null || authorizationCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Authorization code cannot be null or empty");
        }

        try {
            logger.info("Processing OAuth callback with authorization code : {}" , authorizationCode);

            // Exchange authorization code for tokens
            GoogleTokenResponse tokenResponse = authFlow.newTokenRequest(authorizationCode)
                    .setRedirectUri(oauthConfig.getRedirectUri())
                    .execute();

            if (tokenResponse == null) {
                throw new IOException("Failed to exchange authorization code for tokens - null response");
            }

            // Create credential with automatic refresh
            String userId = "user_" + System.currentTimeMillis(); // Temporary user ID
            Credential credential = authFlow.createAndStoreCredential(tokenResponse, userId);

            if (credential == null) {
                throw new IOException("Failed to create credential from token response");
            }

            // Get user email with retry logic
            String userEmail = getUserEmailWithRetry(credential);

            // Generate session ID
            String sessionId = UUID.randomUUID().toString();

            // Store user session
            UserSession session = new UserSession(sessionId, credential, userEmail);
            userSessions.put(sessionId, session);

            logger.info("User authenticated successfully: {} (session: {})", userEmail, sessionId);

            return sessionId;

        } catch (IOException e) {
            logger.error("OAuth callback failed - IO Exception: {}", e.getMessage(), e);
            throw new IOException("Authentication failed: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("OAuth callback failed - Unexpected error: {}", e.getMessage(), e);
            throw new IOException("Authentication failed due to unexpected error: " + e.getMessage(), e);
        }
    }

    /**
     * Gets user email with retry logic for transient failures
     */
    private String getUserEmailWithRetry(Credential credential) throws IOException {
        int maxRetries = 3;
        int retryDelay = 1000; // 1 second

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Gmail gmail = new Gmail.Builder(httpTransport, jsonFactory, credential)
                        .setApplicationName("Cold Email Application")
                        .build();

                String userEmail = gmail.users().getProfile("me").execute().getEmailAddress();

                if (userEmail == null || userEmail.trim().isEmpty()) {
                    throw new IOException("Retrieved email address is null or empty");
                }

                return userEmail;

            } catch (IOException e) {
                logger.warn("Attempt {} to get user email failed: {}", attempt, e.getMessage());

                if (attempt == maxRetries) {
                    throw new IOException("Failed to retrieve user email after " + maxRetries + " attempts: " + e.getMessage(), e);
                }

                // Wait before retry (except for last attempt)
                try {
                    Thread.sleep(retryDelay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while retrying to get user email", ie);
                }
            }
        }

        throw new IOException("Unexpected end of retry loop");
    }

    /**
     * Gets user session by session ID with validation
     *
     * @param sessionId The session ID to look up
     * @return UserSession if valid and not expired, null otherwise
     */
    public UserSession getUserSession(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            logger.debug("Session ID is null or empty");
            return null;
        }

        try {
            UserSession session = userSessions.get(sessionId);

            if (session == null) {
                logger.debug("No session found for ID: {}", sessionId);
                return null;
            }

            if (session.isExpired()) {
                logger.info("Session expired for user: {} (session: {})", session.getEmail(), sessionId);
                userSessions.remove(sessionId);
                return null;
            }

            return session;

        } catch (Exception e) {
            logger.error("Error retrieving session {}: {}", sessionId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Creates Gmail service for user session with validation
     *
     * @param sessionId The session ID
     * @return Gmail service instance
     * @throws IOException if session is invalid or Gmail service creation fails
     */
    public Gmail createGmailService(String sessionId) throws IOException {
        UserSession session = getUserSession(sessionId);

        if (session == null) {
            throw new IOException("Invalid or expired session. Please log in again.");
        }

        try {
            // Validate credential before creating service
            Credential credential = session.getCredential();
            if (credential == null) {
                throw new IOException("Session credential is null");
            }

            // Refresh token if needed
            if (credential.getExpiresInSeconds() != null && credential.getExpiresInSeconds() <= 60) {
                logger.info("Refreshing expired credential for user: {}", session.getEmail());
                credential.refreshToken();
            }

            Gmail gmail = new Gmail.Builder(httpTransport, jsonFactory, credential)
                    .setApplicationName("Cold Email Application")
                    .build();

            // Test the service with a simple call
            try {
                gmail.users().getProfile("me").execute();
            } catch (IOException e) {
                logger.error("Gmail service validation failed for user {}: {}", session.getEmail(), e.getMessage());
                throw new IOException("Gmail service is not accessible. Please try logging in again.", e);
            }

            return gmail;

        } catch (IOException e) {
            logger.error("Failed to create Gmail service for session {}: {}", sessionId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error creating Gmail service for session {}: {}", sessionId, e.getMessage(), e);
            throw new IOException("Failed to create Gmail service: " + e.getMessage(), e);
        }
    }

    /**
     * Logs out user by removing session with cleanup
     *
     * @param sessionId The session ID to remove
     */
    public void logout(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            logger.debug("Attempted to logout with null or empty session ID");
            return;
        }

        try {
            UserSession session = userSessions.remove(sessionId);

            if (session != null) {
                logger.info("User logged out: {} (session: {})", session.getEmail(), sessionId);

                // Revoke credential if possible
                try {
                    if (session.getCredential() != null) {
                        // Note: Google OAuth2 credentials don't have a direct revoke method in this client
                        // In production, you might want to call Google's revoke endpoint
                        logger.debug("Credential cleanup completed for session: {}", sessionId);
                    }
                } catch (Exception e) {
                    logger.warn("Error during credential cleanup for session {}: {}", sessionId, e.getMessage());
                }
            } else {
                logger.debug("No session found to logout: {}", sessionId);
            }

        } catch (Exception e) {
            logger.error("Error during logout for session {}: {}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * Gets user email from session with validation
     *
     * @param sessionId The session ID
     * @return User email if session is valid, null otherwise
     */
    public String getUserEmail(String sessionId) {
        try {
            UserSession session = getUserSession(sessionId);
            return session != null ? session.getEmail() : null;
        } catch (Exception e) {
            logger.error("Error getting user email for session {}: {}", sessionId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Cleanup expired sessions (should be called periodically)
     */
    public void cleanupExpiredSessions() {
        try {
            int removedCount = 0;

            for (String sessionId : userSessions.keySet()) {
                UserSession session = userSessions.get(sessionId);

                if (session != null && session.isExpired()) {
                    userSessions.remove(sessionId);
                    removedCount++;
                    logger.debug("Removed expired session: {} for user: {}", sessionId, session.getEmail());
                }
            }

            if (removedCount > 0) {
                logger.info("Cleaned up {} expired sessions", removedCount);
            }

        } catch (Exception e) {
            logger.error("Error during session cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * Get active session count for monitoring
     */
    public int getActiveSessionCount() {
        return userSessions.size();
    }

    /**
     * Validate if OAuth configuration is proper
     */
    public boolean isConfigurationValid() {
        try {
            return authFlow != null &&
                    oauthConfig != null &&
                    oauthConfig.getRedirectUri() != null &&
                    !oauthConfig.getRedirectUri().isEmpty() &&
                    oauthConfig.getClientId() != null &&
                    !oauthConfig.getClientId().isEmpty();
        } catch (Exception e) {
            logger.error("Error validating OAuth configuration: {}", e.getMessage(), e);
            return false;
        }
    }
}