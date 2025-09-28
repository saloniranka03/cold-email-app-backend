// backend/src/main/java/com/coldemail/config/GoogleOAuthConfig.java
package com.coldemail.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.MemoryDataStoreFactory;
import com.google.api.services.gmail.GmailScopes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Enhanced Google OAuth Configuration with comprehensive exception handling and validation
 */
@Configuration
public class GoogleOAuthConfig {

    private static final Logger logger = LoggerFactory.getLogger(GoogleOAuthConfig.class);

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(GmailScopes.GMAIL_COMPOSE);

    // Validation patterns
    private static final Pattern CLIENT_ID_PATTERN = Pattern.compile("^[0-9]+-[a-zA-Z0-9_]+\\.apps\\.googleusercontent\\.com$");
    private static final Pattern REDIRECT_URI_PATTERN = Pattern.compile("^https?://[^\\s/$.?#].[^\\s]*$");

    @Value("${google.oauth.client-id:}")
    private String clientId;

    @Value("${google.oauth.client-secret:}")
    private String clientSecret;

    @Value("${google.oauth.redirect-uri:}")
    private String redirectUri;

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    // Keep references for cleanup
    private NetHttpTransport httpTransport;
    private GoogleAuthorizationCodeFlow authFlow;

    /**
     * Post-construction validation and logging
     */
    @PostConstruct
    public void validateConfiguration() {
        logger.info("Initializing Google OAuth Configuration for profile: {}", activeProfile);

        try {
            validateRequiredProperties();
            validatePropertyFormats();
            logConfigurationStatus();

        } catch (Exception e) {
            logger.error("Critical error during OAuth configuration validation: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to initialize Google OAuth configuration", e);
        }
    }

