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
package alfio.job.executor;

import alfio.extension.ExtensionService;
import alfio.extension.ScriptingExecutionService;
import alfio.manager.system.AdminJobExecutor;
import alfio.model.system.AdminJobSchedule;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Slf4j
public class RetryFailedExtensionJobExecutor implements AdminJobExecutor {
    private final ExtensionService extensionService;

    public RetryFailedExtensionJobExecutor(ExtensionService extensionService) {
        this.extensionService = extensionService;
    }

    @Override
    public Set<JobName> getJobNames() {
        return EnumSet.of(JobName.EXECUTE_EXTENSION);
    }

    @Override
    public String process(AdminJobSchedule schedule) {
        var metadata = schedule.getMetadata();
        var name = (String) metadata.get(ScriptingExecutionService.EXTENSION_NAME);
        var path = (String) metadata.get(ScriptingExecutionService.EXTENSION_PATH);
        @SuppressWarnings("unchecked")
        var payload = (Map<String, Object>) metadata.get(ScriptingExecutionService.EXTENSION_PARAMS);
        try {
            extensionService.retryFailedAsyncScript(path, name, payload);
        } catch (RuntimeException ex) {
            log.warn("Caught error while retrying extension " + name, ex);
            throw ex;
        }
        return "OK";
    }
}
