package com.oct.invoicesystem.shared.validation;

import java.time.LocalDate;

/**
 * Contrat minimal des requetes portant un couple emission / echeance, de sorte que
 * {@link DueDateNotBeforeIssueDateValidator} s'applique aux DTO de creation comme de mise a jour
 * sans recourir a la reflexion.
 */
public interface HasIssueAndDueDate {

    LocalDate issueDate();

    LocalDate dueDate();
}
