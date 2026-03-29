package com.oct.invoicesystem.config;

import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.statemachine.InvoiceEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;

import java.util.EnumSet;

/**
 * Invoice BAP state model and transitions ({@code docs/WORKFLOW.md} §3).
 * Department-aware branching for {@link InvoiceEvent#VALIDATE_N1} is implemented in {@link #departmentRequiresN2(StateContext)}
 * / {@link #departmentIsSingleLevel(StateContext)} — requires {@value #EXT_KEY_DEPARTMENT} in extended state.
 */
@Configuration
@EnableStateMachineFactory(name = "invoiceStateMachineFactory")
public class StateMachineConfig extends EnumStateMachineConfigurerAdapter<InvoiceStatus, InvoiceEvent> {

    public static final String EXT_KEY_DEPARTMENT = "department";

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
                .guard(StateMachineConfig::departmentRequiresN2)
                .and()
                .withExternal()
                .source(InvoiceStatus.EN_VALIDATION_N1)
                .target(InvoiceStatus.VALIDE)
                .event(InvoiceEvent.VALIDATE_N1)
                .guard(StateMachineConfig::departmentIsSingleLevel)
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

    private static boolean departmentRequiresN2(StateContext<InvoiceStatus, InvoiceEvent> ctx) {
        Object d = ctx.getExtendedState().getVariables().get(EXT_KEY_DEPARTMENT);
        return d instanceof Department dept && dept.isRequiresN2();
    }

    private static boolean departmentIsSingleLevel(StateContext<InvoiceStatus, InvoiceEvent> ctx) {
        Object d = ctx.getExtendedState().getVariables().get(EXT_KEY_DEPARTMENT);
        return d instanceof Department dept && !dept.isRequiresN2();
    }
}
