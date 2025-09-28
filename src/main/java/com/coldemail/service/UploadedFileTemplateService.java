package com.coldemail.service;

import com.coldemail.service.TemplateService.TemplateNotFoundException;
import com.coldemail.service.TemplateService.ResumeNotFoundException;
import com.coldemail.service.TemplateService.ResumeFileResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Template service that works with uploaded files using flexible role-based matching.
 * Supports case-insensitive matching where role name just needs to be contained in filename.
 * Examples: For role "FSE" -> matches "hello_fse.txt", "xxxfsexxx.txt", "MyFSETemplate.txt"
 */
@Service
public class UploadedFileTemplateService {

    private static final Logger logger = LoggerFactory.getLogger(UploadedFileTemplateService.class);

    // Role mapping from short codes to full names
    private static final Map<String, String> ROLE_MAPPINGS = new HashMap<>();

    static {
        ROLE_MAPPINGS.put("FSE", "Full Stack Engineer");
        ROLE_MAPPINGS.put("Backend", "Backend Developer");
        ROLE_MAPPINGS.put("Frontend", "Frontend Developer");
        ROLE_MAPPINGS.put("DevOps", "DevOps Engineer");
        ROLE_MAPPINGS.put("QA", "Quality Assurance Engineer");
        ROLE_MAPPINGS.put("Mobile", "Mobile Application Developer");
        ROLE_MAPPINGS.put("DataScientist", "Data Scientist");
        ROLE_MAPPINGS.put("ML", "Machine Learning Engineer");
        ROLE_MAPPINGS.put("PM", "Product Manager");
        ROLE_MAPPINGS.put("TPM", "Technical Program Manager");
    }

    /**
     * Process template from uploaded files using flexible role matching
     */
    public String processTemplateFromDirectory(String tempDir, String role, Map<String, String> replacements)
            throws TemplateNotFoundException {

        Path templatePath = findTemplateFileFlexible(tempDir, role);

        if (templatePath == null) {
            String expectedName = "file containing '" + role + "'";
            logger.error("Template file not found for role: {} in uploaded files", role);
            throw new TemplateNotFoundException(role, expectedName,
                    "Upload a .txt file with '" + role + "' in the filename (e.g., 'hello_" + role.toLowerCase() + ".txt', '" + role.toLowerCase() + "_template.txt')");
        }

        logger.info("Loading uploaded template: {}", templatePath.getFileName());

        try {
            String templateContent = Files.readString(templatePath);
            logger.debug("Template loaded successfully, length: {} characters", templateContent.length());

            // Process template with replacements
            String processedContent = applyReplacements(templateContent, replacements);

            // Convert markdown-like formatting to HTML
            processedContent = convertFormattingToHtml(processedContent);

            return processedContent;
        } catch (IOException e) {
            logger.error("Error reading template file {}: {}", templatePath, e.getMessage());
            throw new TemplateNotFoundException(role, templatePath.getFileName().toString(),
                    "Unable to read uploaded template file. Check file content and encoding.");
        }
    }

    /**
     * FLEXIBLE TEMPLATE MATCHING: Find template file containing role name (case-insensitive)
     * Examples for role "FSE": hello_fse.txt, xxxfsexxx.txt, MyFSETemplate.txt all match
     */
    private Path findTemplateFileFlexible(String tempDir, String role) {
        Path directory = Paths.get(tempDir);

        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            logger.error("Temporary directory does not exist: {}", tempDir);
            return null;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.txt")) {
            String roleLower = role.toLowerCase();

            for (Path file : stream) {
                String fileName = file.getFileName().toString();
                String fileNameLower = fileName.toLowerCase();

                // FLEXIBLE MATCHING: Check if role name is contained anywhere in filename
                if (fileNameLower.contains(roleLower)) {
                    logger.info("Found uploaded template file for role '{}': {} (flexible match)", role, fileName);
                    return file;
                }
            }
        } catch (IOException e) {
            logger.error("Error scanning temporary directory for templates: {}", e.getMessage());
        }

