package com.coldemail.model;

/**
 * Model class representing contact information extracted from Excel file.
 * Each row in the Excel file maps to one ContactInfo object.
 */
public class ContactInfo {
    private String name;
    private String emailId;
    private String role;

    // Constructors
    public ContactInfo() {}

    public ContactInfo(String name, String emailId, String role) {
        this.name = name;
        this.emailId = emailId;
        this.role = role;
    }

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmailId() { return emailId; }
    public void setEmailId(String emailId) { this.emailId = emailId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    @Override
    public String toString() {
        return "ContactInfo{name='" + name + "', emailId='" + emailId + "', role='" + role + "'}";
    }
}
