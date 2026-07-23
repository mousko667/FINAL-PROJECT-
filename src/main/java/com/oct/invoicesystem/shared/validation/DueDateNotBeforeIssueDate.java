package com.oct.invoicesystem.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Contrainte de classe : l'echeance d'une facture ne peut pas preceder son emission
 * ({@code dueDate >= issueDate}).
 *
 * <p>AUDIT-032 : le systeme acceptait une facture emise le 31/12/2026 et echue le 01/01/2026.
 * La verification est croisee (deux champs), elle ne peut donc pas etre portee par une annotation
 * de champ. L'erreur est en revanche rattachee au noeud {@code dueDate} par le validateur, de sorte
 * qu'elle remonte comme une {@code FieldError} exploitable par l'interface, et non comme une erreur
 * globale anonyme.</p>
 */
@Documented
@Constraint(validatedBy = DueDateNotBeforeIssueDateValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DueDateNotBeforeIssueDate {

    String message() default "validation.invoice.due_date_before_issue_date";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
