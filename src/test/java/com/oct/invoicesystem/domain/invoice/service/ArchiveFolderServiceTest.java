package com.oct.invoicesystem.domain.invoice.service;

import com.oct.invoicesystem.domain.invoice.dto.ArchiveFolderCreateRequest;
import com.oct.invoicesystem.domain.invoice.dto.ArchiveFolderDTO;
import com.oct.invoicesystem.domain.invoice.dto.ArchiveFolderUpdateRequest;
import com.oct.invoicesystem.domain.invoice.model.ArchiveFolder;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.ArchiveFolderRepository;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import com.oct.invoicesystem.shared.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArchiveFolderServiceTest {

    @Mock
    private ArchiveFolderRepository archiveFolderRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @InjectMocks
    private ArchiveFolderServiceImpl archiveFolderService;

    private User adminUser;
    private ArchiveFolder rootFolder;

    @BeforeEach
    void setUp() {
        adminUser = new User();
        adminUser.setId(UUID.randomUUID());

        rootFolder = ArchiveFolder.builder()
                .id(UUID.randomUUID())
                .name("Root Folder")
                .createdBy(adminUser)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void createFolder_happyPath() {
        ArchiveFolderCreateRequest req = new ArchiveFolderCreateRequest("New Folder", "Desc", null);
        when(archiveFolderRepository.existsByNameAndParentIsNull("New Folder")).thenReturn(false);
        when(archiveFolderRepository.save(any())).thenAnswer(inv -> {
            ArchiveFolder f = inv.getArgument(0);
            f.setId(UUID.randomUUID());
            f.setCreatedAt(Instant.now());
            return f;
        });

        ArchiveFolderDTO dto = archiveFolderService.createFolder(req, adminUser);

        assertThat(dto.name()).isEqualTo("New Folder");
        assertThat(dto.parentId()).isNull();
    }

    @Test
    void createFolder_duplicateName_throwsValidation() {
        ArchiveFolderCreateRequest req = new ArchiveFolderCreateRequest("Root Folder", "Desc", null);
        when(archiveFolderRepository.existsByNameAndParentIsNull("Root Folder")).thenReturn(true);

        assertThatThrownBy(() -> archiveFolderService.createFolder(req, adminUser))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("error.archive.folder.name_exists");
    }

    @Test
    void createFolder_maxDepth_throwsValidation() {
        ArchiveFolder parentFolder = ArchiveFolder.builder().id(UUID.randomUUID()).parent(rootFolder).build(); // Already level 1
        ArchiveFolderCreateRequest req = new ArchiveFolderCreateRequest("Child", null, parentFolder.getId());
        
        when(archiveFolderRepository.findById(parentFolder.getId())).thenReturn(Optional.of(parentFolder));

        assertThatThrownBy(() -> archiveFolderService.createFolder(req, adminUser))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("error.archive.folder.max_depth");
    }

    @Test
    void assignInvoiceToFolder_happyPath() {
        Invoice invoice = new Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setStatus(InvoiceStatus.ARCHIVE);

        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(archiveFolderRepository.findById(rootFolder.getId())).thenReturn(Optional.of(rootFolder));

        archiveFolderService.assignInvoiceToFolder(invoice.getId(), rootFolder.getId(), adminUser);

        verify(invoiceRepository).save(invoice);
        assertThat(invoice.getFolder()).isEqualTo(rootFolder);
    }

    @Test
    void assignInvoiceToFolder_notArchived_throwsValidation() {
        Invoice invoice = new Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setStatus(InvoiceStatus.BROUILLON);

        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> archiveFolderService.assignInvoiceToFolder(invoice.getId(), rootFolder.getId(), adminUser))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("error.archive.invoice.not_archived");
    }
}
