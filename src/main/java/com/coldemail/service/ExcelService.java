package com.coldemail.service;

import com.coldemail.model.ContactInfo;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Service class for handling Excel file operations.
 * Processes uploaded Excel files and extracts contact information.
 */
@Service
public class ExcelService {

    private static final Logger logger = LoggerFactory.getLogger(ExcelService.class);

    /**
     * Processes the uploaded Excel file and extracts contact information.
     * Expected columns: Name, Email Id, Role
     *
     * @param file The uploaded Excel file
     * @return List of ContactInfo objects
     * @throws IOException if file cannot be processed
     */
    public List<ContactInfo> processExcelFile(MultipartFile file) throws IOException {
        logger.info("Processing Excel file: {}", file.getOriginalFilename());

        List<ContactInfo> contacts = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();

            // Skip header row
            if (rowIterator.hasNext()) {
                rowIterator.next();
            }

            int rowNumber = 1;
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                rowNumber++;

                try {
                    ContactInfo contact = extractContactInfo(row, rowNumber);
                    if (contact != null) {
                        contacts.add(contact);
                        logger.debug("Extracted contact: {}", contact);
                    }
                } catch (Exception e) {
                    logger.warn("Error processing row {}: {}", rowNumber, e.getMessage());
                }
            }
        }

        logger.info("Successfully processed {} contacts from Excel file", contacts.size());
        return contacts;
    }

    /**
     * Extracts contact information from a single Excel row.
     *
     * @param row The Excel row to process
     * @param rowNumber The row number for logging purposes
     * @return ContactInfo object or null if row is invalid
     */
    private ContactInfo extractContactInfo(Row row, int rowNumber) {
        if (row == null) return null;

        String name = getCellValueAsString(row.getCell(0));
        String emailId = getCellValueAsString(row.getCell(1));
        String role = getCellValueAsString(row.getCell(2));

        // Email and Role are mandatory
        if (emailId == null || emailId.trim().isEmpty() || role == null || role.trim().isEmpty()) {
            logger.warn("Row {}: Missing required fields (Email: {}, Role: {})", rowNumber, emailId, role);
            return null;
        }

        return new ContactInfo(name != null ? name.trim() : "", emailId.trim(), role.trim());
    }

    /**
     * Safely extracts string value from Excel cell.
     *
     * @param cell The Excel cell
     * @return String value or null if cell is empty
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) return null;

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return String.valueOf((long) cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return null;
        }
    }
}
