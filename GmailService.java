// backend/src/main/java/com/coldemail/service/GmailService.java
package com.coldemail.service;

import com.google.api.client.util.Base64;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Draft;
import com.google.api.services.gmail.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.activation.FileDataSource;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Service class for Gmail API operations.
 * Handles creation of email drafts with attachments and supports custom attachment naming.
 */
@Service
public class GmailService {

    private static final Logger logger = LoggerFactory.getLogger(GmailService.class);

    @Autowired
    @Qualifier("gmail")
    private Gmail gmail;

    /**
     * Creates a Gmail draft with the specified content and attachment using actual filename.
     * This method is kept for backward compatibility.
     *
     * @param to Recipient email address
     * @param subject Email subject
     * @param bodyText Email body content (HTML)
     * @param attachmentPath Path to resume attachment
     * @param senderEmail Sender email address
     * @return Created draft ID
     * @throws MessagingException if email creation fails
     * @throws IOException if Gmail API call fails
     */
    public String createDraft(String to, String subject, String bodyText,
                              Path attachmentPath, String senderEmail)
            throws MessagingException, IOException {

        // Use actual filename from path
        String attachmentName = attachmentPath != null ? attachmentPath.getFileName().toString() : null;
        return createDraftWithCustomAttachmentName(to, subject, bodyText, attachmentPath, attachmentName, senderEmail);
    }

    /**
     * Creates a Gmail draft with the specified content and attachment using custom attachment name.
     * This allows for standardized attachment naming regardless of actual file names.
     *
     * @param to Recipient email address
     * @param subject Email subject
     * @param bodyText Email body content (HTML)
     * @param attachmentPath Path to resume attachment (actual file to read)
     * @param attachmentName Custom name to use for the attachment in the email
     * @param senderEmail Sender email address
     * @return Created draft ID
     * @throws MessagingException if email creation fails
     * @throws IOException if Gmail API call fails
     */
    public String createDraftWithCustomAttachmentName(String to, String subject, String bodyText,
                                                      Path attachmentPath, String attachmentName, String senderEmail)
            throws MessagingException, IOException {

        logger.info("Creating draft email to: {}, subject: {}", to, subject);
        if (attachmentPath != null && attachmentName != null) {
            logger.info("Attachment: {} will be named as: {}", attachmentPath.getFileName(), attachmentName);
        }

        try {
            // Create email message with custom attachment name
            MimeMessage email = createEmailWithAttachment(to, senderEmail, subject, bodyText, attachmentPath, attachmentName);

            // Convert to Gmail message
            Message message = createMessageWithEmail(email);

            // Create draft
            Draft draft = new Draft();
            draft.setMessage(message);

            draft = gmail.users().drafts().create("me", draft).execute();

            logger.info("Draft created successfully with ID: {}", draft.getId());
            return draft.getId();

        } catch (Exception e) {
            logger.error("Failed to create draft for {}: {}", to, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Creates a MIME email message with attachment using actual filename.
     * This method is kept for backward compatibility.
     */
    private MimeMessage createEmailWithAttachment(String to, String from, String subject,
                                                  String bodyText, Path attachmentPath)
            throws MessagingException {
        String attachmentName = attachmentPath != null ? attachmentPath.getFileName().toString() : null;
        return createEmailWithAttachment(to, from, subject, bodyText, attachmentPath, attachmentName);
    }

    /**
     * Creates a MIME email message with attachment using custom attachment name.
     *
     * @param to Recipient email
     * @param from Sender email
     * @param subject Email subject
     * @param bodyText Email body (HTML)
     * @param attachmentPath Path to attachment file (file to read from disk)
     * @param attachmentName Name to use for attachment in email (can be different from actual filename)
     * @return MimeMessage object
     * @throws MessagingException if message creation fails
     */
    private MimeMessage createEmailWithAttachment(String to, String from, String subject,
                                                  String bodyText, Path attachmentPath, String attachmentName)
            throws MessagingException {

        logger.debug("Creating email message: from={}, to={}", from, to);

        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);

        MimeMessage email = new MimeMessage(session);
        email.setFrom(new InternetAddress(from));
        email.addRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(to));
        email.setSubject(subject);

        // Create multipart message
        Multipart multipart = new MimeMultipart();

        // Create body part
        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setContent(bodyText, "text/html; charset=UTF-8");
        multipart.addBodyPart(textPart);

        // Create attachment part
        if (attachmentPath != null && attachmentName != null) {
            logger.debug("Adding attachment: {} (file: {})", attachmentName, attachmentPath.getFileName());
            MimeBodyPart attachmentPart = new MimeBodyPart();
            DataSource source = new FileDataSource(attachmentPath.toFile());
            attachmentPart.setDataHandler(new DataHandler(source));

            // Use custom attachment name instead of actual filename
            attachmentPart.setFileName(attachmentName);

            multipart.addBodyPart(attachmentPart);
        }

        email.setContent(multipart);

        return email;
    }

    /**
     * Converts MimeMessage to Gmail Message format.
     *
     * @param emailContent The MimeMessage to convert
     * @return Gmail Message object
     * @throws MessagingException if conversion fails
     * @throws IOException if encoding fails
     */
    private Message createMessageWithEmail(MimeMessage emailContent)
            throws MessagingException, IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        emailContent.writeTo(buffer);
        byte[] bytes = buffer.toByteArray();
        String encodedEmail = Base64.encodeBase64URLSafeString(bytes);

        Message message = new Message();
        message.setRaw(encodedEmail);
        return message;
    }

    /**
     * Gets the authenticated user's email address.
     *
     * @return User's email address
     * @throws IOException if Gmail API call fails
     */
    public String getUserEmail() throws IOException {
        logger.debug("Retrieving authenticated user email");
        try {
            String email = gmail.users().getProfile("me").execute().getEmailAddress();
            logger.info("Authenticated user email: {}", email);
            return email;
        } catch (Exception e) {
            logger.error("Failed to retrieve user email: {}", e.getMessage(), e);
            throw e;
        }
    }
}