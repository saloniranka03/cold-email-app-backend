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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/email")
@CrossOrigin(origins = {"http://localhost:3000", "https://cold-email-app.netlify.app"})
public class EmailController {

    private static final Logger logger = LoggerFactory.getLogger(EmailController.class);

    @Autowired
    private UpdatedEmailService emailService;

    @Autowired
    private AuthService authService;

    /**
     * Process email requests with HTTP session-based authentication
     */
    @PostMapping("/process")
    public ResponseEntity<ProcessingResult> processEmails(
            @RequestParam("file") MultipartFile file,
            @RequestParam("fullName") String fullName,
            @RequestParam("phoneNumber") String phoneNumber,
            @RequestParam(value = "linkedInUrl", required = false) String linkedInUrl,
            @RequestParam("templatesFolderPath") String templatesFolderPath,
            HttpServletRequest request) {

        logger.info("=== Starting email processing request ===");
        logger.info("File: {} (size: {} bytes)", file.getOriginalFilename(), file.getSize());
        logger.info("User: {}, Phone: {}, LinkedIn: {}", fullName, phoneNumber, 
                   linkedInUrl != null ? linkedInUrl : "not provided");
        logger.info("Templates path: {}", templatesFolderPath);

        // Check HTTP session authentication
        logger.debug("Checking HTTP session authentication...");
        HttpSession httpSession = request.getSession(false);
        
        if (httpSession == null) {
            logger.warn("Authentication failed: No HTTP session found");
            ProcessingResult errorResult = createAuthErrorResult("No session found. Please log in first.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResult);
        }

        logger.info("HTTP session found: {}", httpSession.getId());
        
        String accessToken = (String) httpSession.getAttribute("accessToken");
        String userEmail = (String) httpSession.getAttribute("userEmail");
        String userId = (String) httpSession.getAttribute("userId");

        logger.info("Session attributes - User ID: {}, Email: {}, Access Token: {}", 
                    userId, userEmail, accessToken != null ? "present" : "missing");

        if (accessToken == null || userEmail == null) {
            logger.warn("Authentication failed: Missing session attributes (accessToken: {}, userEmail: {})", 
                       accessToken != null ? "present" : "missing", userEmail);
            ProcessingResult errorResult = createAuthErrorResult("Invalid or expired session. Please log in again.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResult);
        }

        logger.info("Authentication successful for user: {}", userEmail);

        try {
            logger.debug("Creating email request object...");
            
            // Create email request object
            EmailRequest emailRequest = new EmailRequest();
            emailRequest.setFullName(fullName);
            emailRequest.setPhoneNumber(phoneNumber);
            emailRequest.setLinkedInUrl(linkedInUrl != null ? linkedInUrl : "");
            emailRequest.setTemplatesFolderPath(templatesFolderPath);

            logger.info("Email request object created successfully");
            logger.info("Request details - Full Name: {}, Phone: {}, LinkedIn: {}, Templates Path: {}", 
                        fullName, phoneNumber, linkedInUrl, templatesFolderPath);

            // Create a temporary session using HTTP session ID for the email service
            logger.debug("Creating temporary session for email service...");
            String sessionId = createTempSessionForEmailService(httpSession, accessToken, userEmail, userId);
            logger.info("Temporary session created: {}", sessionId);

            // Process emails with temporary session
            logger.info("Starting email processing with service...");
            long startTime = System.currentTimeMillis();
            
            ProcessingResult result = emailService.processEmailRequests(file, emailRequest, sessionId);
            
            long processingTime = System.currentTimeMillis() - startTime;
            logger.info("Email processing completed in {} ms", processingTime);
            
            // Log detailed results
            logger.info("=== Email Processing Results ===");
            logger.info("User: {}", userEmail);
            logger.info("Total Processed: {}", result.getTotalProcessed());
            logger.info("Successful: {}", result.getSuccessCount());
            logger.info("Errors: {}", result.getErrorCount());
            logger.info("Missing Templates: {}", result.getMissingTemplates().size());
            logger.info("Missing Resumes: {}", result.getMissingResumes().size());
            
            if (result.getErrorCount() > 0) {
                logger.warn("Processing completed with {} errors:", result.getErrorCount());
                result.getErrors().forEach(error -> logger.warn("  - {}", error));
            }
            
            if (!result.getWarnings().isEmpty()) {
                logger.info("Processing warnings:");
                result.getWarnings().forEach(warning -> logger.info("  - {}", warning));
            }
            
            logger.info("=== Processing Summary: Success Rate: {}/{} ({}%) ===", 
                       result.getSuccessCount(), result.getTotalProcessed(),
                       result.getTotalProcessed() > 0 ? (result.getSuccessCount() * 100 / result.getTotalProcessed()) : 0);

            return ResponseEntity.ok(result);

        } catch (SecurityException e) {
            logger.error("Security error during email processing for user {}: {}", userEmail, e.getMessage(), e);
            
            ProcessingResult errorResult = new ProcessingResult();
            errorResult.setTotalProcessed(0);
            errorResult.setSuccessCount(0);
            errorResult.setErrorCount(1);
            errorResult.addError("Security error: " + e.getMessage());
            errorResult.setHelpText("Authentication failed. Please log in again to continue using the service.");

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResult);
            
        } catch (IllegalArgumentException e) {
            logger.error("Invalid input during email processing for user {}: {}", userEmail, e.getMessage(), e);
            
            ProcessingResult errorResult = new ProcessingResult();
            errorResult.setTotalProcessed(0);
            errorResult.setSuccessCount(0);
            errorResult.setErrorCount(1);
            errorResult.addError("Invalid input: " + e.getMessage());
            errorResult.setHelpText("Please check your input parameters and file format, then try again.");

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResult);
            
        } catch (Exception e) {
            logger.error("Unexpected error during email processing for user {}: {}", userEmail, e.getMessage(), e);
            logger.debug("Full stack trace:", e);

            ProcessingResult errorResult = new ProcessingResult();
            errorResult.setTotalProcessed(0);
            errorResult.setSuccessCount(0);
            errorResult.setErrorCount(1);
            errorResult.addError("Processing failed: " + e.getMessage());
            errorResult.setHelpText("An unexpected error occurred. Please check your file format and try again. " +
                    "Ensure your templates folder path is correct and accessible.");

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
        } finally {
            logger.info("=== Email processing request completed ===");
        }
    }

