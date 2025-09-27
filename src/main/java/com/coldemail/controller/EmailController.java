// backend/src/main/java/com/coldemail/controller/EmailController.java
package com.coldemail.controller;

import com.coldemail.model.EmailRequest;
import com.coldemail.model.ProcessingResult;
import com.coldemail.service.AuthService;
import com.coldemail.service.UpdatedEmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/email")
//@CrossOrigin(origins = {"http://localhost:3000", "${app.frontend.url}"}, allowCredentials = true)
@CrossOrigin(origins = {"http://localhost:3000", "${app.frontend.url}"}, allowCredentials = "true")
//@CrossOrigin(origins = {"http://localhost:3000", "${app.frontend.url}"}, allowCredentials = "${app.cors.allow-credentials:true}")
public class EmailController {

    private static final Logger logger = LoggerFactory.getLogger(EmailController.class);

    @Autowired
    private UpdatedEmailService emailService;

    @Autowired
    private AuthService authService;

    /**
     * Process email requests with session-based authentication
     */
    @PostMapping("/process")
    public ResponseEntity<ProcessingResult> processEmails(
            @RequestParam("file") MultipartFile file,
            @RequestParam("fullName") String fullName,
            @RequestParam("phoneNumber") String phoneNumber,
            @RequestParam(value = "linkedInUrl", required = false) String linkedInUrl,
            @RequestParam("templatesFolderPath") String templatesFolderPath,
            @CookieValue(value = "session_id", required = false) String sessionId) {

        logger.info("Processing email request for user session: {}", sessionId);

        // Check authentication
        if (sessionId == null) {
            ProcessingResult errorResult = createAuthErrorResult("No session found. Please log in first.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResult);
        }

        AuthService.UserSession session = authService.getUserSession(sessionId);
        if (session == null) {
            ProcessingResult errorResult = createAuthErrorResult("Invalid or expired session. Please log in again.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResult);
        }

        try {
            // Create email request object
            EmailRequest emailRequest = new EmailRequest();
            emailRequest.setFullName(fullName);
            emailRequest.setPhoneNumber(phoneNumber);
            emailRequest.setLinkedInUrl(linkedInUrl != null ? linkedInUrl : "");
            emailRequest.setTemplatesFolderPath(templatesFolderPath);

            // Process emails with session
            ProcessingResult result = emailService.processEmailRequests(file, emailRequest, sessionId);

            logger.info("Email processing completed for user: {}. Success: {}, Errors: {}",
                    session.getEmail(), result.getSuccessCount(), result.getErrorCount());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("Email processing failed for user {}: {}", session.getEmail(), e.getMessage(), e);

            ProcessingResult errorResult = new ProcessingResult();
            errorResult.setTotalProcessed(0);
            errorResult.setSuccessCount(0);
            errorResult.setErrorCount(1);
            errorResult.addError("Processing failed: " + e.getMessage());
            errorResult.setHelpText("An unexpected error occurred. Please check your file format and try again. " +
                    "Ensure your templates folder path is correct and accessible.");

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "OK");
        response.put("service", "Cold Email Service");
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        return ResponseEntity.ok(response);
    }

    /**
     * Get user info endpoint
     */
    @GetMapping("/user-info")
    public ResponseEntity<Map<String, Object>> getUserInfo(
            @CookieValue(value = "session_id", required = false) String sessionId) {

        Map<String, Object> response = new HashMap<>();

        if (sessionId == null) {
            response.put("error", "No session found");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        AuthService.UserSession session = authService.getUserSession(sessionId);
        if (session == null) {
            response.put("error", "Invalid or expired session");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        response.put("email", session.getEmail());
        response.put("userId", session.getUserId());
        response.put("authenticated", true);

        return ResponseEntity.ok(response);
    }

    /**
     * Creates error result for authentication failures
     */
    private ProcessingResult createAuthErrorResult(String message) {
        ProcessingResult result = new ProcessingResult();
        result.setTotalProcessed(0);
        result.setSuccessCount(0);
        result.setErrorCount(1);
        result.addError("Authentication Error: " + message);
        result.setHelpText("Please log in with your Google account to use this service. " +
                "Click the login button to authenticate with Gmail.");
        return result;
    }
}