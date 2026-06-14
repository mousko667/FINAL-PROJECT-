package com.oct.invoicesystem.domain.user.service;

import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.department.repository.DepartmentRepository;
import com.oct.invoicesystem.domain.user.dto.UserCreateRequest;
import com.oct.invoicesystem.domain.user.dto.UserImportResultDTO;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Bulk CSV import/export of users (P11-16). Export emits one row per user (no passwords);
 * import is create-only — each data row creates a new user via {@link UserService#createUser}
 * (so it inherits all validation, password encoding and role wiring) or is rejected and reported.
 *
 * <p>CSV columns (header, in this order):
 * {@code username,email,firstName,lastName,preferredLang,employeeId,departmentCode,approvalLimit,isActive,roles}
 * where {@code roles} is a {@code ;}-separated list and {@code departmentCode} references a
 * department by its code (not UUID). Imported users get a random, undisclosed password and must
 * use the forgot-password flow to set their own — no password is ever present in the CSV.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserCsvService {

    static final String[] HEADERS = {
            "username", "email", "firstName", "lastName", "preferredLang",
            "employeeId", "departmentCode", "approvalLimit", "isActive", "roles"
    };

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserService userService;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;

    // ─── Export ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true) // keep the session open while iterating lazy userRoles
    public ByteArrayInputStream exportUsersToCsv() {
        Map<UUID, String> deptCodeById = departmentRepository.findAll().stream()
                .collect(Collectors.toMap(Department::getId, Department::getCode));

        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", HEADERS)).append("\r\n");

        for (User u : userRepository.findAll()) {
            String roles = u.getUserRoles().stream()
                    .map(ur -> ur.getRole().getName())
                    .collect(Collectors.joining(";"));
            String deptCode = u.getDepartmentId() == null ? "" : deptCodeById.getOrDefault(u.getDepartmentId(), "");
            String[] cells = {
                    nz(u.getUsername()), nz(u.getEmail()), nz(u.getFirstName()), nz(u.getLastName()),
                    nz(u.getPreferredLang()), nz(u.getEmployeeId()), deptCode,
                    u.getApprovalLimit() == null ? "" : u.getApprovalLimit().toPlainString(),
                    String.valueOf(u.isActive()), roles
            };
            sb.append(Arrays.stream(cells).map(UserCsvService::escape).collect(Collectors.joining(",")))
                    .append("\r\n");
        }
        return new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    // ─── Import ────────────────────────────────────────────────────────────

    public UserImportResultDTO importUsersFromCsv(MultipartFile file) {
        List<List<String>> rows;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            rows = parse(reader);
        } catch (IOException e) {
            throw new com.oct.invoicesystem.shared.exception.ValidationException("user.import.unreadable_file");
        }

        if (rows.isEmpty()) {
            return new UserImportResultDTO(0, 0, 0, List.of());
        }

        // Row 0 is the header (line 1). Data rows start at line 2.
        List<String> header = rows.get(0).stream().map(s -> s.trim().toLowerCase()).toList();
        List<UserImportResultDTO.RowError> errors = new ArrayList<>();
        int created = 0;
        int dataRows = rows.size() - 1;

        for (int i = 1; i < rows.size(); i++) {
            int line = i + 1; // 1-based, header = line 1
            List<String> row = rows.get(i);
            String username = cell(header, row, "username");
            try {
                if (row.stream().allMatch(c -> c == null || c.isBlank())) {
                    continue; // skip fully blank lines (don't count as failures)
                }
                createUserFromRow(header, row);
                created++;
            } catch (Exception ex) {
                errors.add(new UserImportResultDTO.RowError(line, username,
                        ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
            }
        }

        // Blank lines were skipped from both created and failed; recompute the effective total.
        int effectiveTotal = created + errors.size();
        log.info("CSV user import: {} data rows, {} created, {} failed", dataRows, created, errors.size());
        return new UserImportResultDTO(effectiveTotal, created, errors.size(), errors);
    }

    private void createUserFromRow(List<String> header, List<String> row) {
        String username = require(header, row, "username");
        String email = require(header, row, "email");
        String firstName = require(header, row, "firstName");
        String lastName = require(header, row, "lastName");
        String preferredLang = orDefault(cell(header, row, "preferredLang"), "fr");
        String employeeId = blankToNull(cell(header, row, "employeeId"));
        String deptCode = blankToNull(cell(header, row, "departmentCode"));
        String approvalRaw = blankToNull(cell(header, row, "approvalLimit"));
        String rolesRaw = blankToNull(cell(header, row, "roles"));

        if (userRepository.existsByUsername(username)) {
            throw new com.oct.invoicesystem.shared.exception.ValidationException(
                    "Username already exists: " + username);
        }
        if (userRepository.existsByEmail(email)) {
            throw new com.oct.invoicesystem.shared.exception.ValidationException(
                    "Email already exists: " + email);
        }

        UUID departmentId = null;
        if (deptCode != null) {
            Department dept = departmentRepository.findByCode(deptCode).orElseThrow(() ->
                    new com.oct.invoicesystem.shared.exception.ValidationException(
                            "Unknown department code: " + deptCode));
            departmentId = dept.getId();
        }

        BigDecimal approvalLimit = null;
        if (approvalRaw != null) {
            try {
                approvalLimit = new BigDecimal(approvalRaw);
            } catch (NumberFormatException nfe) {
                throw new com.oct.invoicesystem.shared.exception.ValidationException(
                        "Invalid approvalLimit: " + approvalRaw);
            }
        }

        List<String> roles = rolesRaw == null ? List.of()
                : Arrays.stream(rolesRaw.split(";")).map(String::trim).filter(s -> !s.isBlank()).toList();

        // create-only: random undisclosed password → user must use forgot-password to set their own.
        String tempPassword = randomPassword();
        userService.createUser(new UserCreateRequest(
                username, email, tempPassword, firstName, lastName, preferredLang,
                employeeId, departmentId, approvalLimit, roles));
    }

    // ─── CSV helpers ─────────────────────────────────────────────────────────

    /** Minimal RFC-4180 parser: handles quoted fields, escaped quotes (""), and embedded commas/newlines. */
    static List<List<String>> parse(BufferedReader reader) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean rowHasContent = false;
        int ch;
        while ((ch = reader.read()) != -1) {
            char c = (char) ch;
            if (inQuotes) {
                if (c == '"') {
                    reader.mark(1);
                    int next = reader.read();
                    if (next == '"') {
                        field.append('"'); // escaped quote
                    } else {
                        inQuotes = false;
                        if (next != -1) reader.reset();
                    }
                } else {
                    field.append(c);
                }
            } else {
                switch (c) {
                    case '"' -> inQuotes = true;
                    case ',' -> { current.add(field.toString()); field.setLength(0); rowHasContent = true; }
                    case '\r' -> { /* ignore, handle on \n */ }
                    case '\n' -> {
                        current.add(field.toString()); field.setLength(0);
                        rows.add(current); current = new ArrayList<>();
                        rowHasContent = false;
                    }
                    default -> { field.append(c); rowHasContent = true; }
                }
            }
        }
        // flush trailing field/row if the file didn't end with a newline
        if (field.length() > 0 || !current.isEmpty() || rowHasContent) {
            current.add(field.toString());
            rows.add(current);
        }
        return rows;
    }

    /** Escape a value for CSV output (quote if it contains comma, quote, CR or LF). */
    static String escape(String value) {
        if (value == null) return "";
        boolean needsQuote = value.contains(",") || value.contains("\"")
                || value.contains("\n") || value.contains("\r");
        if (!needsQuote) return value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String nz(String s) { return s == null ? "" : s; }
    private static String blankToNull(String s) { return (s == null || s.isBlank()) ? null : s.trim(); }
    private static String orDefault(String s, String def) { return (s == null || s.isBlank()) ? def : s.trim(); }

    private static String cell(List<String> header, List<String> row, String column) {
        int idx = header.indexOf(column.toLowerCase());
        if (idx < 0 || idx >= row.size()) return null;
        return row.get(idx);
    }

    private static String require(List<String> header, List<String> row, String column) {
        String v = cell(header, row, column);
        if (v == null || v.isBlank()) {
            throw new com.oct.invoicesystem.shared.exception.ValidationException(
                    "Missing required column value: " + column);
        }
        return v.trim();
    }

    private static String randomPassword() {
        // 24 url-safe-ish chars; never disclosed — the user resets via the forgot-password flow.
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%";
        StringBuilder sb = new StringBuilder(24);
        for (int i = 0; i < 24; i++) sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        return sb.toString();
    }
}
