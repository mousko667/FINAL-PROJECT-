package com.oct.invoicesystem.domain.checklist.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * One answered line of a {@link ChecklistResponse} (B1): whether the validator checked the
 * corresponding {@link ChecklistTemplateItem}, plus an optional note.
 */
@Entity
@Table(name = "checklist_response_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChecklistResponseItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "response_id", nullable = false)
    private ChecklistResponse response;

    @Column(name = "template_item_id", nullable = false)
    private UUID templateItemId;

    @Column(nullable = false)
    @Builder.Default
    private boolean checked = false;

    @Column(length = 1000)
    private String note;
}
