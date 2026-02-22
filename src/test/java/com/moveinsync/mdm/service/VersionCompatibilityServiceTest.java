package com.moveinsync.mdm.service;

import com.moveinsync.mdm.dto.response.CompatibilityCheckResponse;
import com.moveinsync.mdm.entity.VersionCompatibility;
import com.moveinsync.mdm.repository.VersionCompatibilityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Gap #6: Unit tests for BFS upgrade path validation in
 * VersionCompatibilityService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VersionCompatibilityService — BFS Upgrade Path Tests")
class VersionCompatibilityServiceTest {

    @Mock
    private VersionCompatibilityRepository compatibilityRepository;

    @InjectMocks
    private VersionCompatibilityService service;

    // ==================== DIRECT PATH ====================

    @Nested
    @DisplayName("Direct upgrade paths")
    class DirectPaths {

        @Test
        @DisplayName("Should find direct upgrade path 100 → 110")
        void directPathExists() {
            VersionCompatibility rule = VersionCompatibility.builder()
                    .fromVersionCode(100)
                    .toVersionCode(110)
                    .requiresIntermediate(false)
                    .build();
            when(compatibilityRepository.findAll()).thenReturn(List.of(rule));

            CompatibilityCheckResponse result = service.checkUpgradePath(100, 110);

            assertTrue(result.isAllowed());
            assertNotNull(result.getUpgradePath());
            assertEquals(List.of(100, 110), result.getUpgradePath());
        }

        @Test
        @DisplayName("Should find multi-hop path 100 → 105 → 110")
        void multiHopPath() {
            List<VersionCompatibility> rules = List.of(
                    VersionCompatibility.builder()
                            .fromVersionCode(100).toVersionCode(105)
                            .requiresIntermediate(false).build(),
                    VersionCompatibility.builder()
                            .fromVersionCode(105).toVersionCode(110)
                            .requiresIntermediate(false).build());
            when(compatibilityRepository.findAll()).thenReturn(rules);

            CompatibilityCheckResponse result = service.checkUpgradePath(100, 110);

            assertTrue(result.isAllowed());
            assertEquals(List.of(100, 105, 110), result.getUpgradePath());
        }
    }

    // ==================== INTERMEDIATE VERSIONS ====================

    @Nested
    @DisplayName("Intermediate version enforcement (Gap #12)")
    class IntermediateVersions {

        @Test
        @DisplayName("Should enforce intermediate version in path")
        void intermediateEnforced() {
            VersionCompatibility rule = VersionCompatibility.builder()
                    .fromVersionCode(100)
                    .toVersionCode(120)
                    .requiresIntermediate(true)
                    .intermediateVersionCode(110)
                    .build();
            VersionCompatibility intermediateRule = VersionCompatibility.builder()
                    .fromVersionCode(110)
                    .toVersionCode(120)
                    .requiresIntermediate(false)
                    .build();
            when(compatibilityRepository.findAll()).thenReturn(List.of(rule, intermediateRule));

            CompatibilityCheckResponse result = service.checkUpgradePath(100, 120);

            assertTrue(result.isAllowed());
            assertTrue(result.getUpgradePath().contains(110),
                    "Path should include intermediate version 110");
            assertEquals(List.of(100, 110, 120), result.getUpgradePath());
        }

        @Test
        @DisplayName("Single requiresIntermediate rule creates path through intermediate")
        void singleRuleCreatesPathThroughIntermediate() {
            // A single rule with requiresIntermediate=true creates both edges:
            // 100→110 and 110→120. BFS finds path 100→110→120.
            VersionCompatibility rule = VersionCompatibility.builder()
                    .fromVersionCode(100)
                    .toVersionCode(120)
                    .requiresIntermediate(true)
                    .intermediateVersionCode(110)
                    .build();
            when(compatibilityRepository.findAll()).thenReturn(List.of(rule));

            CompatibilityCheckResponse result = service.checkUpgradePath(100, 120);

            assertTrue(result.isAllowed());
            assertEquals(List.of(100, 110, 120), result.getUpgradePath(),
                    "Path must go through intermediate version 110");
            assertTrue(result.isRequiresIntermediate(),
                    "Should indicate intermediate is required");
        }
    }

    // ==================== NO PATH ====================

    @Nested
    @DisplayName("No valid paths")
    class NoValidPaths {

        @Test
        @DisplayName("Should return not allowed when no path exists")
        void noPathExists() {
            when(compatibilityRepository.findAll()).thenReturn(List.of());

            CompatibilityCheckResponse result = service.checkUpgradePath(100, 200);

            assertFalse(result.isAllowed());
        }

        @Test
        @DisplayName("Should return not allowed for disconnected graph")
        void disconnectedGraph() {
            List<VersionCompatibility> rules = List.of(
                    VersionCompatibility.builder()
                            .fromVersionCode(100).toVersionCode(105)
                            .requiresIntermediate(false).build(),
                    VersionCompatibility.builder()
                            .fromVersionCode(200).toVersionCode(210)
                            .requiresIntermediate(false).build());
            when(compatibilityRepository.findAll()).thenReturn(rules);

            CompatibilityCheckResponse result = service.checkUpgradePath(100, 210);

            assertFalse(result.isAllowed());
        }
    }

    // ==================== DOWNGRADE PREVENTION ====================

    @Nested
    @DisplayName("Downgrade prevention")
    class DowngradePrevention {

        @Test
        @DisplayName("BFS should not find path for downgrade even if rule exists in reverse")
        void noDowngradePath() {
            // Service returns early at the downgrade check (to <= from)
            // before BFS or findAll() is ever called
            CompatibilityCheckResponse result = service.checkUpgradePath(120, 110);

            assertFalse(result.isAllowed(),
                    "Downgrade 120 → 110 should not be allowed");
        }
    }
}
