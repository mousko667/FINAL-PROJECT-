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
}
