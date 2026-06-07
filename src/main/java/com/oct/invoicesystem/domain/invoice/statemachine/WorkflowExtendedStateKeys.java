package com.oct.invoicesystem.domain.invoice.statemachine;

/**
 * Keys for variables stored in the state machine extended state.
 */
public final class WorkflowExtendedStateKeys {

    public static final String DEPARTMENT = "department";
    public static final String USER_ID = "USER_ID";
    public static final String AUTO_ARCHIVE = "AUTO_ARCHIVE";
    public static final String CHANGE_REASON = "CHANGE_REASON";

    private WorkflowExtendedStateKeys() {
    }
}
