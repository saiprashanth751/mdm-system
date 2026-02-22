package com.moveinsync.mdm.repository;

import com.moveinsync.mdm.entity.AppVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppVersionRepository extends JpaRepository<AppVersion, UUID> {

    Optional<AppVersion> findByVersionCode(Integer versionCode);

    boolean existsByVersionCode(Integer versionCode);

    List<AppVersion> findAllByOrderByVersionCodeDesc();

    List<AppVersion> findByCustomizationTag(String customizationTag);

    Optional<AppVersion> findByVersionName(String versionName);

    Optional<AppVersion> findTopByIsActiveTrueOrderByVersionCodeDesc();
}
