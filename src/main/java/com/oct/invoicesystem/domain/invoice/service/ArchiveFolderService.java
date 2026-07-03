package com.oct.invoicesystem.domain.invoice.service;

import com.oct.invoicesystem.domain.invoice.dto.ArchiveFolderCreateRequest;
import com.oct.invoicesystem.domain.invoice.dto.ArchiveFolderDTO;
import com.oct.invoicesystem.domain.invoice.dto.ArchiveFolderUpdateRequest;
import com.oct.invoicesystem.domain.user.model.User;

import java.util.List;
import java.util.UUID;

public interface ArchiveFolderService {
    ArchiveFolderDTO createFolder(ArchiveFolderCreateRequest request, User user);
    ArchiveFolderDTO updateFolder(UUID id, ArchiveFolderUpdateRequest request, User user);
    void deleteFolder(UUID id);
    List<ArchiveFolderDTO> listFolders(UUID parentId);
    List<ArchiveFolderDTO> getFolderTree();
    void assignInvoiceToFolder(UUID invoiceId, UUID folderId, User user);
}
