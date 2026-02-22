package com.moveinsync.mdm.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gap #6: Unit tests for the UpdateState state machine.
 * Validates all valid transitions, blocks invalid transitions,
 * and verifies terminal/active state classification.
 */
@DisplayName("UpdateState — State Machine Tests")
class UpdateStateTest {

    // ==================== VALID TRANSITIONS ====================

    @Nested
    @DisplayName("Valid transitions")
    class ValidTransitions {

        @ParameterizedTest(name = "{0} → {1} should be valid")
        @CsvSource({
                "SCHEDULED, NOTIFIED",
                "SCHEDULED, FAILED",
                "NOTIFIED, DOWNLOAD_STARTED",
                "NOTIFIED, FAILED",
                "DOWNLOAD_STARTED, DOWNLOAD_COMPLETED",
                "DOWNLOAD_STARTED, FAILED",
                "DOWNLOAD_COMPLETED, INSTALLATION_STARTED",
                "DOWNLOAD_COMPLETED, FAILED",
                "INSTALLATION_STARTED, INSTALLATION_COMPLETED",
                "INSTALLATION_STARTED, FAILED",
                "FAILED, SCHEDULED"
        })
        void shouldAllowValidTransition(UpdateState from, UpdateState to) {
            assertTrue(from.canTransitionTo(to),
                    from + " → " + to + " should be a valid transition");
        }
    }

    // ==================== INVALID TRANSITIONS ====================

    @Nested
    @DisplayName("Invalid transitions")
    class InvalidTransitions {

        @ParameterizedTest(name = "{0} → {1} should be BLOCKED")
        @CsvSource({
                // Skip states (not sequential)
                "SCHEDULED, DOWNLOAD_STARTED",
                "SCHEDULED, DOWNLOAD_COMPLETED",
                "SCHEDULED, INSTALLATION_STARTED",
                "SCHEDULED, INSTALLATION_COMPLETED",
                "NOTIFIED, DOWNLOAD_COMPLETED",
                "NOTIFIED, INSTALLATION_STARTED",
                // Backwards transitions (no going back)
                "DOWNLOAD_STARTED, NOTIFIED",
                "DOWNLOAD_STARTED, SCHEDULED",
                "DOWNLOAD_COMPLETED, DOWNLOAD_STARTED",
                "INSTALLATION_STARTED, DOWNLOAD_COMPLETED",
                "INSTALLATION_COMPLETED, INSTALLATION_STARTED",
                // Terminal state — no transitions
                "INSTALLATION_COMPLETED, SCHEDULED",
                "INSTALLATION_COMPLETED, NOTIFIED",
                "INSTALLATION_COMPLETED, FAILED",
                // Failed → only SCHEDULED (retry), nothing else
                "FAILED, NOTIFIED",
                "FAILED, DOWNLOAD_STARTED",
                "FAILED, INSTALLATION_COMPLETED"
        })
        void shouldBlockInvalidTransition(UpdateState from, UpdateState to) {
            assertFalse(from.canTransitionTo(to),
                    from + " → " + to + " should be BLOCKED");
        }
    }

    // ==================== TERMINAL STATE ====================

    @Test
    @DisplayName("INSTALLATION_COMPLETED is terminal")
    void installationCompletedIsTerminal() {
        assertTrue(UpdateState.INSTALLATION_COMPLETED.isTerminal());
    }

    @ParameterizedTest(name = "{0} is NOT terminal")
    @EnumSource(value = UpdateState.class, names = "INSTALLATION_COMPLETED", mode = EnumSource.Mode.EXCLUDE)
    void nonTerminalStates(UpdateState state) {
        assertFalse(state.isTerminal(),
                state + " should NOT be terminal");
    }

    // ==================== ACTIVE STATE ====================

    @ParameterizedTest(name = "{0} is active")
    @EnumSource(value = UpdateState.class, names = { "INSTALLATION_COMPLETED",
            "FAILED" }, mode = EnumSource.Mode.EXCLUDE)
    void activeStates(UpdateState state) {
        assertTrue(state.isActive(),
                state + " should be an active state");
    }

    @Test
    @DisplayName("INSTALLATION_COMPLETED is not active")
    void completedNotActive() {
        assertFalse(UpdateState.INSTALLATION_COMPLETED.isActive());
    }

    @Test
    @DisplayName("FAILED is not active")
    void failedNotActive() {
        assertFalse(UpdateState.FAILED.isActive());
    }

    // ==================== DOWNGRADE TRANSITIONS ====================

    @Test
    @DisplayName("Self-transition is not allowed")
    void selfTransitionBlocked() {
        for (UpdateState state : UpdateState.values()) {
            assertFalse(state.canTransitionTo(state),
                    state + " → " + state + " (self) should be blocked");
        }
    }
}