    /**
     * Validates that all required properties are present
     */
    private void validateRequiredProperties() {
        StringBuilder missingProperties = new StringBuilder();

        if (isNullOrEmpty(clientId)) {
            missingProperties.append("google.oauth.client-id, ");
        }

        if (isNullOrEmpty(clientSecret)) {
            missingProperties.append("google.oauth.client-secret, ");
        }

        if (isNullOrEmpty(redirectUri)) {
            missingProperties.append("google.oauth.redirect-uri, ");
        }

        if (missingProperties.length() > 0) {
            String missing = missingProperties.substring(0, missingProperties.length() - 2);
            String errorMessage = String.format(
                    "Missing required OAuth configuration properties: [%s]. " +
                            "Please ensure these environment variables are set: GOOGLE_OAUTH_CLIENT_ID, " +
                            "GOOGLE_OAUTH_CLIENT_SECRET, GOOGLE_OAUTH_REDIRECT_URI", missing);

            logger.error(errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }

        logger.debug("All required OAuth properties are present");
    }

    /**
     * Validates the format of OAuth properties
     */
    private void validatePropertyFormats() {
        try {
            // Validate Client ID format
            System.out.println("clientId : " + clientId);
            if (!CLIENT_ID_PATTERN.matcher(clientId).matches()) {
                String errorMessage = String.format(
                        "Invalid Google OAuth Client ID format: '%s'. " +
                                "Expected format: xxxxxxxxx-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.apps.googleusercontent.com",
                        maskSensitiveValue(clientId));
                logger.error(errorMessage);
                throw new IllegalArgumentException(errorMessage);
            }

            // Validate Client Secret (basic length check)
            if (clientSecret.length() < 20) {
                logger.error("Google OAuth Client Secret appears to be too short (expected 20+ characters)");
                throw new IllegalArgumentException("Invalid Google OAuth Client Secret format - too short");
            }

            // Validate Redirect URI format
            if (!REDIRECT_URI_PATTERN.matcher(redirectUri).matches()) {
                String errorMessage = String.format(
                        "Invalid redirect URI format: '%s'. Must be a valid HTTP/HTTPS URL", redirectUri);
                logger.error(errorMessage);
                throw new IllegalArgumentException(errorMessage);
            }

            // Warn about localhost in production
            if (!"local".equals(activeProfile) && redirectUri.contains("localhost")) {
                logger.warn("Using localhost redirect URI in non-local profile '{}'. " +
                        "This may cause issues in production deployment.", activeProfile);
            }

            logger.debug("OAuth property formats validation passed");

        } catch (Exception e) {
            logger.error("OAuth property format validation failed: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Creates HTTP transport with proper error handling
     */
    @Bean
    public NetHttpTransport httpTransport() {
        try {
            logger.info("Creating Google HTTP transport...");

            this.httpTransport = GoogleNetHttpTransport.newTrustedTransport();

            if (this.httpTransport == null) {
                throw new IllegalStateException("Failed to create HTTP transport - null result");
            }

            logger.info("Google HTTP transport created successfully");
            return this.httpTransport;

        } catch (GeneralSecurityException e) {
            logger.error("Security error creating HTTP transport: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to create secure HTTP transport", e);
        } catch (IOException e) {
            logger.error("IO error creating HTTP transport: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to initialize HTTP transport", e);
        } catch (Exception e) {
            logger.error("Unexpected error creating HTTP transport: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to create HTTP transport due to unexpected error", e);
        }
    }

    /**
     * Creates JSON factory bean
     */
    @Bean
    public JsonFactory jsonFactory() {
        try {
            logger.debug("Creating JSON factory...");
            JsonFactory factory = JSON_FACTORY;

            if (factory == null) {
                throw new IllegalStateException("Failed to create JSON factory - null result");
            }

            logger.debug("JSON factory created successfully");
            return factory;

        } catch (Exception e) {
            logger.error("Error creating JSON factory: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to create JSON factory", e);
        }
    }

    /**
     * Creates Google client secrets with validation
     */
    @Bean
    public GoogleClientSecrets googleClientSecrets() {
        try {
            logger.info("Creating Google client secrets...");

            GoogleClientSecrets.Details details = new GoogleClientSecrets.Details();
            details.setClientId(clientId);
            details.setClientSecret(clientSecret);

            GoogleClientSecrets clientSecrets = new GoogleClientSecrets();
            clientSecrets.setInstalled(details);

            // Validate the created secrets
            if (clientSecrets.getInstalled() == null) {
                throw new IllegalStateException("Failed to set client secrets details");
            }

            if (!clientId.equals(clientSecrets.getInstalled().getClientId())) {
                throw new IllegalStateException("Client ID mismatch in created secrets");
            }

            logger.info("Google client secrets created successfully for client ID: {}",
                    maskSensitiveValue(clientId));

            return clientSecrets;

        } catch (Exception e) {
            logger.error("Error creating Google client secrets: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to create Google client secrets", e);
        }
    }

    /**
     * Creates Google authorization code flow with comprehensive error handling
     */
    @Bean
    public GoogleAuthorizationCodeFlow googleAuthorizationCodeFlow(
            NetHttpTransport httpTransport,
            GoogleClientSecrets clientSecrets) {

        try {
            logger.info("Creating Google authorization code flow...");

            // Validate dependencies
            if (httpTransport == null) {
                throw new IllegalArgumentException("HTTP transport cannot be null");
            }

            if (clientSecrets == null) {
                throw new IllegalArgumentException("Client secrets cannot be null");
            }

            // Create the flow
            this.authFlow = new GoogleAuthorizationCodeFlow.Builder(
                    httpTransport,
                    JSON_FACTORY,
                    clientSecrets,
                    SCOPES)
                    .setDataStoreFactory(new MemoryDataStoreFactory())
                    .setAccessType("offline")
                    .setApprovalPrompt("force") // Ensure refresh token is always returned
                    .build();

            if (this.authFlow == null) {
                throw new IllegalStateException("Failed to create authorization flow - null result");
            }

            // Validate the flow was created properly
            if (!SCOPES.equals(this.authFlow.getScopes())) {
                logger.warn("Scope mismatch in created flow. Expected: {}, Actual: {}",
                        SCOPES, this.authFlow.getScopes());
            }

            logger.info("Google authorization code flow created successfully with scopes: {}", SCOPES);

            return this.authFlow;

        } catch (IOException e) {
            logger.error("IO error creating authorization flow: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to create authorization flow due to IO error", e);
        } catch (Exception e) {
            logger.error("Unexpected error creating authorization flow: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to create authorization flow", e);
        }
    }

    /**
     * Logs configuration status for troubleshooting
     */
    private void logConfigurationStatus() {
        try {
            logger.info("=== Google OAuth Configuration Status ===");
            logger.info("Profile: {}", activeProfile);
            logger.info("Client ID: {}", maskSensitiveValue(clientId));
            logger.info("Client Secret: {} characters", clientSecret != null ? clientSecret.length() : 0);
            logger.info("Redirect URI: {}", redirectUri);
            logger.info("Scopes: {}", SCOPES);
            logger.info("JSON Factory: {}", JSON_FACTORY.getClass().getSimpleName());

            // Environment-specific warnings
            if ("local".equals(activeProfile)) {
                logger.info("Running in LOCAL mode - using development OAuth settings");
            } else if ("production".equals(activeProfile)) {
                logger.info("Running in PRODUCTION mode - ensure OAuth settings are production-ready");

                if (redirectUri.contains("localhost")) {
                    logger.warn("WARNING: Using localhost redirect URI in production profile!");
                }
            }

            logger.info("========================================");

        } catch (Exception e) {
            logger.warn("Error logging configuration status: {}", e.getMessage());
        }
    }

    /**
     * Cleanup method called during shutdown
     */
    @PreDestroy
    public void cleanup() {
        try {
            logger.info("Cleaning up Google OAuth configuration...");

            if (authFlow != null) {
                // Clear any stored credentials from memory
                try {
                    authFlow.getCredentialDataStore().clear();
                    logger.debug("Cleared credential data store");
                } catch (Exception e) {
                    logger.warn("Error clearing credential data store: {}", e.getMessage());
                }
            }

            // HTTP transport doesn't need explicit cleanup, but log it
            if (httpTransport != null) {
                logger.debug("HTTP transport reference cleared");
            }

            logger.info("Google OAuth configuration cleanup completed");

        } catch (Exception e) {
            logger.error("Error during OAuth configuration cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * Utility method to check for null or empty strings
     */
    private boolean isNullOrEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Masks sensitive values for logging
     */
    private String maskSensitiveValue(String value) {
        if (value == null || value.length() < 8) {
            return "***";
        }
        return value.substring(0, 4) + "***" + value.substring(value.length() - 4);
    }

    /**
     * Public method to validate current configuration
     */
    public boolean isConfigurationValid() {
        try {
            return !isNullOrEmpty(clientId) &&
                    !isNullOrEmpty(clientSecret) &&
                    !isNullOrEmpty(redirectUri) &&
                    CLIENT_ID_PATTERN.matcher(clientId).matches() &&
                    REDIRECT_URI_PATTERN.matcher(redirectUri).matches();
        } catch (Exception e) {
            logger.error("Error validating configuration: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get configuration summary for health checks
     */
    public String getConfigurationSummary() {
        try {
            return String.format("OAuth Config [Profile: %s, ClientID: %s, RedirectURI: %s, Valid: %s]",
                    activeProfile,
                    maskSensitiveValue(clientId),
                    redirectUri,
                    isConfigurationValid());
        } catch (Exception e) {
            return "OAuth Config [Error getting summary: " + e.getMessage() + "]";
        }
    }

    // Getters with validation
    public String getClientId() {
        if (isNullOrEmpty(clientId)) {
            throw new IllegalStateException("Client ID is not configured");
        }
        return clientId;
    }

    public String getRedirectUri() {
        if (isNullOrEmpty(redirectUri)) {
            throw new IllegalStateException("Redirect URI is not configured");
        }
        return redirectUri;
    }

    public List<String> getScopes() {
        return SCOPES;
    }

    public String getActiveProfile() {
        return activeProfile;
    }
}