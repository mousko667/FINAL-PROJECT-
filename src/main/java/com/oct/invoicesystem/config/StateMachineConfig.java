package com.oct.invoicesystem.config;

import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.statemachine.InvoiceEvent;
import com.oct.invoicesystem.domain.workflow.guard.DepartmentTransitionGuard;
import com.oct.invoicesystem.domain.workflow.guard.DocumentRequiredGuard;
import com.oct.invoicesystem.domain.workflow.guard.RejectionReasonGuard;
import com.oct.invoicesystem.domain.workflow.guard.ResubmissionVersionGuard;
import com.oct.invoicesystem.domain.workflow.guard.RoleMatchGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;

import java.util.EnumSet;

/**
 * Invoice BAP state model and transitions ({@code docs/WORKFLOW.md} §3).
 * N1 routing uses {@link DepartmentTransitionGuard} and {@code Department} in extended state
 * ({@link com.oct.invoicesystem.domain.invoice.statemachine.WorkflowExtendedStateKeys#DEPARTMENT}).
 */
@Configuration
@EnableStateMachineFactory(name = "invoiceStateMachineFactory")
@RequiredArgsConstructor
public class StateMachineConfig extends EnumStateMachineConfigurerAdapter<InvoiceStatus, InvoiceEvent> {

    private final DepartmentTransitionGuard departmentTransitionGuard;
    private final DocumentRequiredGuard documentRequiredGuard;
    private final RoleMatchGuard roleMatchGuard;
    private final RejectionReasonGuard rejectionReasonGuard;
    private final ResubmissionVersionGuard resubmissionVersionGuard;

    @Override
    public void configure(StateMachineConfigurationConfigurer<InvoiceStatus, InvoiceEvent> config) throws Exception {
        config.withConfiguration().autoStartup(false);
    }

    @Override
    public void configure(StateMachineStateConfigurer<InvoiceStatus, InvoiceEvent> states) throws Exception {
        states.withStates()
                .initial(InvoiceStatus.BROUILLON)
                .end(InvoiceStatus.ARCHIVE)
                .states(EnumSet.allOf(InvoiceStatus.class));
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<InvoiceStatus, InvoiceEvent> transitions) throws Exception {
        transitions
                .withExternal()
                .source(InvoiceStatus.BROUILLON)
                .target(InvoiceStatus.SOUMIS)
                .event(InvoiceEvent.SUBMIT)
                .guard(documentRequiredGuard)
                .and()
                .withExternal()
                .source(InvoiceStatus.SOUMIS)
                .target(InvoiceStatus.EN_CONTROLE_AA)
                .event(InvoiceEvent.ASSIGN_AA)
                .guard(roleMatchGuard)
                .and()
                .withExternal()
                .source(InvoiceStatus.EN_CONTROLE_AA)
                .target(InvoiceStatus.EN_VALIDATION_N1)
                .event(InvoiceEvent.ASSIGN_REVIEWER)
                .guard(roleMatchGuard)
                .and()
                .withExternal()
                .source(InvoiceStatus.EN_CONTROLE_AA)
                .target(InvoiceStatus.REJETE)
                .event(InvoiceEvent.REJECT)
                .guard(ctx -> rejectionReasonGuard.evaluate(ctx) && roleMatchGuard.evaluate(ctx))
                .and()
                .withExternal()
                .source(InvoiceStatus.EN_VALIDATION_N1)
                .target(InvoiceStatus.EN_VALIDATION_N2)
                .event(InvoiceEvent.VALIDATE_N1)
                .guard(ctx -> departmentTransitionGuard.requiresN2(ctx) && roleMatchGuard.evaluate(ctx))
                .and()
                .withExternal()
                .source(InvoiceStatus.EN_VALIDATION_N1)
                .target(InvoiceStatus.VALIDE)
                .event(InvoiceEvent.VALIDATE_N1)
                .guard(ctx -> departmentTransitionGuard.isSingleLevel(ctx) && roleMatchGuard.evaluate(ctx))
                .and()
                .withExternal()
                .source(InvoiceStatus.EN_VALIDATION_N2)
                .target(InvoiceStatus.VALIDE)
                .event(InvoiceEvent.VALIDATE_N2)
                .guard(roleMatchGuard)
                .and()
                .withExternal()
                .source(InvoiceStatus.VALIDE)
                .target(InvoiceStatus.BON_A_PAYER)
                .event(InvoiceEvent.BON_A_PAYER)
                .guard(roleMatchGuard)
                .and()
                .withExternal()
                .source(InvoiceStatus.BON_A_PAYER)
                .target(InvoiceStatus.PAYE)
                .event(InvoiceEvent.RECORD_PAYMENT)
                .and()
                .withExternal()
                .source(InvoiceStatus.PAYE)
                .target(InvoiceStatus.ARCHIVE)
                .event(InvoiceEvent.ARCHIVE)
                .and()
                .withExternal()
                .source(InvoiceStatus.EN_VALIDATION_N1)
                .target(InvoiceStatus.REJETE)
                .event(InvoiceEvent.REJECT)
                .guard(ctx -> rejectionReasonGuard.evaluate(ctx) && roleMatchGuard.evaluate(ctx))
                .and()
                .withExternal()
                .source(InvoiceStatus.EN_VALIDATION_N2)
                .target(InvoiceStatus.REJETE)
                .event(InvoiceEvent.REJECT)
                .guard(ctx -> rejectionReasonGuard.evaluate(ctx) && roleMatchGuard.evaluate(ctx))
                .and()
                .withExternal()
                .source(InvoiceStatus.VALIDE)
                .target(InvoiceStatus.REJETE)
                .event(InvoiceEvent.REJECT)
                .guard(ctx -> rejectionReasonGuard.evaluate(ctx) && roleMatchGuard.evaluate(ctx))
                .and()
                .withExternal()
                .source(InvoiceStatus.REJETE)
                .target(InvoiceStatus.SOUMIS)
                .event(InvoiceEvent.RESUBMIT)
                .guard(resubmissionVersionGuard);
    }
}