    /**
     * Creates a temporary session for the email service using HTTP session data
     */
    private String createTempSessionForEmailService(HttpSession httpSession, String accessToken, String userEmail, String userId) {
        try {
            logger.debug("Creating temporary session bridge for email processing...");
            
            // Use the HTTP session ID as a temporary session identifier
            String tempSessionId = httpSession.getId();
            
            logger.info("Temporary session created successfully: {}", tempSessionId);
            logger.debug("Session mapping - HTTP Session: {} -> Temp Session: {}", httpSession.getId(), tempSessionId);
            
            // Store session data in AuthService for email processing
            // Note: This is a bridge solution. In production, consider unifying the auth systems.
            logger.debug("Session bridge established for user: {} with access token: {}", 
                        userEmail, accessToken != null ? "present" : "missing");
            
            return tempSessionId;
            
        } catch (Exception e) {
            logger.error("Error creating temporary session for user {}: {}", userEmail, e.getMessage(), e);
            throw new RuntimeException("Failed to create session for email processing", e);
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        logger.debug("Health check endpoint called");
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "OK");
        response.put("service", "Cold Email Service");
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        
        logger.debug("Health check response: {}", response);
        return ResponseEntity.ok(response);
    }

    /**
     * Get user info endpoint using HTTP session
     */
    @GetMapping("/user-info")
    public ResponseEntity<Map<String, Object>> getUserInfo(HttpServletRequest request) {

        logger.debug("User info endpoint called");
        Map<String, Object> response = new HashMap<>();

        HttpSession httpSession = request.getSession(false);
        if (httpSession == null) {
            logger.warn("User info request failed: No HTTP session found");
            response.put("error", "No session found");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        logger.debug("HTTP session found for user info: {}", httpSession.getId());

        String userEmail = (String) httpSession.getAttribute("userEmail");
        String userId = (String) httpSession.getAttribute("userId");
        String userName = (String) httpSession.getAttribute("userName");

        logger.debug("Session attributes - User ID: {}, Email: {}, Name: {}", 
                    userId, userEmail, userName);

        if (userEmail == null) {
            logger.warn("User info request failed: Missing userEmail in session {}", httpSession.getId());
            response.put("error", "Invalid or expired session");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        response.put("email", userEmail);
        response.put("userId", userId);
        response.put("name", userName);
        response.put("authenticated", true);

        logger.info("User info retrieved successfully for user: {}", userEmail);
        return ResponseEntity.ok(response);
    }

    /**
     * Creates error result for authentication failures
     */
    private ProcessingResult createAuthErrorResult(String message) {
        logger.debug("Creating authentication error result: {}", message);
        
        ProcessingResult result = new ProcessingResult();
        result.setTotalProcessed(0);
        result.setSuccessCount(0);
        result.setErrorCount(1);
        result.addError("Authentication Error: " + message);
        result.setHelpText("Please log in with your Google account to use this service. " +
                "Click the login button to authenticate with Gmail.");
        
        logger.debug("Authentication error result created");
        return result;
    }
}
