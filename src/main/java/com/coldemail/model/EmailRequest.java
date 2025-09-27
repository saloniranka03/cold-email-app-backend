package com.coldemail.model;

/**
 * Request model for email processing containing user information
 * and file processing details.
 */
public class EmailRequest {

    private String fullName;
    private String phoneNumber;
    private String linkedInUrl;
    private String templatesFolderPath;

    // Constructors
    public EmailRequest() {}

    public EmailRequest(String fullName, String phoneNumber, String linkedInUrl, String templatesFolderPath) {
        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
        this.linkedInUrl = linkedInUrl;
        this.templatesFolderPath = templatesFolderPath;
    }

    // Getters and Setters
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getLinkedInUrl() { return linkedInUrl; }
    public void setLinkedInUrl(String linkedInUrl) { this.linkedInUrl = linkedInUrl; }

    public String getTemplatesFolderPath() { return templatesFolderPath; }
    public void setTemplatesFolderPath(String templatesFolderPath) { this.templatesFolderPath = templatesFolderPath; }
}