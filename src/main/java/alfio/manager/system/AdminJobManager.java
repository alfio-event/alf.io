/**
 * This file is part of alf.io.
 *
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */
package alfio.manager.system;

import alfio.manager.system.AdminJobExecutor.JobName;
import alfio.model.result.ErrorCode;
import alfio.model.result.Result;
import alfio.model.system.AdminJobSchedule;
import alfio.repository.system.AdminJobQueueRepository;
import alfio.util.ClockProvider;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static alfio.model.system.AdminJobSchedule.Status.EXECUTED;
import static java.util.stream.Collectors.*;

@Transactional
@Slf4j
public class AdminJobManager {

    static final int MAX_ATTEMPTS = 17; // will retry for approximately 36h
    private static final Set<String> ADMIN_JOBS = EnumSet.complementOf(EnumSet.of(JobName.EXECUTE_EXTENSION))
        .stream()
        .map(Enum::name)
        .collect(toSet());
    private static final Set<String> EXTENSIONS_JOB = Set.of(JobName.EXECUTE_EXTENSION.name());
    private final Map<JobName, List<AdminJobExecutor>> executorsByJobId;
    private final AdminJobQueueRepository adminJobQueueRepository;
    private final TransactionTemplate nestedTransactionTemplate;
    private final Set<String> executedStatuses;
    private final Set<String> notExecutedStatuses;
    private final ClockProvider clockProvider;

    public AdminJobManager(List<AdminJobExecutor> jobExecutors,
                           AdminJobQueueRepository adminJobQueueRepository,
                           PlatformTransactionManager transactionManager,
                           ClockProvider clockProvider) {

        this.executorsByJobId = jobExecutors.stream()
            .flatMap(je -> je.getJobNames().stream().map(n -> Pair.of(n, je)))
            .collect(groupingBy(Pair::getLeft, () -> new EnumMap<>(JobName.class), mapping(Pair::getValue, toList())));
        this.adminJobQueueRepository = adminJobQueueRepository;
        this.nestedTransactionTemplate = new TransactionTemplate(transactionManager, new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_NESTED));
        var executed = EnumSet.of(EXECUTED);
        this.executedStatuses = executed.stream().map(Enum::name).collect(toSet());
        this.notExecutedStatuses = EnumSet.complementOf(executed).stream().map(Enum::name).collect(toSet());
        this.clockProvider = clockProvider;
    }

    @Scheduled(fixedDelay = 1000L)
    void processPendingExtensionRetry() {
        log.trace("Processing pending extensions retry");
        processPendingExtensionRetry(ZonedDateTime.now(clockProvider.getClock()));
        log.trace("done processing pending extensions retry");
    }

    // internal method invoked by tests
    void processPendingExtensionRetry(ZonedDateTime timestamp) {
        internalProcessPendingSchedules(adminJobQueueRepository.loadPendingSchedules(EXTENSIONS_JOB, timestamp));
    }

    @Scheduled(fixedDelay = 60 * 1000)
    void processPendingRequests() {
        log.trace("Processing pending requests");
        internalProcessPendingSchedules(adminJobQueueRepository.loadPendingSchedules(ADMIN_JOBS, ZonedDateTime.now(clockProvider.getClock())));
        log.trace("done processing pending requests");
    }

    private void internalProcessPendingSchedules(List<AdminJobSchedule> pendingSchedules) {
        pendingSchedules.stream()
            .map(this::processPendingRequest)
            .filter(p -> !p.getRight().isEmpty())
            .forEach(scheduleWithResults -> {
                var schedule = scheduleWithResults.getLeft();
                var partitionedResults = scheduleWithResults.getRight().stream().collect(Collectors.partitioningBy(Result::isSuccess));
                if(!partitionedResults.get(false).isEmpty()) {
                    partitionedResults.get(false).forEach(r -> log.warn("Processing failed for {}: {}", schedule.getJobName(), r.getErrors()));
                    if (schedule.getJobName() != JobName.EXECUTE_EXTENSION || schedule.getAttempts() > MAX_ATTEMPTS) {
                        adminJobQueueRepository.updateSchedule(schedule.getId(), AdminJobSchedule.Status.FAILED, ZonedDateTime.now(clockProvider.getClock()), Map.of());
                    } else {
                        var nextExecution = getNextExecution(schedule.getAttempts());
                        var extensionName = schedule.getMetadata().get("extensionName");
                        log.debug("scheduling failed extension {} to be executed at {}", extensionName, nextExecution);
                        adminJobQueueRepository.scheduleRetry(schedule.getId(), nextExecution);
                    }
                } else {
                    partitionedResults.get(true).forEach(result -> {
                        if(result.getData() != null) {
                            log.trace("Message from {}: {}", schedule.getJobName(), result.getData());
                        }
                    });
                    adminJobQueueRepository.updateSchedule(schedule.getId(), EXECUTED, ZonedDateTime.now(clockProvider.getClock()), Map.of());
                }
            });
    }

    static ZonedDateTime getNextExecution(int currentAttempt) {
        return ZonedDateTime.now(ClockProvider.clock())
            .plusSeconds((long) Math.pow(2, currentAttempt + 1D));
    }

    @Scheduled(cron = "#{environment.acceptsProfiles('dev') ? '0 * * * * *' : '0 0 0 * * *'}")
    void cleanupExpiredRequests() {
        log.trace("Cleanup expired requests");
        ZonedDateTime now = ZonedDateTime.now(clockProvider.getClock());
        int deleted = adminJobQueueRepository.removePastSchedules(now.minusDays(1), executedStatuses);
        if(deleted > 0) {
            log.trace("Deleted {} executed jobs", deleted);
        }
        deleted = adminJobQueueRepository.removePastSchedules(now.minusWeeks(1), notExecutedStatuses);
        if(deleted > 0) {
            log.warn("Deleted {} NOT executed jobs", deleted);
        }
    }

    public boolean scheduleExecution(JobName jobName, Map<String, Object> metadata) {
        return scheduleExecution(jobName, metadata, ZonedDateTime.now(clockProvider.getClock()).truncatedTo(ChronoUnit.MINUTES));
    }

    public boolean scheduleExecution(JobName jobName, Map<String, Object> metadata, ZonedDateTime executionTime) {
        return executionScheduler(jobName, metadata, executionTime).apply(adminJobQueueRepository);
    }

    private Pair<AdminJobSchedule, List<Result<String>>> processPendingRequest(AdminJobSchedule schedule) {
        return Pair.of(schedule, executorsByJobId.getOrDefault(schedule.getJobName(), List.of())
            .stream()
            .map(s -> {
                try {
                    return Result.success(nestedTransactionTemplate.execute(status -> s.process(schedule)));
                } catch (Exception ex) {
                    return Result.<String>error(ErrorCode.custom("exception", ex.getMessage()));
                }
            })
            .collect(Collectors.toList()));
    }

    public static Function<AdminJobQueueRepository, Boolean> executionScheduler(JobName jobName, Map<String, Object> metadata, ZonedDateTime executionTime) {
        return adminJobQueueRepository -> {
            try {
                int result = adminJobQueueRepository.schedule(jobName, executionTime, metadata);
                if (result == 0) {
                    log.trace("Possible duplication detected while inserting {}", jobName);
                }
                return result == 1;
            } catch (DataIntegrityViolationException ex) {
                log.trace("Integrity violation", ex);
                return false;
            }
        };
    }
}
