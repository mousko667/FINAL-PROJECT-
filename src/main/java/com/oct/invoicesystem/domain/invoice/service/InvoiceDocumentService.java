package com.oct.invoicesystem.domain.invoice.service;

import com.oct.invoicesystem.domain.invoice.dto.BulkUploadResultDTO;
import com.oct.invoicesystem.domain.invoice.dto.InvoiceDocumentDTO;
import com.oct.invoicesystem.domain.invoice.model.DocumentAccessLog;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceDocument;
import com.oct.invoicesystem.domain.invoice.repository.DocumentAccessLogRepository;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceDocumentRepository;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.storage.service.MinioStorageService;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import com.oct.invoicesystem.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceDocumentService {

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "image/tiff",
            // B8 (M3): structured XML invoices (Tika detects either of these for .xml content).
            "application/xml",
            "text/xml"
    );

    private final InvoiceRepository invoiceRepository;
    private final InvoiceDocumentRepository invoiceDocumentRepository;
    private final DocumentAccessLogRepository documentAccessLogRepository;
    private final UserRepository userRepository;
    private final MinioStorageService minioStorageService;
    private final Tika tika = new Tika();

    /**
     * Uploads a validated invoice document and stores metadata with SHA-256 checksum.
     *
     * @param invoiceId target invoice id
     * @param file uploaded file
     * @param actorId authenticated uploader id
     * @return created invoice document metadata
     * @throws Exception if storage operation fails
     */
    /**
     * Resolves the uploader from the authenticated username (lookup moved out of the
     * controller, P1-05) and delegates to {@link #upload(UUID, MultipartFile, UUID)}.
     *
     * @param invoiceId target invoice id
     * @param file uploaded file
     * @param username authenticated uploader username
     * @return created invoice document metadata
     * @throws ResourceNotFoundException if no user matches the username
     * @throws Exception if storage operation fails
     */
    @Transactional
    public InvoiceDocument upload(UUID invoiceId, MultipartFile file, String username) throws Exception {
        User uploader = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        return upload(invoiceId, file, uploader.getId());
    }

    @Transactional
    public InvoiceDocument upload(UUID invoiceId, MultipartFile file, UUID actorId) throws Exception {
        Invoice invoice = invoiceRepository.findByIdAndDeletedAtIsNull(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + invoiceId));
        User uploader = userRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + actorId));

        validateFileSize(file);
        byte[] content = readContent(file);
        String detectedMimeType = detectAndValidateMimeType(content);
        String checksum = computeSha256(content);
        String objectKey = buildObjectKey(invoiceId, file.getOriginalFilename());

        minioStorageService.upload(objectKey, content, detectedMimeType);

        // M9 versioning: if a current document with the same filename already exists on this invoice,
        // the new upload becomes the next version and the old one is marked superseded (history kept).
        int nextVersion = 1;
        InvoiceDocument previous = file.getOriginalFilename() == null ? null
                : invoiceDocumentRepository
                    .findFirstByInvoiceIdAndOriginalFilenameAndSupersededByDocumentIdIsNull(
                            invoiceId, file.getOriginalFilename())
                    .orElse(null);
        if (previous != null) {
            nextVersion = previous.getVersion() + 1;
        }

        InvoiceDocument document = InvoiceDocument.builder()
                .invoice(invoice)
                .originalFilename(file.getOriginalFilename())
                .minioObjectKey(objectKey)
                .fileType(detectedMimeType)
                .fileSizeBytes(file.getSize())
                .checksumSha256(checksum)
                .uploadedBy(uploader)
                .version(nextVersion)
                .build();

        InvoiceDocument saved = invoiceDocumentRepository.save(document);

        if (previous != null) {
            previous.setSupersededByDocumentId(saved.getId());
            invoiceDocumentRepository.save(previous);
        }
        return saved;
    }

    /**
     * Bulk-uploads multiple documents to one invoice (P11-48 / REQ-05). Each file is validated and
     * stored independently: valid files are persisted and returned, invalid ones (bad type, oversize,
     * unreadable) are skipped and reported per-file — valid files are NOT rolled back when others fail.
     *
     * <p>The invoice and uploader are resolved once up front (a missing invoice/user fails the whole
     * batch). Each file is then delegated to {@link #upload(UUID, MultipartFile, UUID)}, whose own
     * {@code @Transactional} commits that file's row before the next is attempted.
     *
     * @param invoiceId target invoice id
     * @param files uploaded files (must be non-empty)
     * @param username authenticated uploader username
     * @return per-file outcome report
     * @throws ValidationException if the file list is empty
     * @throws ResourceNotFoundException if the invoice or uploader does not exist
     */
    public BulkUploadResultDTO uploadMultiple(UUID invoiceId, List<MultipartFile> files, String username) {
        if (files == null || files.isEmpty()) {
            throw new ValidationException("At least one file is required");
        }
        // Resolve once: a bad invoice/user is a batch-level error, not a per-file one.
        User uploader = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        invoiceRepository.findByIdAndDeletedAtIsNull(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + invoiceId));

        List<InvoiceDocumentDTO> uploaded = new ArrayList<>();
        List<BulkUploadResultDTO.FileError> errors = new ArrayList<>();

        for (MultipartFile file : files) {
            String filename = file == null ? null : file.getOriginalFilename();
            try {
                InvoiceDocument document = upload(invoiceId, file, uploader.getId());
                uploaded.add(toDto(document));
            } catch (ValidationException ex) {
                // Authored, user-safe messages (bad type / oversize) — safe to return.
                errors.add(new BulkUploadResultDTO.FileError(filename, ex.getMessage()));
            } catch (Exception ex) {
                // Log the real cause server-side; return a fixed message so internals
                // (e.g. storage host/port) are never leaked to the API client.
                log.error("Bulk upload failed for file '{}' on invoice {}", filename, invoiceId, ex);
                errors.add(new BulkUploadResultDTO.FileError(filename, "Upload failed due to an internal error"));
            }
        }

        return new BulkUploadResultDTO(files.size(), uploaded.size(), errors.size(), uploaded, errors);
    }

    private InvoiceDocumentDTO toDto(InvoiceDocument document) {
        return new InvoiceDocumentDTO(
                document.getId(),
                document.getInvoice() != null ? document.getInvoice().getId() : null,
                document.getOriginalFilename(),
                document.getFileType(),
                document.getFileSizeBytes(),
                document.getChecksumSha256(),
                document.getUploadedBy() != null ? document.getUploadedBy().getId() : null,
                document.getUploadedAt(),
                document.getVersion(),
                document.getSupersededByDocumentId()
        );
    }

    /**
     * Lists all documents attached to an invoice.
     *
     * @param invoiceId invoice id
     * @return document list
     */
    @Transactional(readOnly = true)
    public List<InvoiceDocument> listByInvoice(UUID invoiceId) {
        return invoiceDocumentRepository.findByInvoiceId(invoiceId);
    }

    /**
     * Generates a pre-signed download URL for an invoice document after re-verifying SHA-256 integrity.
     *
     * @param invoiceId invoice id
     * @param documentId document id
     * @return pre-signed URL
     * @throws Exception if URL generation fails
     */
    @Transactional(readOnly = true)
    public String generateDownloadUrl(UUID invoiceId, UUID documentId) throws Exception {
        InvoiceDocument document = invoiceDocumentRepository.findByIdAndInvoiceId(documentId, invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + documentId));
        verifyStoredChecksum(document);
        return minioStorageService.generateDownloadUrl(document.getMinioObjectKey());
    }

    /**
     * Generates a pre-signed download URL AND records an append-only access-log entry
     * (P11-50 / REQ-16). The access log captures who downloaded which document, when, and from
     * where — a tamper-evident trail distinct from the generic HTTP audit log. Integrity is
     * re-checked against MinIO before the URL is issued or the access log is written.
     *
     * @param invoiceId invoice id
     * @param documentId document id
     * @param username authenticated accessor username (may be null/unknown)
     * @param ipAddress requester IP (nullable)
     * @param userAgent requester user-agent (nullable)
     * @return pre-signed URL
     * @throws Exception if URL generation fails
     */
    @Transactional
    public String generateDownloadUrlAndLog(UUID invoiceId, UUID documentId, String username,
                                            String ipAddress, String userAgent) throws Exception {
        InvoiceDocument document = invoiceDocumentRepository.findByIdAndInvoiceId(documentId, invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + documentId));

        verifyStoredChecksum(document);

        User accessor = username == null ? null : userRepository.findByUsername(username).orElse(null);

        documentAccessLogRepository.save(DocumentAccessLog.builder()
                .document(document)
                .invoiceId(invoiceId)
                .accessedBy(accessor)
                .action("DOWNLOAD")
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build());

        return minioStorageService.generateDownloadUrl(document.getMinioObjectKey());
    }

    private void validateFileSize(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("Document file is required");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ValidationException("Document exceeds 10MB size limit");
        }
    }

    private byte[] readContent(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (Exception exception) {
            throw new ValidationException("Unable to read uploaded document");
        }
    }

    private String detectAndValidateMimeType(byte[] content) {
        try {
            String mimeType = tika.detect(content);
            if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
                throw new ValidationException("Unsupported MIME type: " + mimeType);
            }
            return mimeType;
        } catch (ValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ValidationException("Unable to detect MIME type");
        }
    }

    private String computeSha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            return HexFormat.of().formatHex(hash);
        } catch (Exception exception) {
            throw new ValidationException("Unable to compute document checksum");
        }
    }

    /**
     * Re-fetches the object from MinIO and compares its SHA-256 to the checksum stored at upload time.
     *
     * @param document invoice document metadata including expected checksum
     * @throws Exception if MinIO read fails
     * @throws ValidationException if the recomputed checksum does not match (corruption or tampering)
     */
    private void verifyStoredChecksum(InvoiceDocument document) throws Exception {
        byte[] content = minioStorageService.download(document.getMinioObjectKey());
        String actual = computeSha256(content);
        String expected = document.getChecksumSha256();
        if (expected == null || !actual.equalsIgnoreCase(expected)) {
            log.error("Document integrity mismatch for id={} objectKey={} expected={} actual={}",
                    document.getId(), document.getMinioObjectKey(), expected, actual);
            throw new ValidationException("error.document.integrity_mismatch");
        }
    }

    private String buildObjectKey(UUID invoiceId, String originalFilename) {
        String safeName = originalFilename == null ? "document.bin" : originalFilename;
        String normalizedName = new String(safeName.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)
                .replace(" ", "_");
        return "invoices/" + invoiceId + "/" + UUID.randomUUID() + "-" + normalizedName;
    }
}
