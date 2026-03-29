package com.oct.invoicesystem.config;

import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.statemachine.InvoiceEvent;
import com.oct.invoicesystem.domain.workflow.guard.DepartmentTransitionGuard;
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
                .and()
                .withExternal()
                .source(InvoiceStatus.SOUMIS)
                .target(InvoiceStatus.EN_VALIDATION_N1)
                .event(InvoiceEvent.ASSIGN_REVIEWER)
                .and()
                .withExternal()
                .source(InvoiceStatus.EN_VALIDATION_N1)
                .target(InvoiceStatus.EN_VALIDATION_N2)
                .event(InvoiceEvent.VALIDATE_N1)
                .guard(departmentTransitionGuard::requiresN2)
                .and()
                .withExternal()
                .source(InvoiceStatus.EN_VALIDATION_N1)
                .target(InvoiceStatus.VALIDE)
                .event(InvoiceEvent.VALIDATE_N1)
                .guard(departmentTransitionGuard::isSingleLevel)
                .and()
                .withExternal()
                .source(InvoiceStatus.EN_VALIDATION_N2)
                .target(InvoiceStatus.VALIDE)
                .event(InvoiceEvent.VALIDATE_N2)
                .and()
                .withExternal()
                .source(InvoiceStatus.VALIDE)
                .target(InvoiceStatus.BON_A_PAYER)
                .event(InvoiceEvent.BON_A_PAYER)
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
                .and()
                .withExternal()
                .source(InvoiceStatus.EN_VALIDATION_N2)
                .target(InvoiceStatus.REJETE)
                .event(InvoiceEvent.REJECT)
                .and()
                .withExternal()
                .source(InvoiceStatus.VALIDE)
                .target(InvoiceStatus.REJETE)
                .event(InvoiceEvent.REJECT)
                .and()
                .withExternal()
                .source(InvoiceStatus.REJETE)
                .target(InvoiceStatus.SOUMIS)
                .event(InvoiceEvent.RESUBMIT);
    }
}
