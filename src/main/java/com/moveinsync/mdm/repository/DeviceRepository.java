package com.moveinsync.mdm.repository;

import com.moveinsync.mdm.entity.Device;
import com.moveinsync.mdm.enums.DeviceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceRepository extends JpaRepository<Device, UUID> {

    Optional<Device> findByImei(String imei);

    boolean existsByImei(String imei);

    // Filtered device listing with optional parameters
    @Query("SELECT d FROM Device d WHERE " +
            "(:region IS NULL OR d.region = :region) AND " +
            "(:appVersion IS NULL OR d.appVersion = :appVersion) AND " +
            "(:clientTag IS NULL OR d.clientTag = :clientTag) AND " +
            "(:status IS NULL OR d.status = :status)")
    Page<Device> findWithFilters(
            @Param("region") String region,
            @Param("appVersion") String appVersion,
            @Param("clientTag") String clientTag,
            @Param("status") DeviceStatus status,
            Pageable pageable);

    // Devices inactive beyond a threshold
    List<Device> findByLastHeartbeatBeforeAndStatus(LocalDateTime threshold, DeviceStatus status);

    // Count by region
    @Query("SELECT d.region, COUNT(d) FROM Device d WHERE d.status = 'ACTIVE' GROUP BY d.region")
    List<Object[]> countByRegion();

    // Version distribution
    @Query("SELECT d.appVersion, COUNT(d) FROM Device d WHERE d.status = 'ACTIVE' GROUP BY d.appVersion ORDER BY COUNT(d) DESC")
    List<Object[]> countByAppVersion();

    // Total active count
    long countByStatus(DeviceStatus status);

    // Find devices matching an update schedule's targeting criteria
    @Query("SELECT d FROM Device d WHERE " +
            "d.appVersion = :appVersion AND " +
            "d.status = 'ACTIVE' AND " +
            "(:region IS NULL OR d.region = :region) AND " +
            "(:clientTag IS NULL OR d.clientTag = :clientTag)")
    List<Device> findTargetDevices(
            @Param("appVersion") String appVersion,
            @Param("region") String region,
            @Param("clientTag") String clientTag);
}