        logger.warn("No template file found containing role '{}' in uploaded files", role);
        return null;
    }

    /**
     * Find resume file with attachment name using flexible role matching
     */
    public ResumeFileResult findResumeFileWithAttachmentName(String tempDir, String role, String fullNameFromUI)
            throws ResumeNotFoundException {

        Path resumeFile = findResumeFileFlexible(tempDir, role);

        if (resumeFile == null) {
            String expectedName = "file containing '" + role + "'";
            logger.error("Resume file not found for role: {} in uploaded files", role);
            throw new ResumeNotFoundException(role, expectedName,
                    "Upload a resume file (.pdf or .docx) with '" + role + "' in the filename (e.g., '" + role.toLowerCase() + "_resume.pdf', 'my_" + role.toLowerCase() + "_cv.docx')");
        }

        // Generate standardized attachment name
        String attachmentName = generateAttachmentName(fullNameFromUI, role, resumeFile);

        logger.info("Found uploaded resume file for role '{}': {} (will be attached as: {})",
                role, resumeFile.getFileName(), attachmentName);

        return new ResumeFileResult(resumeFile, attachmentName);
    }

    /**
     * FLEXIBLE RESUME MATCHING: Find resume file containing role name (case-insensitive)
     * Examples for role "FSE": xxxSaloFSE.docx, fsexxx.docx, my_fse_resume.pdf all match
     */
    private Path findResumeFileFlexible(String tempDir, String role) {
        Path directory = Paths.get(tempDir);

        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            logger.error("Temporary directory does not exist: {}", tempDir);
            return null;
        }

        String roleLower = role.toLowerCase();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path file : stream) {
                String fileName = file.getFileName().toString();
                String fileNameLower = fileName.toLowerCase();

                // Check if it's a valid resume file format
                if (!fileNameLower.endsWith(".pdf") && !fileNameLower.endsWith(".docx")) {
                    continue;
                }

                // FLEXIBLE MATCHING: Check if role name is contained anywhere in filename
                if (fileNameLower.contains(roleLower)) {
                    logger.info("Found uploaded resume file for role '{}': {} (flexible match)", role, fileName);
                    return file;
                }
            }
        } catch (IOException e) {
            logger.error("Error scanning temporary directory for resumes: {}", e.getMessage());
        }

        logger.warn("No resume file found containing role '{}' in uploaded files", role);
        return null;
    }

    /**
     * Apply template replacements
     */
    private String applyReplacements(String template, Map<String, String> replacements) {
        String result = template;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            String value = entry.getValue() != null ? entry.getValue() : "";
            result = result.replace(entry.getKey(), value);
        }
        return result;
    }

    /**
     * Convert formatting to HTML
     */
    private String convertFormattingToHtml(String content) {
        // Convert bold text
        content = content.replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>");

        // Convert bullet points
        content = content.replaceAll("(?m)^[\\*\\-] (.*?)$", "<li>$1</li>");

        // Wrap consecutive list items in <ul> tags
        content = content.replaceAll("(<li>.*?</li>(?:\\s*<li>.*?</li>)*)", "<ul>$1</ul>");

        // Convert links
        content = content.replaceAll("\\[(.*?)\\]\\((.*?)\\)", "<a href=\"$2\">$1</a>");

        // Convert line breaks to HTML breaks
        content = content.replace("\n", "<br>");

        return content;
    }

    /**
     * Generate standardized attachment name
     */
    public String generateAttachmentName(String fullNameFromUI, String role, Path originalFilePath) {
        String formattedName = formatNameForResume(fullNameFromUI);

        // Get file extension
        String fileName = originalFilePath.getFileName().toString();
        String extension = "";
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            extension = fileName.substring(lastDot);
        }

        // Construct standardized name
        String attachmentName = formattedName + "_" + role + extension;

        logger.info("Generated attachment name: {} (from UI name: '{}', role: '{}', original file: '{}')",
                attachmentName, fullNameFromUI, role, fileName);

        return attachmentName;
    }

    /**
     * Format name for resume filename
     */
    private String formatNameForResume(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "Resume";
        }

        String[] words = name.trim().split("\\s+");
        StringBuilder formatted = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            String word = words[i].toLowerCase();
            if (!word.isEmpty()) {
                word = word.substring(0, 1).toUpperCase() + word.substring(1);
                formatted.append(word);

                if (i < words.length - 1) {
                    formatted.append("_");
                }
            }
        }

        return formatted.toString();
    }

    /**
     * Generate email subject
     */
    public String generateSubject(String role, String fullName) {
        String fullRoleName = ROLE_MAPPINGS.getOrDefault(role, role);
        return String.format("Application for %s - %s", fullRoleName, fullName);
    }

    /**
     * Get full role name
     */
    public String getFullRoleName(String roleCode) {
        return ROLE_MAPPINGS.getOrDefault(roleCode, roleCode);
    }
}