package com.electricitysplit.repository;

import com.electricitysplit.entity.ScheduledTaskExecution;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScheduledTaskExecutionRepository extends JpaRepository<ScheduledTaskExecution, Long> {

    @Query("SELECT t FROM ScheduledTaskExecution t WHERE t.taskName = :taskName AND t.taskPeriod = :taskPeriod " +
           "ORDER BY t.createdAt DESC")
    List<ScheduledTaskExecution> findLatestByTaskNameAndPeriod(@Param("taskName") String taskName,
                                                                @Param("taskPeriod") String taskPeriod,
                                                                Pageable pageable);

    @Query("SELECT t FROM ScheduledTaskExecution t WHERE t.taskName = :taskName AND t.taskPeriod = :taskPeriod " +
           "AND t.status IN :statuses ORDER BY t.createdAt DESC")
    List<ScheduledTaskExecution> findByTaskNameAndPeriodAndStatusIn(@Param("taskName") String taskName,
                                                                     @Param("taskPeriod") String taskPeriod,
                                                                     @Param("statuses") List<ScheduledTaskExecution.TaskStatus> statuses,
                                                                     Pageable pageable);

    @Query("SELECT COUNT(t) > 0 FROM ScheduledTaskExecution t WHERE t.taskName = :taskName " +
           "AND t.taskPeriod = :taskPeriod AND t.status = :status")
    boolean existsByTaskNameAndPeriodAndStatus(@Param("taskName") String taskName,
                                                @Param("taskPeriod") String taskPeriod,
                                                @Param("status") ScheduledTaskExecution.TaskStatus status);

    @Query("SELECT t FROM ScheduledTaskExecution t WHERE t.taskName = :taskName AND t.isPaused = true " +
           "ORDER BY t.createdAt DESC")
    List<ScheduledTaskExecution> findPausedTasks(@Param("taskName") String taskName, Pageable pageable);

    @Query("SELECT t FROM ScheduledTaskExecution t WHERE t.taskName = :taskName AND t.status = 'RETRYING' " +
           "AND t.nextRetryAt <= :now ORDER BY t.nextRetryAt ASC")
    List<ScheduledTaskExecution> findTasksReadyForRetry(@Param("taskName") String taskName,
                                                         @Param("now") LocalDateTime now);
}
