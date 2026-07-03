package com.oct.invoicesystem.domain.invoice.repository;

import com.oct.invoicesystem.domain.invoice.model.ArchiveFolder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ArchiveFolderRepository extends JpaRepository<ArchiveFolder, UUID> {
    List<ArchiveFolder> findByParentIsNull();
    List<ArchiveFolder> findByParentId(UUID parentId);
    boolean existsByNameAndParentId(String name, UUID parentId);
    boolean existsByNameAndParentIsNull(String name);
}
