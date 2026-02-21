package com.moveinsync.mdm.repository;

import com.moveinsync.mdm.entity.UpdateSchedule;
import com.moveinsync.mdm.enums.ScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UpdateScheduleRepository extends JpaRepository<UpdateSchedule, UUID> {

    List<UpdateSchedule> findByStatus(ScheduleStatus status);

    List<UpdateSchedule> findByStatusIn(List<ScheduleStatus> statuses);
}
