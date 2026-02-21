package com.moveinsync.mdm.repository;

import com.moveinsync.mdm.entity.VersionCompatibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VersionCompatibilityRepository extends JpaRepository<VersionCompatibility, UUID> {

    List<VersionCompatibility> findByFromVersionCode(Integer fromVersionCode);

    boolean existsByFromVersionCodeAndToVersionCode(Integer fromVersionCode, Integer toVersionCode);
}
