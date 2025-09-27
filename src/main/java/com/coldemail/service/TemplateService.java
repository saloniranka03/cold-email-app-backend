package com.coldemail.service;

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
 * Enhanced service class for handling email template operations with flexible file matching.
 * Now supports case-insensitive matching and flexible file naming patterns.
 */
@Service
public class TemplateService {

    private static final Logger logger = LoggerFactory.getLogger(TemplateService.class);

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
        // Add more mappings as needed
    }

    /**
     * Loads and processes email template for specific role with flexible file matching.
     * Now supports case-insensitive matching and files that contain the role as suffix.
     */
    public String processTemplate(String templatesFolderPath, String role, Map<String, String> replacements) throws TemplateNotFoundException {
        Path templatePath = findTemplateFile(templatesFolderPath, role);

        if (templatePath == null) {
            String expectedPath = Paths.get(templatesFolderPath, role + ".txt").toString();
            logger.error("Template file not found for role: {}", role);
            throw new TemplateNotFoundException(role, expectedPath,
                    "Create " + role + ".txt in your templates folder with email content using placeholders like {NAME}, {POSITION}, {USER_NAME}");
        }

        logger.info("Loading template: {}", templatePath);

        try {
            String templateContent = Files.readString(templatePath);
            logger.debug("Template loaded successfully, length: {} characters", templateContent.length());

            // Process template with replacements
            String processedContent = applyReplacements(templateContent, replacements);

            // Convert markdown-like formatting to HTML (preserve original line breaks)
            processedContent = convertFormattingToHtml(processedContent);

            return processedContent;
        } catch (IOException e) {
            logger.error("Error reading template file {}: {}", templatePath, e.getMessage());
            String expectedPath = Paths.get(templatesFolderPath, role + ".txt").toString();
            throw new TemplateNotFoundException(role, expectedPath,
                    "Unable to read template file. Check file permissions and content encoding.");
        }
    }

    /**
     * Finds template file with flexible matching.
     * Looks for files that:
     * 1. Exactly match role.txt (case-insensitive)
     * 2. End with _role.txt or -role.txt (case-insensitive)
     * 3. Contain role as a suffix before .txt (case-insensitive)
     */
    private Path findTemplateFile(String templatesFolderPath, String role) {
        Path templatesDir = Paths.get(templatesFolderPath);

        if (!Files.exists(templatesDir) || !Files.isDirectory(templatesDir)) {
            logger.error("Templates directory does not exist: {}", templatesFolderPath);
            return null;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(templatesDir, "*.txt")) {
            for (Path file : stream) {
                String fileName = file.getFileName().toString();
                String fileNameLower = fileName.toLowerCase();
                String roleLower = role.toLowerCase();

                // Remove .txt extension for comparison
                String baseName = fileNameLower.replace(".txt", "");

                // Check various matching patterns
                if (baseName.equals(roleLower) ||                    // exact match: role.txt
                        baseName.endsWith("_" + roleLower) ||            // ends with _role.txt
                        baseName.endsWith("-" + roleLower) ||            // ends with -role.txt
                        baseName.endsWith(roleLower)) {                  // ends with role.txt

                    logger.info("Found template file for role '{}': {}", role, fileName);
                    return file;
                }
            }
        } catch (IOException e) {
            logger.error("Error scanning templates directory: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Applies dynamic content replacements to template using user's placeholder format.
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
     * Convert formatting to HTML while preserving exact line structure.
     */
    private String convertFormattingToHtml(String content) {
        // Only convert formatting, don't add/remove lines

        // Convert bold text (both ** and existing ** patterns)
        content = content.replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>");

        // Convert bullet points starting with * or - (preserve original format)
        content = content.replaceAll("(?m)^[\\*\\-] (.*?)$", "<li>$1</li>");

        // Wrap consecutive list items in <ul> tags
        content = content.replaceAll("(<li>.*?</li>(?:\\s*<li>.*?</li>)*)", "<ul>$1</ul>");

        // Convert links
        content = content.replaceAll("\\[(.*?)\\]\\((.*?)\\)", "<a href=\"$2\">$1</a>");

        // Convert single line breaks to HTML breaks (preserve exact formatting)
        content = content.replace("\n", "<br>");

        return content;
    }

    /**
     * Generates email subject with proper role mapping.
     */
    public String generateSubject(String role, String fullName) {
        String fullRoleName = ROLE_MAPPINGS.getOrDefault(role, role);
        return String.format("Application for %s - %s", fullRoleName, fullName);
    }

    /**
     * Finds resume file for specific role with flexible matching.
     * Now supports case-insensitive matching and various naming patterns.
     */
    public Path findResumeFile(String templatesFolderPath, String role, String applicantName) throws ResumeNotFoundException {
        Path resumeFile = findResumeFileFlexible(templatesFolderPath, role, applicantName);

        if (resumeFile == null) {
            // Create expected path for error message (using the formatted name convention)
            String formattedName = formatNameForResume(applicantName);
            String expectedBaseName = formattedName + "_" + role;
            String expectedPath = Paths.get(templatesFolderPath, expectedBaseName + ".pdf").toString();

            logger.error("Resume file not found for role: {} (searched for patterns containing '{}')", role, role);
            throw new ResumeNotFoundException(role, expectedPath,
                    "Create resume file with '" + role + "' in the filename (e.g., " + expectedBaseName + ".pdf or " + expectedBaseName + ".docx)");
        }

        logger.info("Found resume file for role '{}': {}", role, resumeFile.getFileName());
        return resumeFile;
    }

    /**
     * Generates the standardized attachment filename for the resume.
     * This ensures all email attachments follow the "Title_Case_Role.extension" convention
     * regardless of the actual filename on disk.
     *
     * @param fullNameFromUI The full name entered by user on UI (e.g., "saloni ranka")
     * @param role The role from Excel sheet (e.g., "FSE")
     * @param originalFilePath The path to the actual resume file found
     * @return Standardized attachment name (e.g., "Saloni_Ranka_FSE.pdf")
     */
    public String generateAttachmentName(String fullNameFromUI, String role, Path originalFilePath) {
        // Format the name from UI to Title Case with underscores
        String formattedName = formatNameForResume(fullNameFromUI);

        // Get the file extension from the original file
        String fileName = originalFilePath.getFileName().toString();
        String extension = "";
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            extension = fileName.substring(lastDot); // includes the dot
        }

        // Construct standardized name: Title_Case_Role.extension
        String attachmentName = formattedName + "_" + role + extension;

        logger.info("Generated attachment name: {} (from UI name: '{}', role: '{}', original file: '{}')",
                attachmentName, fullNameFromUI, role, fileName);

        return attachmentName;
    }

    /**
     * Result class that contains both the file path and the standardized attachment name.
     * This allows the email service to use the correct attachment name while accessing the actual file.
     */
    public static class ResumeFileResult {
        private final Path filePath;
        private final String attachmentName;

        public ResumeFileResult(Path filePath, String attachmentName) {
            this.filePath = filePath;
            this.attachmentName = attachmentName;
        }

        public Path getFilePath() { return filePath; }
        public String getAttachmentName() { return attachmentName; }
    }

    /**
     * Enhanced method that returns both the file path and standardized attachment name.
     * Use this method when you need both the actual file and the proper attachment name.
     */
    public ResumeFileResult findResumeFileWithAttachmentName(String templatesFolderPath, String role,
                                                             String fullNameFromUI) throws ResumeNotFoundException {
        Path resumeFile = findResumeFileFlexible(templatesFolderPath, role, fullNameFromUI);

        if (resumeFile == null) {
            String formattedName = formatNameForResume(fullNameFromUI);
            String expectedBaseName = formattedName + "_" + role;
            String expectedPath = Paths.get(templatesFolderPath, expectedBaseName + ".pdf").toString();

            logger.error("Resume file not found for role: {} (searched for patterns containing '{}')", role, role);
            throw new ResumeNotFoundException(role, expectedPath,
                    "Create resume file with '" + role + "' in the filename (e.g., " + expectedBaseName + ".pdf or " + expectedBaseName + ".docx)");
        }

        // Generate standardized attachment name
        String attachmentName = generateAttachmentName(fullNameFromUI, role, resumeFile);

        logger.info("Found resume file for role '{}': {} (will be attached as: {})",
                role, resumeFile.getFileName(), attachmentName);

        return new ResumeFileResult(resumeFile, attachmentName);
    }

    /**
     * Flexible resume file finder that supports various naming patterns.
     * Looks for files that contain the role name (case-insensitive) in various formats.
     */
    private Path findResumeFileFlexible(String templatesFolderPath, String role, String applicantName) {
        Path templatesDir = Paths.get(templatesFolderPath);

        if (!Files.exists(templatesDir) || !Files.isDirectory(templatesDir)) {
            logger.error("Templates directory does not exist: {}", templatesFolderPath);
            return null;
        }

        String roleLower = role.toLowerCase();
        String formattedName = formatNameForResume(applicantName);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(templatesDir)) {
            for (Path file : stream) {
                String fileName = file.getFileName().toString();
                String fileNameLower = fileName.toLowerCase();

                // Skip if not a resume file format
                if (!fileNameLower.endsWith(".pdf") && !fileNameLower.endsWith(".docx")) {
                    continue;
                }

                // Remove file extension for comparison
                String baseName = fileNameLower.replaceAll("\\.(pdf|docx)$", "");

                // Check various matching patterns
                if (baseName.contains(roleLower)) {
                    // Additional validation: prefer files that also contain applicant name if available
                    if (applicantName != null && !applicantName.trim().isEmpty()) {
                        String namePattern = formattedName.toLowerCase();
                        if (baseName.contains(namePattern) ||
                                baseName.contains(applicantName.toLowerCase().replace(" ", "_")) ||
                                baseName.contains(applicantName.toLowerCase().replace(" ", ""))) {
                            return file; // Best match: contains both name and role
                        }
                    } else {
                        return file; // Contains role, no name to match against
                    }
                }
            }

            // Second pass: if we didn't find a file with both name and role,
            // look for any file that contains the role
            DirectoryStream<Path> secondStream = Files.newDirectoryStream(templatesDir);
            for (Path file : secondStream) {
                String fileName = file.getFileName().toString();
                String fileNameLower = fileName.toLowerCase();

                if ((fileNameLower.endsWith(".pdf") || fileNameLower.endsWith(".docx")) &&
                        fileNameLower.contains(roleLower)) {
                    secondStream.close();
                    return file;
                }
            }
            secondStream.close();

        } catch (IOException e) {
            logger.error("Error scanning templates directory for resume: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Formats applicant name to Title Case with underscores for resume filename.
     * Example: "saloni ranka" -> "Saloni_Ranka"
     */
    private String formatNameForResume(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "Resume";
        }

        // Split by spaces and convert each word to Title Case
        String[] words = name.trim().split("\\s+");
        StringBuilder formatted = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            String word = words[i].toLowerCase();
            if (!word.isEmpty()) {
                // Capitalize first letter
                word = word.substring(0, 1).toUpperCase() + word.substring(1);
                formatted.append(word);

                // Add underscore between words (not after last word)
                if (i < words.length - 1) {
                    formatted.append("_");
                }
            }
        }

        return formatted.toString();
    }

    /**
     * Maps role code to full role name.
     */
    public String getFullRoleName(String roleCode) {
        return ROLE_MAPPINGS.getOrDefault(roleCode, roleCode);
    }

    // Custom exceptions for better error handling
    public static class TemplateNotFoundException extends Exception {
        private final String role;
        private final String expectedPath;
        private final String suggestion;

        public TemplateNotFoundException(String role, String expectedPath, String suggestion) {
            super("Template file not found for role: " + role + " at " + expectedPath);
            this.role = role;
            this.expectedPath = expectedPath;
            this.suggestion = suggestion;
        }

        public String getRole() { return role; }
        public String getExpectedPath() { return expectedPath; }
        public String getSuggestion() { return suggestion; }
    }

    public static class ResumeNotFoundException extends Exception {
        private final String role;
        private final String expectedPath;
        private final String suggestion;

        public ResumeNotFoundException(String role, String expectedPath, String suggestion) {
            super("Resume file not found for role: " + role + " at " + expectedPath);
            this.role = role;
            this.expectedPath = expectedPath;
            this.suggestion = suggestion;
        }

        public String getRole() { return role; }
        public String getExpectedPath() { return expectedPath; }
        public String getSuggestion() { return suggestion; }
    }
}