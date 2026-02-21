package com.moveinsync.mdm.enums;

import java.util.List;
import java.util.Map;

/**
 * State machine for device update lifecycle.
 * Each state defines which states it can transition to.
 * FAILED can be reached from any active state.
 */
public enum UpdateState {

    SCHEDULED,
    NOTIFIED,
    DOWNLOAD_STARTED,
    DOWNLOAD_COMPLETED,
    INSTALLATION_STARTED,
    INSTALLATION_COMPLETED,
    FAILED;

    private static final Map<UpdateState, List<UpdateState>> VALID_TRANSITIONS = Map.of(
            SCHEDULED, List.of(NOTIFIED, FAILED),
            NOTIFIED, List.of(DOWNLOAD_STARTED, FAILED),
            DOWNLOAD_STARTED, List.of(DOWNLOAD_COMPLETED, FAILED),
            DOWNLOAD_COMPLETED, List.of(INSTALLATION_STARTED, FAILED),
            INSTALLATION_STARTED, List.of(INSTALLATION_COMPLETED, FAILED),
            INSTALLATION_COMPLETED, List.of(), // terminal state
            FAILED, List.of(SCHEDULED) // retry: go back to SCHEDULED
    );

    /**
     * Check if transitioning from this state to the target state is valid.
     */
    public boolean canTransitionTo(UpdateState target) {
        List<UpdateState> allowed = VALID_TRANSITIONS.get(this);
        return allowed != null && allowed.contains(target);
    }

    /**
     * Check if this is a terminal state (no further transitions allowed except
     * retry).
     */
    public boolean isTerminal() {
        return this == INSTALLATION_COMPLETED;
    }

    /**
     * Check if this state indicates an active (in-progress) update.
     */
    public boolean isActive() {
        return this != INSTALLATION_COMPLETED && this != FAILED;
    }
}
