/**
 * Main service class orchestrating the email processing workflow.
 * Coordinates between Excel processing, template processing, and Gmail operations.
 * Enhanced EmailService.java with grouped error reporting and standardized attachment naming.
 */
package com.coldemail.service;

import com.coldemail.model.ContactInfo;
import com.coldemail.model.EmailRequest;
import com.coldemail.model.ProcessingResult;
import com.coldemail.service.TemplateService.TemplateNotFoundException;
import com.coldemail.service.TemplateService.ResumeNotFoundException;
import com.coldemail.service.TemplateService.ResumeFileResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enhanced main service class with correct placeholder format, resume naming, and standardized attachment naming.
 * Updated email service that uses session-based authentication
 */
@Service
public class UpdatedEmailService {

    private static final Logger logger = LoggerFactory.getLogger(UpdatedEmailService.class);

    @Autowired
    private ExcelService excelService;

    @Autowired
    private TemplateService templateService;

    @Autowired
    private UpdatedGmailService gmailService;

    @Autowired
    private AuthService authService;

    /**
     * Main method to process uploaded Excel file and create email drafts with session authentication
     */
    public ProcessingResult processEmailRequests(MultipartFile file, EmailRequest emailRequest, String sessionId) {
        logger.info("Starting email processing for session: {}", sessionId);

        ProcessingResult result = new ProcessingResult();

        try {
            // Validate session
            if (sessionId == null) {
                throw new SecurityException("Invalid session ID");
            }

            // Get authenticated user's email
            String senderEmail = gmailService.getUserEmail(sessionId);
            logger.info("Authenticated user email: {}", senderEmail);

            // Process Excel file
            List<ContactInfo> contacts = excelService.processExcelFile(file);
            result.setTotalProcessed(contacts.size());

            if (contacts.isEmpty()) {
                result.addWarning("No valid contacts found in Excel file");
                result.setHelpText("Check your Excel file format. Ensure it has 'Name', 'Email Id', and 'Role' columns.");
                return result;
            }

            // Process each contact with enhanced grouped error handling
            for (ContactInfo contact : contacts) {
                try {
                    processIndividualContact(contact, emailRequest, sessionId);
                    result.incrementSuccess();
                    logger.info("Successfully processed contact: {} for role: {}", contact.getEmailId(), contact.getRole());

                } catch (TemplateNotFoundException e) {
                    logger.warn("Template missing for {} ({}): {}", contact.getEmailId(), contact.getRole(), e.getMessage());
                    result.addMissingTemplate(e.getRole(), e.getExpectedPath(), contact.getEmailId());

                } catch (ResumeNotFoundException e) {
                    logger.warn("Resume missing for {} ({}): {}", contact.getEmailId(), contact.getRole(), e.getMessage());
                    result.addMissingResume(e.getRole(), e.getExpectedPath(), emailRequest.getFullName(), contact.getEmailId());

                } catch (Exception e) {
                    String errorMsg = String.format("Failed to process %s (%s): %s",
                            contact.getEmailId(), contact.getRole(), e.getMessage());
                    result.addError(errorMsg);
                    logger.error("Error processing contact {}: ", contact.getEmailId(), e);
                }
            }

            // Set helpful guidance based on error types
            setHelpText(result);

        } catch (SecurityException e) {
            result.addError("Authentication error: " + e.getMessage());
            result.setHelpText("Please log in again to continue using the service.");
            logger.error("Authentication error during processing: ", e);
        } catch (Exception e) {
            result.addError("Failed to process Excel file: " + e.getMessage());
            result.setHelpText("Check your Excel file format and ensure it's a valid .xlsx file.");
            logger.error("Fatal error during processing: ", e);
        }

        logger.info("Processing completed. Success: {}, Errors: {}, Missing Templates: {}, Missing Resumes: {}",
                result.getSuccessCount(), result.getErrorCount(),
                result.getMissingTemplates().size(), result.getMissingResumes().size());
        return result;
    }

    /**
     * Processes a single contact and creates email draft using session authentication
     */
    private void processIndividualContact(ContactInfo contact, EmailRequest emailRequest, String sessionId)
            throws Exception {

        // Prepare template replacements using user's placeholder format
        Map<String, String> replacements = prepareTemplateReplacements(contact, emailRequest);

        // Log the replacements for debugging
        logger.debug("Template replacements for {}: {}", contact.getEmailId(), replacements);

        // Process email template (throws TemplateNotFoundException if missing)
        String emailBody = templateService.processTemplate(
                emailRequest.getTemplatesFolderPath(),
                contact.getRole(),
                replacements
        );

        // Generate subject with proper role mapping
        String subject = templateService.generateSubject(contact.getRole(), emailRequest.getFullName());
        logger.debug("Generated subject: {}", subject);

        // Find resume file with standardized attachment name (throws ResumeNotFoundException if missing)
        ResumeFileResult resumeResult = templateService.findResumeFileWithAttachmentName(
                emailRequest.getTemplatesFolderPath(),
                contact.getRole(),
                emailRequest.getFullName()
        );

        Path actualResumeFile = resumeResult.getFilePath();
        String standardizedAttachmentName = resumeResult.getAttachmentName();

        logger.debug("Resume file: {}, will be attached as: {}",
                actualResumeFile.getFileName(), standardizedAttachmentName);

        // Create Gmail draft with session-based authentication
        gmailService.createDraftWithCustomAttachmentName(
                sessionId,
                contact.getEmailId(),
                subject,
                emailBody,
                actualResumeFile,
                standardizedAttachmentName
        );

        logger.info("Email draft created for {} with resume attached as '{}'",
                contact.getEmailId(), standardizedAttachmentName);
    }

