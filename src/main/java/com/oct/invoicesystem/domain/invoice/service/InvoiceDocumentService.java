package com.oct.invoicesystem.domain.invoice.service;

import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceDocument;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceDocumentRepository;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.storage.service.MinioStorageService;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import com.oct.invoicesystem.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvoiceDocumentService {

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "image/tiff"
    );

    private final InvoiceRepository invoiceRepository;
    private final InvoiceDocumentRepository invoiceDocumentRepository;
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

        InvoiceDocument document = InvoiceDocument.builder()
                .invoice(invoice)
                .originalFilename(file.getOriginalFilename())
                .minioObjectKey(objectKey)
                .fileType(detectedMimeType)
                .fileSizeBytes(file.getSize())
                .checksumSha256(checksum)
                .uploadedBy(uploader)
                .build();

        return invoiceDocumentRepository.save(document);
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
     * Generates a pre-signed download URL for an invoice document.
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

    private String buildObjectKey(UUID invoiceId, String originalFilename) {
        String safeName = originalFilename == null ? "document.bin" : originalFilename;
        String normalizedName = new String(safeName.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)
                .replace(" ", "_");
        return "invoices/" + invoiceId + "/" + UUID.randomUUID() + "-" + normalizedName;
    }
}
