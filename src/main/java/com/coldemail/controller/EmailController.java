package com.coldemail.controller;

import com.coldemail.model.EmailRequest;
import com.coldemail.model.ProcessingResult;
import com.coldemail.service.AuthService;
import com.coldemail.service.UpdatedEmailService;
import com.coldemail.service.FileProcessingService;
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
@CrossOrigin(origins = {"http://localhost:3000", "${app.frontend.url}"}, allowCredentials = "true")
public class EmailController {

    private static final Logger logger = LoggerFactory.getLogger(EmailController.class);

    @Autowired
    private UpdatedEmailService emailService;

    @Autowired
    private FileProcessingService fileProcessingService;

    @Autowired
    private AuthService authService;

    /**
     * ENHANCED: Process emails with either file uploads OR folder path
     * Supports both new file upload method and existing folder path method
     */
    @PostMapping("/process")
    public ResponseEntity<ProcessingResult> processEmails(
            @RequestParam("file") MultipartFile file,
            @RequestParam("fullName") String fullName,
            @RequestParam("phoneNumber") String phoneNumber,
            @RequestParam(value = "linkedInUrl", required = false) String linkedInUrl,
            @RequestParam(value = "templatesFolderPath", required = false) String templatesFolderPath,
            @RequestParam(value = "templateFiles", required = false) MultipartFile[] templateFiles,
            @RequestParam(value = "resumeFiles", required = false) MultipartFile[] resumeFiles,
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

            // Determine processing method based on what was provided
            boolean hasFileUploads = (templateFiles != null && templateFiles.length > 0) ||
                    (resumeFiles != null && resumeFiles.length > 0);
            boolean hasFolderPath = templatesFolderPath != null && !templatesFolderPath.trim().isEmpty();

            ProcessingResult result;

            if (hasFileUploads) {
                // NEW METHOD: Process with uploaded files
                logger.info("Processing with uploaded files: {} templates, {} resumes",
                        templateFiles != null ? templateFiles.length : 0,
                        resumeFiles != null ? resumeFiles.length : 0);

                result = fileProcessingService.processEmailRequestsWithFiles(
                        file,
                        templateFiles != null ? templateFiles : new MultipartFile[0],
                        resumeFiles != null ? resumeFiles : new MultipartFile[0],
                        emailRequest,
                        sessionId);

            } else if (hasFolderPath) {
                // EXISTING METHOD: Process with folder path
                logger.info("Processing with folder path: {}", templatesFolderPath);
                emailRequest.setTemplatesFolderPath(templatesFolderPath);
                result = emailService.processEmailRequests(file, emailRequest, sessionId);

            } else {
                // NO METHOD PROVIDED: Return error
                throw new IllegalArgumentException("Please provide either a templates folder path OR upload template/resume files");
            }

            logger.info("Email processing completed for user: {}. Success: {}, Errors: {}",
                    session.getEmail(), result.getSuccessCount(), result.getErrorCount());

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid request for user {}: {}", session.getEmail(), e.getMessage());
            ProcessingResult errorResult = new ProcessingResult();
            errorResult.setTotalProcessed(0);
            errorResult.setSuccessCount(0);
            errorResult.setErrorCount(1);
            errorResult.addError("Invalid request: " + e.getMessage());
            errorResult.setHelpText("Please provide either a valid templates folder path or upload your template and resume files.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResult);

        } catch (Exception e) {
            logger.error("Email processing failed for user {}: {}", session.getEmail(), e.getMessage(), e);

            ProcessingResult errorResult = new ProcessingResult();
            errorResult.setTotalProcessed(0);
            errorResult.setSuccessCount(0);
            errorResult.setErrorCount(1);
            errorResult.addError("Processing failed: " + e.getMessage());
            errorResult.setHelpText("An unexpected error occurred. Please check your file format and try again. " +
                    "Ensure your templates folder path is correct and accessible, or upload valid template and resume files.");

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
        response.put("service", "Cold Email Service - Dual Method Support");
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