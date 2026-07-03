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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArchiveFolderServiceImpl implements ArchiveFolderService {

    private final ArchiveFolderRepository archiveFolderRepository;
    private final InvoiceRepository invoiceRepository;

    @Override
    @Transactional
    public ArchiveFolderDTO createFolder(ArchiveFolderCreateRequest request, User user) {
        ArchiveFolder parent = null;
        if (request.parentId() != null) {
            parent = archiveFolderRepository.findById(request.parentId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.archive.folder.not_found"));
            if (parent.getParent() != null) {
                throw new ValidationException("error.archive.folder.max_depth");
            }
            if (archiveFolderRepository.existsByNameAndParentId(request.name(), request.parentId())) {
                throw new ValidationException("error.archive.folder.name_exists");
            }
        } else {
            if (archiveFolderRepository.existsByNameAndParentIsNull(request.name())) {
                throw new ValidationException("error.archive.folder.name_exists");
            }
        }

        ArchiveFolder folder = ArchiveFolder.builder()
                .name(request.name())
                .description(request.description())
                .parent(parent)
                .createdBy(user)
                .build();

        folder = archiveFolderRepository.save(folder);
        return toDto(folder);
    }

    @Override
    @Transactional
    public ArchiveFolderDTO updateFolder(UUID id, ArchiveFolderUpdateRequest request, User user) {
        ArchiveFolder folder = archiveFolderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.archive.folder.not_found"));

        ArchiveFolder parent = null;
        if (request.parentId() != null) {
            if (request.parentId().equals(id)) {
                throw new ValidationException("error.archive.folder.invalid_parent");
            }
            parent = archiveFolderRepository.findById(request.parentId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.archive.folder.not_found"));
            if (parent.getParent() != null) {
                throw new ValidationException("error.archive.folder.max_depth");
            }
            // Cannot move a folder with children to a parent (would exceed depth 2)
            if (!archiveFolderRepository.findByParentId(id).isEmpty()) {
                throw new ValidationException("error.archive.folder.max_depth");
            }
            if (!request.parentId().equals(folder.getParent() != null ? folder.getParent().getId() : null) &&
                archiveFolderRepository.existsByNameAndParentId(request.name(), request.parentId())) {
                throw new ValidationException("error.archive.folder.name_exists");
            }
        } else {
            if ((folder.getParent() != null || !folder.getName().equals(request.name())) &&
                archiveFolderRepository.existsByNameAndParentIsNull(request.name())) {
                throw new ValidationException("error.archive.folder.name_exists");
            }
        }

        folder.setName(request.name());
        folder.setDescription(request.description());
        folder.setParent(parent);
        
        folder = archiveFolderRepository.save(folder);
        return toDto(folder);
    }

    @Override
    @Transactional
    public void deleteFolder(UUID id) {
        if (!archiveFolderRepository.existsById(id)) {
            throw new ResourceNotFoundException("error.archive.folder.not_found");
        }
        archiveFolderRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ArchiveFolderDTO> listFolders(UUID parentId) {
        List<ArchiveFolder> folders = parentId == null 
                ? archiveFolderRepository.findByParentIsNull() 
                : archiveFolderRepository.findByParentId(parentId);
        return folders.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ArchiveFolderDTO> getFolderTree() {
        // Return flat list: roots first, then all children
        List<ArchiveFolder> roots = archiveFolderRepository.findByParentIsNull();
        List<ArchiveFolderDTO> result = new java.util.ArrayList<>();
        for (ArchiveFolder root : roots) {
            result.add(toDto(root));
            List<ArchiveFolder> children = archiveFolderRepository.findByParentId(root.getId());
            for (ArchiveFolder child : children) {
                result.add(toDto(child));
            }
        }
        return result;
    }

    @Override
    @Transactional
    public void assignInvoiceToFolder(UUID invoiceId, UUID folderId, User user) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("error.invoice.not_found"));
                
        if (invoice.getStatus() != InvoiceStatus.ARCHIVE) {
            throw new ValidationException("error.archive.invoice.not_archived");
        }

        ArchiveFolder folder = null;
        if (folderId != null) {
            folder = archiveFolderRepository.findById(folderId)
                    .orElseThrow(() -> new ResourceNotFoundException("error.archive.folder.not_found"));
        }
        
        invoice.setFolder(folder);
        invoiceRepository.save(invoice);
    }

    private ArchiveFolderDTO toDto(ArchiveFolder folder) {
        long count = invoiceRepository.countByFolderId(folder.getId());
        return new ArchiveFolderDTO(
                folder.getId(),
                folder.getName(),
                folder.getDescription(),
                folder.getParent() != null ? folder.getParent().getId() : null,
                folder.getParent() != null ? folder.getParent().getName() : null,
                count,
                folder.getCreatedAt()
        );
    }
}
