package com.oct.invoicesystem.domain.invoice.service;

import com.oct.invoicesystem.domain.invoice.dto.ArchiveFolderCreateRequest;
import com.oct.invoicesystem.domain.invoice.dto.ArchiveFolderDTO;
import com.oct.invoicesystem.domain.invoice.dto.ArchiveFolderUpdateRequest;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.support.AbstractPostgresIntegrationTest;
import com.oct.invoicesystem.shared.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ArchiveFolderIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private ArchiveFolderService archiveFolderService;

    @Autowired
    private UserRepository userRepository;

    private User admin;

    @BeforeEach
    void setUp() {
        // PROB-089: do NOT load the admin entity — its mfa_secret was encrypted with a different
        // AES key (2026-07-02 audit session) and is undecryptable under the test profile key
        // (AEADBadTagException). createFolder/updateFolder only need the createdBy FK, so a
        // lazy reference by id avoids loading — and decrypting — any column of the user row.
        UUID adminId = userRepository.findIdByUsername("admin")
                .orElseThrow(() -> new IllegalStateException("admin not seeded in dev DB"));
        admin = userRepository.getReferenceById(adminId); // lazy proxy, no column read/decryption
    }

    @Test
    void shouldCreateAndUpdateFolderTree() {
        // Create root
        ArchiveFolderCreateRequest req1 = new ArchiveFolderCreateRequest("Root 1", "Root Folder", null);
        ArchiveFolderDTO root1 = archiveFolderService.createFolder(req1, admin);
        assertThat(root1.id()).isNotNull();
        assertThat(root1.name()).isEqualTo("Root 1");

        // Create child
        ArchiveFolderCreateRequest req2 = new ArchiveFolderCreateRequest("Child 1", "Child Folder", root1.id());
        ArchiveFolderDTO child1 = archiveFolderService.createFolder(req2, admin);
        assertThat(child1.parentId()).isEqualTo(root1.id());

        // Update child
        ArchiveFolderUpdateRequest updateReq = new ArchiveFolderUpdateRequest("Child 1 Updated", "New Desc", root1.id());
        ArchiveFolderDTO updatedChild = archiveFolderService.updateFolder(child1.id(), updateReq, admin);
        assertThat(updatedChild.name()).isEqualTo("Child 1 Updated");

        // Verify tree
        List<ArchiveFolderDTO> tree = archiveFolderService.getFolderTree();
        assertThat(tree).extracting("id").contains(root1.id(), child1.id());
    }

    @Test
    void shouldEnforceMaxDepth() {
        ArchiveFolderDTO root = archiveFolderService.createFolder(new ArchiveFolderCreateRequest("Root", null, null), admin);
        ArchiveFolderDTO child = archiveFolderService.createFolder(new ArchiveFolderCreateRequest("Child", null, root.id()), admin);

        assertThatThrownBy(() -> archiveFolderService.createFolder(new ArchiveFolderCreateRequest("Grandchild", null, child.id()), admin))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("error.archive.folder.max_depth");
    }

    @Test
    void shouldPreventDuplicateNames() {
        archiveFolderService.createFolder(new ArchiveFolderCreateRequest("Folder A", null, null), admin);

        assertThatThrownBy(() -> archiveFolderService.createFolder(new ArchiveFolderCreateRequest("Folder A", null, null), admin))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("error.archive.folder.name_exists");
    }
}
