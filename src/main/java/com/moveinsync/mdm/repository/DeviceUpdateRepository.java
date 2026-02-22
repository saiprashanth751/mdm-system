package com.moveinsync.mdm.repository;

import com.moveinsync.mdm.entity.DeviceUpdate;
import com.moveinsync.mdm.enums.UpdateState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceUpdateRepository extends JpaRepository<DeviceUpdate, UUID> {

    List<DeviceUpdate> findByScheduleId(UUID scheduleId);

    List<DeviceUpdate> findByDeviceId(UUID deviceId);

    // Find pending (active) updates for a specific device
    @Query("SELECT du FROM DeviceUpdate du WHERE du.device.id = :deviceId AND du.currentState NOT IN ('INSTALLATION_COMPLETED', 'FAILED')")
    List<DeviceUpdate> findPendingByDeviceId(@Param("deviceId") UUID deviceId);

    // Find the latest pending update for a device (for heartbeat response)
    @Query("SELECT du FROM DeviceUpdate du JOIN du.schedule s WHERE du.device.id = :deviceId AND du.currentState = 'SCHEDULED' ORDER BY du.createdAt DESC")
    List<DeviceUpdate> findScheduledByDeviceId(@Param("deviceId") UUID deviceId);

    // Count by state for a schedule (for dashboard/audit)
    long countByScheduleIdAndCurrentState(UUID scheduleId, UpdateState state);

    // Count total for a schedule
    long countByScheduleId(UUID scheduleId);

    // Find by schedule and device (unique)
    Optional<DeviceUpdate> findByScheduleIdAndDeviceId(UUID scheduleId, UUID deviceId);

    // Find device updates by schedule and state (for scheduled rollout trigger)
    List<DeviceUpdate> findByScheduleIdAndCurrentState(UUID scheduleId, UpdateState state);

    // Find failed updates eligible for retry
    List<DeviceUpdate> findByCurrentStateAndRetryCountLessThan(UpdateState state, int maxRetries);

    // Count permanently failed updates for schedule lifecycle
    long countByScheduleIdAndCurrentStateAndRetryCountGreaterThanEqual(
            UUID scheduleId, UpdateState state, int retryCount);
}
