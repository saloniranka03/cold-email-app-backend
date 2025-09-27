// Fixed ProcessingResult.java with correct error counting logic
package com.coldemail.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enhanced model class with correct error counting logic.
 *
 * IMPORTANT: Error counting logic explanation:
 * - Each individual email processing attempt counts as 1 unit
 * - successCount = number of emails successfully processed
 * - errorCount = number of emails that failed processing (regardless of failure reason)
 * - If 6 emails fail due to 2 missing templates, errorCount should be 6 (not 2)
 * - The grouped display (missingTemplates/missingResumes) is just for UI organization
 * - Total processed should always equal successCount + errorCount
 *
 * Example scenarios:
 * - Scenario 1: 6 emails, all fail due to same missing template → errorCount = 6
 * - Scenario 2: 6 emails, 4 succeed, 2 fail → successCount = 4, errorCount = 2
 * - Scenario 3: 6 emails, 3 fail due to missing template, 2 fail due to missing resume, 1 succeeds
 *   → successCount = 1, errorCount = 5 (3+2)
 */
public class ProcessingResult {
    // Core counters - these represent individual email processing results
    private int totalProcessed;    // Total number of emails attempted to process
    private int successCount;      // Number of emails successfully processed
    private int errorCount;        // Number of emails that failed processing

    // Error details for user feedback
    private List<String> errors;   // General error messages
    private List<String> warnings; // Warning messages

    // Grouped error information for organized UI display
    // NOTE: These are grouped by role for UI clarity, but don't affect counting logic
    private List<MissingTemplate> missingTemplates; // Unique missing templates by role
    private List<MissingResume> missingResumes;     // Unique missing resumes by role

    private String helpText; // User guidance text

    // Internal maps to group errors by role for UI display (not for counting)
    private Map<String, MissingTemplate> templatesByRole = new HashMap<>();
    private Map<String, MissingResume> resumesByRole = new HashMap<>();

    public ProcessingResult() {
        this.errors = new ArrayList<>();
        this.warnings = new ArrayList<>();
        this.missingTemplates = new ArrayList<>();
        this.missingResumes = new ArrayList<>();
    }

    /**
     * Adds a general error message and increments the error count.
     * This method should be called for each email that fails processing.
     *
     * @param error The error message to add
     */
    public void addError(String error) {
        this.errors.add(error);
        this.errorCount++; // Each call increments error count by 1
    }

    /**
     * Adds a warning message (doesn't affect error count).
     *
     * @param warning The warning message to add
     */
    public void addWarning(String warning) {
        this.warnings.add(warning);
    }

    /**
     * Adds missing template information for UI display AND increments error count.
     *
     * CRITICAL: This method increments errorCount because it represents a failed email processing.
     * Even if multiple emails fail due to the same missing template, each email failure
     * should increment the error count.
     *
     * @param role The role for which template is missing
     * @param expectedPath The expected file path
     * @param affectedEmail The email address that failed due to this missing template
     */
    public void addMissingTemplate(String role, String expectedPath, String affectedEmail) {
        // STEP 1: Increment error count for this failed email processing
        this.errorCount++;

        // STEP 2: Group by role for UI display (this doesn't affect counting)
        MissingTemplate template = templatesByRole.get(role);
        if (template == null) {
            // First time seeing this role - create new grouped entry
            template = new MissingTemplate();
            template.setRole(role);
            template.setExpectedPath(expectedPath);
            template.setSuggestion("Create " + role + ".txt with email template content using placeholders like {NAME}, {POSITION}, {USER_NAME}");
            template.setAffectedEmails(new ArrayList<>());
            templatesByRole.put(role, template);

            // Add to general errors list (once per role for readability)
            this.errors.add("Template missing: " + role + ".txt not found at " + expectedPath);
        }

        // STEP 3: Add this email to the affected emails list for UI display
        template.getAffectedEmails().add(affectedEmail);

        // STEP 4: Update the UI display list
        updateMissingTemplatesList();
    }

    /**
     * Adds missing resume information for UI display AND increments error count.
     *
     * CRITICAL: Similar to addMissingTemplate, this increments errorCount for each
     * failed email processing, regardless of whether other emails failed for the same reason.
     *
     * @param role The role for which resume is missing
     * @param expectedPath The expected file path
     * @param applicantName The applicant's name
     * @param affectedEmail The email address that failed due to this missing resume
     */
    public void addMissingResume(String role, String expectedPath, String applicantName, String affectedEmail) {
        // STEP 1: Increment error count for this failed email processing
        this.errorCount++;

        // STEP 2: Group by role for UI display (this doesn't affect counting)
        MissingResume resume = resumesByRole.get(role);
        if (resume == null) {
            // First time seeing this role - create new grouped entry
            resume = new MissingResume();
            resume.setRole(role);
            resume.setExpectedPath(expectedPath);
            resume.setSuggestion("Create resume file named " + applicantName + "_" + role + ".pdf or " + applicantName + "_" + role + ".docx");
            resume.setAffectedEmails(new ArrayList<>());
            resumesByRole.put(role, resume);

            // Add to general errors list (once per role for readability)
            this.errors.add("Resume missing: " + role + " resume not found at " + expectedPath);
        }

        // STEP 3: Add this email to the affected emails list for UI display
        resume.getAffectedEmails().add(affectedEmail);

        // STEP 4: Update the UI display list
        updateMissingResumesList();
    }