    /**
     * Prepares replacement map using the user's placeholder format.
     * User's format: {NAME}, {POSITION}, {USER_NAME}, {PHONE}, {LINKEDIN}
     */
    private Map<String, String> prepareTemplateReplacements(ContactInfo contact, EmailRequest emailRequest) {
        Map<String, String> replacements = new HashMap<>();

        // Contact-specific replacements (user's format)
        String contactName = (contact.getName() != null && !contact.getName().trim().isEmpty())
                ? contact.getName().trim()
                : "";

        replacements.put("{NAME}", contactName);

        // Use full role name for position (FSE -> Full Stack Engineer)
        String fullRoleName = templateService.getFullRoleName(contact.getRole());
        replacements.put("{POSITION}", fullRoleName);

        // User-specific replacements
        replacements.put("{USER_NAME}", emailRequest.getFullName());
        replacements.put("{PHONE}", emailRequest.getPhoneNumber());

        // LinkedIn URL replacement
        String linkedInUrl = "";
        if (emailRequest.getLinkedInUrl() != null && !emailRequest.getLinkedInUrl().trim().isEmpty()) {
            linkedInUrl = emailRequest.getLinkedInUrl().trim();
        }
        replacements.put("{LINKEDIN}", linkedInUrl);

        // Also support the old format for backward compatibility
        replacements.put("{{CONTACT_NAME}}", contactName);
        replacements.put("{{ROLE}}", fullRoleName);
        replacements.put("{{FULL_NAME}}", emailRequest.getFullName());
        replacements.put("{{PHONE_NUMBER}}", emailRequest.getPhoneNumber());

        String linkedInSection = "";
        if (!linkedInUrl.isEmpty()) {
            linkedInSection = String.format("LinkedIn: <a href=\"%s\">%s</a>", linkedInUrl, linkedInUrl);
        }
        replacements.put("{{LINKEDIN_URL}}", linkedInSection);

        return replacements;
    }

    /**
     * Sets helpful guidance text based on the types of errors encountered.
     */
    private void setHelpText(ProcessingResult result) {
        int uniqueTemplateErrors = result.getMissingTemplates().size();
        int uniqueResumeErrors = result.getMissingResumes().size();
        int totalAffectedEmails = 0;

        // Count total affected emails
        for (ProcessingResult.MissingTemplate template : result.getMissingTemplates()) {
            totalAffectedEmails += template.getAffectedEmails().size();
        }
        for (ProcessingResult.MissingResume resume : result.getMissingResumes()) {
            totalAffectedEmails += resume.getAffectedEmails().size();
        }

        if (result.hasMissingTemplates() && result.hasMissingResumes()) {
            result.setHelpText(String.format("Missing %d template file(s) and %d resume file(s) affecting %d email addresses. " +
                            "Template files can have flexible names (containing the role), " +
                            "and resume files will be attached with standardized names: Full_Name_Role.extension format.",
                    uniqueTemplateErrors, uniqueResumeErrors, totalAffectedEmails));
        } else if (result.hasMissingTemplates()) {
            result.setHelpText(String.format("Missing %d template file(s) affecting %d email addresses. " +
                            "Create .txt files containing the role name (e.g., FSE.txt, backend_template.txt, ml.txt) in your templates folder. " +
                            "Check the 'Template Guide' tab for examples using placeholders like {NAME}, {POSITION}, {USER_NAME}.",
                    uniqueTemplateErrors, totalAffectedEmails));
        } else if (result.hasMissingResumes()) {
            result.setHelpText(String.format("Missing %d resume file(s) affecting %d email addresses. " +
                            "Resume files can have flexible names (containing the role), but will be attached to emails using standardized format: " +
                            "Full_Name_Role.extension (e.g., John_Smith_FSE.pdf). Ensure resume files contain the role name in your templates folder.",
                    uniqueResumeErrors, totalAffectedEmails));
        } else if (result.getErrorCount() > 0) {
            result.setHelpText("Check the error details above. Common issues include invalid email addresses in Excel file or authentication problems.");
        }
    }
}
