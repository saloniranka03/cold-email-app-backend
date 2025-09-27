package com.coldemail.service;

import com.google.api.client.util.Base64;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Draft;
import com.google.api.services.gmail.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Updated Gmail service that works with user sessions instead of global credentials
 */
@Service
public class UpdatedGmailService {

    private static final Logger logger = LoggerFactory.getLogger(UpdatedGmailService.class);

    @Autowired
    private AuthService authService;

    /**
     * Creates a Gmail draft with session-based authentication
     */
    public String createDraftWithCustomAttachmentName(String sessionId, String to, String subject,
                                                      String bodyText, Path attachmentPath,
                                                      String attachmentName) throws MessagingException, IOException {

        logger.info("Creating draft email to: {}, subject: {}", to, subject);

        try {
            // Get Gmail service for user session
            Gmail gmail = authService.createGmailService(sessionId);
            String senderEmail = authService.getUserEmail(sessionId);

            // Create email message
            MimeMessage email = createEmailWithAttachment(to, senderEmail, subject, bodyText,
                    attachmentPath, attachmentName);

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
     * Backward compatibility method
     */
    public String createDraft(String sessionId, String to, String subject, String bodyText,
                              Path attachmentPath) throws MessagingException, IOException {
        String attachmentName = attachmentPath != null ? attachmentPath.getFileName().toString() : null;
        return createDraftWithCustomAttachmentName(sessionId, to, subject, bodyText,
                attachmentPath, attachmentName);
    }

    /**
     * Creates a MIME email message with attachment using custom attachment name.
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
     * Gets the authenticated user's email address using session
     */
    public String getUserEmail(String sessionId) throws IOException {
        logger.debug("Retrieving authenticated user email for session: {}", sessionId);
        try {
            String email = authService.getUserEmail(sessionId);
            if (email == null) {
                throw new IOException("Invalid session or user not authenticated");
            }
            logger.info("Authenticated user email: {}", email);
            return email;
        } catch (Exception e) {
            logger.error("Failed to retrieve user email: {}", e.getMessage(), e);
            throw e;
        }
    }
}