    /**
     * Updates the missing templates list for UI display.
     * This is called after adding/modifying template entries.
     */
    private void updateMissingTemplatesList() {
        this.missingTemplates = new ArrayList<>(templatesByRole.values());
    }

    /**
     * Updates the missing resumes list for UI display.
     * This is called after adding/modifying resume entries.
     */
    private void updateMissingResumesList() {
        this.missingResumes = new ArrayList<>(resumesByRole.values());
    }

    /**
     * Increments the success count when an email is processed successfully.
     * This should be called once for each email that processes without errors.
     */
    public void incrementSuccess() {
        this.successCount++;
    }

    /**
     * Validates that the counting logic is correct.
     * totalProcessed should always equal successCount + errorCount
     *
     * @return true if counts are consistent, false otherwise
     */
    public boolean isCountingConsistent() {
        return totalProcessed == (successCount + errorCount);
    }

    /**
     * Gets a summary string for debugging purposes.
     *
     * @return A string summarizing the processing results
     */
    public String getCountingSummary() {
        return String.format("Total: %d, Success: %d, Errors: %d, Consistent: %s",
                totalProcessed, successCount, errorCount, isCountingConsistent());
    }

    // Utility methods for checking if there are grouped errors (for UI)
    public boolean hasMissingTemplates() {
        return !missingTemplates.isEmpty();
    }

    public boolean hasMissingResumes() {
        return !missingResumes.isEmpty();
    }

    // Standard getters and setters
    public int getTotalProcessed() { return totalProcessed; }
    public void setTotalProcessed(int totalProcessed) { this.totalProcessed = totalProcessed; }

    public int getSuccessCount() { return successCount; }
    public void setSuccessCount(int successCount) { this.successCount = successCount; }

    public int getErrorCount() { return errorCount; }
    public void setErrorCount(int errorCount) { this.errorCount = errorCount; }

    public List<String> getErrors() { return errors; }
    public void setErrors(List<String> errors) { this.errors = errors; }

    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }

    public List<MissingTemplate> getMissingTemplates() { return missingTemplates; }
    public void setMissingTemplates(List<MissingTemplate> missingTemplates) { this.missingTemplates = missingTemplates; }

    public List<MissingResume> getMissingResumes() { return missingResumes; }
    public void setMissingResumes(List<MissingResume> missingResumes) { this.missingResumes = missingResumes; }

    public String getHelpText() { return helpText; }
    public void setHelpText(String helpText) { this.helpText = helpText; }

    /**
     * Inner class representing a missing template grouped by role.
     * This is used for UI display organization, not for counting logic.
     */
    public static class MissingTemplate {
        private String role;              // The role name (e.g., "FSE", "Backend")
        private String expectedPath;      // Where the template file should be located
        private String suggestion;        // How to fix this issue
        private List<String> affectedEmails; // All email addresses affected by this missing template

        // Standard getters and setters
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }

        public String getExpectedPath() { return expectedPath; }
        public void setExpectedPath(String expectedPath) { this.expectedPath = expectedPath; }

        public String getSuggestion() { return suggestion; }
        public void setSuggestion(String suggestion) { this.suggestion = suggestion; }

        public List<String> getAffectedEmails() { return affectedEmails; }
        public void setAffectedEmails(List<String> affectedEmails) { this.affectedEmails = affectedEmails; }
    }

    /**
     * Inner class representing a missing resume grouped by role.
     * This is used for UI display organization, not for counting logic.
     */
    public static class MissingResume {
        private String role;              // The role name (e.g., "FSE", "Backend")
        private String expectedPath;      // Where the resume file should be located
        private String suggestion;        // How to fix this issue
        private List<String> affectedEmails; // All email addresses affected by this missing resume

        // Standard getters and setters
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }

        public String getExpectedPath() { return expectedPath; }
        public void setExpectedPath(String expectedPath) { this.expectedPath = expectedPath; }

        public String getSuggestion() { return suggestion; }
        public void setSuggestion(String suggestion) { this.suggestion = suggestion; }

        public List<String> getAffectedEmails() { return affectedEmails; }
        public void setAffectedEmails(List<String> affectedEmails) { this.affectedEmails = affectedEmails; }
    }
}