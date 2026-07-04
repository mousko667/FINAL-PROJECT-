package com.oct.invoicesystem.domain.invoice.service;

import com.oct.invoicesystem.domain.invoice.dto.BulkUploadResultDTO;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceDocument;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceDocumentRepository;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.storage.service.MinioStorageService;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import com.oct.invoicesystem.shared.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceDocumentServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private InvoiceDocumentRepository invoiceDocumentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MinioStorageService minioStorageService;
    @Mock
    private com.oct.invoicesystem.domain.invoice.repository.DocumentAccessLogRepository documentAccessLogRepository;

    @InjectMocks
    private InvoiceDocumentService service;

    private UUID invoiceId;
    private UUID actorId;
    private Invoice invoice;
    private User user;

    @BeforeEach
    void setUp() {
        invoiceId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        invoice = Invoice.builder().id(invoiceId).build();
        user = new User();
        user.setId(actorId);
        user.setUsername("assistant");
        user.setPassword("x");
        user.setActive(true);
    }

    @Test
    void upload_rejectsUnsupportedMimeType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "note.txt",
                "text/plain",
                "hello".getBytes(StandardCharsets.UTF_8)
        );
        when(invoiceRepository.findByIdAndDeletedAtIsNull(invoiceId)).thenReturn(Optional.of(invoice));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(user));

        assertThrows(ValidationException.class, () -> service.upload(invoiceId, file, actorId));
    }

    @Test
    void upload_computesChecksumAndSaves() throws Exception {
        byte[] pdfBytes = "%PDF-1.4\nsample".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "invoice.pdf",
                "application/pdf",
                pdfBytes
        );

        when(invoiceRepository.findByIdAndDeletedAtIsNull(invoiceId)).thenReturn(Optional.of(invoice));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(user));
        when(minioStorageService.upload(any(), any(), eq("application/pdf"))).thenAnswer(i -> i.getArgument(0));
        when(invoiceDocumentRepository.save(any(InvoiceDocument.class))).thenAnswer(i -> i.getArgument(0));

        InvoiceDocument result = service.upload(invoiceId, file, actorId);

        String expectedChecksum = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdfBytes));
        assertEquals(expectedChecksum, result.getChecksumSha256());

        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(minioStorageService).upload(any(), payloadCaptor.capture(), eq("application/pdf"));
        assertEquals(pdfBytes.length, payloadCaptor.getValue().length);
    }

    @Test
    void upload_rejectsFileOverSizeLimit() {
        byte[] tooLarge = new byte[10 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile("file", "big.pdf", "application/pdf", tooLarge);
        when(invoiceRepository.findByIdAndDeletedAtIsNull(invoiceId)).thenReturn(Optional.of(invoice));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(user));

        assertThrows(ValidationException.class, () -> service.upload(invoiceId, file, actorId));
    }

    @Test
    void uploadByUsername_resolvesUserAndDelegates() throws Exception {
        byte[] pdfBytes = "%PDF-1.4\nsample".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "invoice.pdf", "application/pdf", pdfBytes);

        when(userRepository.findByUsername("assistant")).thenReturn(Optional.of(user));
        when(invoiceRepository.findByIdAndDeletedAtIsNull(invoiceId)).thenReturn(Optional.of(invoice));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(user));
        when(minioStorageService.upload(any(), any(), eq("application/pdf"))).thenAnswer(i -> i.getArgument(0));
        when(invoiceDocumentRepository.save(any(InvoiceDocument.class))).thenAnswer(i -> i.getArgument(0));

        InvoiceDocument result = service.upload(invoiceId, file, "assistant");

        assertEquals(actorId, result.getUploadedBy().getId());
        verify(userRepository).findByUsername("assistant");
    }

    @Test
    void uploadByUsername_unknownUser_throwsResourceNotFound() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "invoice.pdf", "application/pdf",
                "%PDF-1.4".getBytes(StandardCharsets.UTF_8));
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.upload(invoiceId, file, "ghost"));
    }

    @Test
    void uploadMultiple_storesValidFilesAndReportsInvalidOnes() throws Exception {
        MockMultipartFile good1 = new MockMultipartFile("files", "a.pdf", "application/pdf",
                "%PDF-1.4\nA".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile bad = new MockMultipartFile("files", "note.txt", "text/plain",
                "hello".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile good2 = new MockMultipartFile("files", "b.pdf", "application/pdf",
                "%PDF-1.4\nB".getBytes(StandardCharsets.UTF_8));

        when(userRepository.findByUsername("assistant")).thenReturn(Optional.of(user));
        when(invoiceRepository.findByIdAndDeletedAtIsNull(invoiceId)).thenReturn(Optional.of(invoice));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(user));
        when(minioStorageService.upload(any(), any(), eq("application/pdf"))).thenAnswer(i -> i.getArgument(0));
        when(invoiceDocumentRepository.save(any(InvoiceDocument.class))).thenAnswer(i -> i.getArgument(0));

        BulkUploadResultDTO result = service.uploadMultiple(
                invoiceId, java.util.List.of(good1, bad, good2), "assistant");

        assertEquals(3, result.totalFiles());
        assertEquals(2, result.uploaded());
        assertEquals(1, result.failed());
        assertEquals(2, result.documents().size());
        assertEquals(1, result.errors().size());
        assertEquals("note.txt", result.errors().get(0).filename());
    }

    @Test
    void upload_secondVersionOfSameFile_incrementsVersionAndSupersedes() throws Exception {
        byte[] pdf = "%PDF-1.4\nv2".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "invoice.pdf", "application/pdf", pdf);

        InvoiceDocument prior = InvoiceDocument.builder()
                .id(UUID.randomUUID()).invoice(invoice).originalFilename("invoice.pdf").version(1).build();

        when(invoiceRepository.findByIdAndDeletedAtIsNull(invoiceId)).thenReturn(Optional.of(invoice));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(user));
        when(minioStorageService.upload(any(), any(), eq("application/pdf"))).thenAnswer(i -> i.getArgument(0));
        when(invoiceDocumentRepository
                .findFirstByInvoiceIdAndOriginalFilenameAndSupersededByDocumentIdIsNull(invoiceId, "invoice.pdf"))
                .thenReturn(Optional.of(prior));
        when(invoiceDocumentRepository.save(any(InvoiceDocument.class))).thenAnswer(i -> {
            InvoiceDocument d = i.getArgument(0);
            if (d.getId() == null) d.setId(UUID.randomUUID());
            return d;
        });

        InvoiceDocument v2 = service.upload(invoiceId, file, actorId);

        assertEquals(2, v2.getVersion());
        // the prior version must now point to the new one
        assertEquals(v2.getId(), prior.getSupersededByDocumentId());
    }

    @Test
    void uploadMultiple_emptyList_throwsValidation() {
        assertThrows(ValidationException.class,
                () -> service.uploadMultiple(invoiceId, java.util.List.of(), "assistant"));
    }

    @Test
    void download_verifiesChecksum() throws Exception {
        UUID docId = UUID.randomUUID();
        byte[] content = "%PDF-1.4\nintegrity".getBytes(StandardCharsets.UTF_8);
        String checksum = java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(content));
        InvoiceDocument doc = InvoiceDocument.builder()
                .id(docId)
                .invoice(invoice)
                .minioObjectKey("invoices/k")
                .checksumSha256(checksum)
                .build();
        when(invoiceDocumentRepository.findByIdAndInvoiceId(docId, invoiceId)).thenReturn(Optional.of(doc));
        when(minioStorageService.download("invoices/k")).thenReturn(content);
        when(minioStorageService.generateDownloadUrl("invoices/k")).thenReturn("https://signed-url");
        when(userRepository.findByUsername("assistant")).thenReturn(Optional.of(user));

        String url = service.generateDownloadUrlAndLog(invoiceId, docId, "assistant", "10.0.0.1", "JUnit");

        assertEquals("https://signed-url", url);
        verify(minioStorageService).download("invoices/k");
    }

    @Test
    void download_checksumMismatch_throwsValidation() throws Exception {
        UUID docId = UUID.randomUUID();
        byte[] stored = "%PDF-1.4\noriginal".getBytes(StandardCharsets.UTF_8);
        byte[] tampered = "%PDF-1.4\ntampered".getBytes(StandardCharsets.UTF_8);
        String checksum = java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(stored));
        InvoiceDocument doc = InvoiceDocument.builder()
                .id(docId)
                .invoice(invoice)
                .minioObjectKey("invoices/k")
                .checksumSha256(checksum)
                .build();
        when(invoiceDocumentRepository.findByIdAndInvoiceId(docId, invoiceId)).thenReturn(Optional.of(doc));
        when(minioStorageService.download("invoices/k")).thenReturn(tampered);

        ValidationException ex = assertThrows(ValidationException.class,
                () -> service.generateDownloadUrlAndLog(invoiceId, docId, "assistant", "10.0.0.1", "JUnit"));
        assertEquals("error.document.integrity_mismatch", ex.getMessage());
        verify(documentAccessLogRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void generateDownloadUrlAndLog_writesAccessLogEntry() throws Exception {
        UUID docId = UUID.randomUUID();
        byte[] content = "%PDF-1.4\nlog".getBytes(StandardCharsets.UTF_8);
        String checksum = java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(content));
        InvoiceDocument doc = InvoiceDocument.builder()
                .id(docId).invoice(invoice).minioObjectKey("invoices/k").checksumSha256(checksum).build();
        when(invoiceDocumentRepository.findByIdAndInvoiceId(docId, invoiceId)).thenReturn(Optional.of(doc));
        when(minioStorageService.download("invoices/k")).thenReturn(content);
        when(minioStorageService.generateDownloadUrl("invoices/k")).thenReturn("https://signed-url");
        when(userRepository.findByUsername("assistant")).thenReturn(Optional.of(user));

        String url = service.generateDownloadUrlAndLog(invoiceId, docId, "assistant", "10.0.0.1", "JUnit");

        assertEquals("https://signed-url", url);
        org.mockito.ArgumentCaptor<com.oct.invoicesystem.domain.invoice.model.DocumentAccessLog> captor =
                org.mockito.ArgumentCaptor.forClass(com.oct.invoicesystem.domain.invoice.model.DocumentAccessLog.class);
        verify(documentAccessLogRepository).save(captor.capture());
        assertEquals(invoiceId, captor.getValue().getInvoiceId());
        assertEquals("DOWNLOAD", captor.getValue().getAction());
        assertEquals(user.getId(), captor.getValue().getAccessedBy().getId());
        assertEquals("10.0.0.1", captor.getValue().getIpAddress());
    }
}
