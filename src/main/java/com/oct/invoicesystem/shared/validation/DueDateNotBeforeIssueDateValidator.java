package com.oct.invoicesystem.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;

/**
 * Verifie {@code dueDate >= issueDate} (AUDIT-032).
 *
 * <p>Une date absente n'est pas traitee ici : sa presence releve de {@code @NotNull} sur le champ,
 * et signaler deux fois la meme omission brouillerait le message rendu a l'utilisateur.</p>
 */
public class DueDateNotBeforeIssueDateValidator
        implements ConstraintValidator<DueDateNotBeforeIssueDate, HasIssueAndDueDate> {

    @Override
    public boolean isValid(HasIssueAndDueDate request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }
        LocalDate issueDate = request.issueDate();
        LocalDate dueDate = request.dueDate();
        if (issueDate == null || dueDate == null) {
            return true;
        }
        if (!dueDate.isBefore(issueDate)) {
            return true;
        }

        // Rattache la violation au champ dueDate : elle remonte alors comme FieldError, que le
        // GlobalExceptionHandler sait deja resoudre et nommer.
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("dueDate")
                .addConstraintViolation();
        return false;
    }
}
