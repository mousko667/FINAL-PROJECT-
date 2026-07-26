package com.oct.invoicesystem.domain.invoice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oct.invoicesystem.domain.invoice.dto.ArchiveFolderCreateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization coverage for {@code ArchiveFolderController.createFolder}. The folder-management
 * surface (create/rename/delete) was opened from ADMIN-only to the roles that classify archived
 * invoices — ADMIN, DAF and ASSISTANT_COMPTABLE — since the AA owns archiving but previously could
 * not create the folders to classify into. SUPPLIER stays refused.
 *
 * <p>This test asserts the {@code @PreAuthorize} decision only, not persistence: an allowed role
 * gets past the guard (any status other than 403 — here 404, because these mock usernames are not
 * seeded in the H2 test DB, so {@code SecurityHelper.currentUser} can't resolve the createdBy FK),
 * whereas a forbidden role is stopped at the guard with 403 before the service is ever reached.
 * Full context, {@code test} profile, {@code @Transactional} → rollback.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("ArchiveFolderControllerAuthTest")
class ArchiveFolderControllerAuthTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private String body(String name) throws Exception {
        return objectMapper.writeValueAsString(new ArchiveFolderCreateRequest(name, null, null));
    }

    @Test
    @DisplayName("Create folder: ASSISTANT_COMPTABLE passes the guard (not 403 — the AA classifies archived invoices)")
    @WithMockUser(username = "aa", roles = "ASSISTANT_COMPTABLE")
    void createFolder_asAa_notForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/archive/folders")
                        .contentType(MediaType.APPLICATION_JSON).content(body("AA Folder")))
                .andExpect(status().is(not(403)));
    }

    @Test
    @DisplayName("Create folder: DAF passes the guard (not 403)")
    @WithMockUser(username = "daf", roles = "DAF")
    void createFolder_asDaf_notForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/archive/folders")
                        .contentType(MediaType.APPLICATION_JSON).content(body("DAF Folder")))
                .andExpect(status().is(not(403)));
    }

    @Test
    @DisplayName("Create folder: ADMIN passes the guard (not 403 — still allowed)")
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createFolder_asAdmin_notForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/archive/folders")
                        .contentType(MediaType.APPLICATION_JSON).content(body("Admin Folder")))
                .andExpect(status().is(not(403)));
    }

    @Test
    @DisplayName("Create folder: SUPPLIER → 403 (external role cannot manage folders)")
    @WithMockUser(username = "supplier", roles = "SUPPLIER")
    void createFolder_asSupplier_forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/archive/folders")
                        .contentType(MediaType.APPLICATION_JSON).content(body("Nope")))
                .andExpect(status().isForbidden());
    }
}
