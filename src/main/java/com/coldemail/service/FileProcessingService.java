package com.coldemail.service;

import com.coldemail.model.ContactInfo;
import com.coldemail.model.EmailRequest;
import com.coldemail.model.ProcessingResult;
import com.coldemail.service.TemplateService.TemplateNotFoundException;
import com.coldemail.service.TemplateService.ResumeNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for handling file uploads and temporary storage during email processing.
 * Supports flexible file matching based on role name contained in filename.
 */
@Service
public class FileProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(FileProcessingService.class);

    @Autowired
    private ExcelService excelService;

    @Autowired
    private UploadedFileTemplateService uploadedFileTemplateService;

    @Autowired
    private UpdatedGmailService gmailService;

    @Autowired
    private AuthService authService;

    /**
     * Process email requests using uploaded files with flexible role-based matching
     */
    public ProcessingResult processEmailRequestsWithFiles(
            MultipartFile excelFile,
            MultipartFile[] templateFiles,
            MultipartFile[] resumeFiles,
            EmailRequest emailRequest,
            String sessionId) throws IOException {

        logger.info("Starting file-based email processing for session: {}", sessionId);

        ProcessingResult result = new ProcessingResult();
        String tempDir = null;

        try {
            // Validate session
            if (sessionId == null || authService.getUserSession(sessionId) == null) {
                throw new SecurityException("Invalid or expired session");
            }

            // Validate files
            if (templateFiles.length == 0 && resumeFiles.length == 0) {
                throw new IllegalArgumentException("At least one template or resume file must be uploaded");
            }

            // Create temporary directory for this processing session
            tempDir = createTempDirectory();
            logger.info("Created temporary directory: {}", tempDir);

            // Save uploaded files to temp directory
            saveUploadedFiles(tempDir, templateFiles, resumeFiles);

            // Process Excel file
            List<ContactInfo> contacts = excelService.processExcelFile(excelFile);
            result.setTotalProcessed(contacts.size());

            if (contacts.isEmpty()) {
                result.addWarning("No valid contacts found in Excel file");
                result.setHelpText("Check your Excel file format. Ensure it has 'Name', 'Email Id', and 'Role' columns.");
                return result;
            }

            // Process each contact using temporary files
            for (ContactInfo contact : contacts) {
                try {
                    processIndividualContactWithFiles(contact, emailRequest, sessionId, tempDir);
                    result.incrementSuccess();
                    logger.info("Successfully processed contact: {} for role: {}", contact.getEmailId(), contact.getRole());

                } catch (TemplateNotFoundException e) {
                    logger.warn("Template missing for {} ({}): {}", contact.getEmailId(), contact.getRole(), e.getMessage());
                    result.addMissingTemplate(e.getRole(), "Uploaded file containing: " + e.getRole(), contact.getEmailId());

                } catch (ResumeNotFoundException e) {
                    logger.warn("Resume missing for {} ({}): {}", contact.getEmailId(), contact.getRole(), e.getMessage());
                    result.addMissingResume(e.getRole(), "Uploaded file containing: " + e.getRole(), emailRequest.getFullName(), contact.getEmailId());

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
            result.addError("Failed to process files: " + e.getMessage());
            result.setHelpText("Check your uploaded files and try again.");
            logger.error("Fatal error during file processing: ", e);
        } finally {
            // Cleanup temporary directory
            if (tempDir != null) {
                cleanupTempDirectory(tempDir);
            }
        }

        logger.info("File processing completed. Success: {}, Errors: {}, Missing Templates: {}, Missing Resumes: {}",
                result.getSuccessCount(), result.getErrorCount(),
                result.getMissingTemplates().size(), result.getMissingResumes().size());
        return result;
    }

    /**
     * Creates a temporary directory for this processing session
     */
    private String createTempDirectory() throws IOException {
        String sessionId = UUID.randomUUID().toString();
        Path tempPath = Files.createTempDirectory("cold-email-" + sessionId);
        return tempPath.toString();
    }

    /**
     * Saves uploaded files to temporary directory with validation
     */
    private void saveUploadedFiles(String tempDir, MultipartFile[] templateFiles, MultipartFile[] resumeFiles) throws IOException {
        logger.info("Saving {} template files and {} resume files to temp directory", templateFiles.length, resumeFiles.length);

        // Save template files (.txt only)
        for (MultipartFile templateFile : templateFiles) {
            if (!templateFile.isEmpty()) {
                String filename = templateFile.getOriginalFilename();
                if (filename == null || !filename.toLowerCase().endsWith(".txt")) {
                    logger.warn("Skipping non-txt template file: {}", filename);
                    continue;
                }

                Path targetPath = Path.of(tempDir, filename);
                Files.copy(templateFile.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
                logger.debug("Saved template file: {}", filename);
            }
        }

        // Save resume files (.pdf and .docx only)
        for (MultipartFile resumeFile : resumeFiles) {
            if (!resumeFile.isEmpty()) {
                String filename = resumeFile.getOriginalFilename();
                if (filename == null || (!filename.toLowerCase().endsWith(".pdf") && !filename.toLowerCase().endsWith(".docx"))) {
                    logger.warn("Skipping invalid resume file format: {}", filename);
                    continue;
                }

                Path targetPath = Path.of(tempDir, filename);
                Files.copy(resumeFile.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
                logger.debug("Saved resume file: {}", filename);
            }
        }
    }

    /**
     * Process individual contact using files from temporary directory
     */
    private void processIndividualContactWithFiles(ContactInfo contact, EmailRequest emailRequest, String sessionId, String tempDir) throws Exception {
        // Prepare template replacements
        Map<String, String> replacements = prepareTemplateReplacements(contact, emailRequest);

        // Process email template using uploaded files
        String emailBody = uploadedFileTemplateService.processTemplateFromDirectory(
                tempDir, contact.getRole(), replacements);

        // Generate subject
        String subject = uploadedFileTemplateService.generateSubject(contact.getRole(), emailRequest.getFullName());

        // Find and prepare resume file
        TemplateService.ResumeFileResult resumeResult = uploadedFileTemplateService.findResumeFileWithAttachmentName(
                tempDir, contact.getRole(), emailRequest.getFullName());

        // Create Gmail draft
        gmailService.createDraftWithCustomAttachmentName(
                sessionId,
                contact.getEmailId(),
                subject,
                emailBody,
                resumeResult.getFilePath(),
                resumeResult.getAttachmentName()
        );

        logger.info("Email draft created for {} with resume attached as '{}'",
                contact.getEmailId(), resumeResult.getAttachmentName());
    }

    /**
     * Prepare template replacement map
     */
    private Map<String, String> prepareTemplateReplacements(ContactInfo contact, EmailRequest emailRequest) {
        Map<String, String> replacements = new HashMap<>();

        String contactName = (contact.getName() != null && !contact.getName().trim().isEmpty())
                ? contact.getName().trim() : "";

        replacements.put("{NAME}", contactName);
        replacements.put("{POSITION}", uploadedFileTemplateService.getFullRoleName(contact.getRole()));
        replacements.put("{USER_NAME}", emailRequest.getFullName());
        replacements.put("{PHONE}", emailRequest.getPhoneNumber());

        String linkedInUrl = "";
        if (emailRequest.getLinkedInUrl() != null && !emailRequest.getLinkedInUrl().trim().isEmpty()) {
            linkedInUrl = emailRequest.getLinkedInUrl().trim();
        }
        replacements.put("{LINKEDIN}", linkedInUrl);

        return replacements;
    }

    /**
     * Cleanup temporary directory and all files
     */
    private void cleanupTempDirectory(String tempDir) {
        try {
            Path tempPath = Path.of(tempDir);
            if (Files.exists(tempPath)) {
                Files.walk(tempPath)
                        .map(Path::toFile)
                        .sorted((o1, o2) -> -o1.compareTo(o2))
                        .forEach(file -> {
                            if (!file.delete()) {
                                logger.warn("Failed to delete temporary file: {}", file.getAbsolutePath());
                            }
                        });
                logger.info("Cleaned up temporary directory: {}", tempDir);
            }
        } catch (IOException e) {
            logger.error("Error cleaning up temporary directory {}: {}", tempDir, e.getMessage());
        }
    }

    /**
     * Set helpful guidance text based on errors
     */
    private void setHelpText(ProcessingResult result) {
        int uniqueTemplateErrors = result.getMissingTemplates().size();
        int uniqueResumeErrors = result.getMissingResumes().size();

        if (result.hasMissingTemplates() && result.hasMissingResumes()) {
            result.setHelpText(String.format("Missing %d template file(s) and %d resume file(s). " +
                            "Upload .txt files for templates and .pdf/.docx files for resumes. " +
                            "File names should contain the role name (e.g., 'hello_fse.txt', 'my_fse_resume.pdf').",
                    uniqueTemplateErrors, uniqueResumeErrors));
        } else if (result.hasMissingTemplates()) {
            result.setHelpText(String.format("Missing %d template file(s). " +
                            "Upload .txt files with role names in the filename (e.g., 'fse_template.txt', 'backend.txt') " +
                            "containing email content with placeholders like {NAME}, {POSITION}, {USER_NAME}.",
                    uniqueTemplateErrors));
        } else if (result.hasMissingResumes()) {
            result.setHelpText(String.format("Missing %d resume file(s). " +
                            "Upload .pdf or .docx files with role names in the filename " +
                            "(e.g., 'john_fse_resume.pdf', 'backend_cv.docx').",
                    uniqueResumeErrors));
        } else if (result.getErrorCount() > 0) {
            result.setHelpText("Check the error details above. Common issues include invalid email addresses or file format problems.");
        }
    }
}