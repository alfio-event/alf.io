package alfio.controller.api.v2.admin;

import alfio.config.WebSecurityConfig;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v2/admin/setting/")
@Api("setting")
public class AdminApiSettingController {

    @GetMapping("/system")
    @ApiOperation(value = "Retrieves all the system-level settings for the current instance", tags = WebSecurityConfig.ADMIN)
    public List<Setting> getSystemLevelSettings(Principal principal) {
        return null;
    }

    @GetMapping("/organization/{orgShortName}")
    @ApiOperation(value = "Retrieves all settings for the selected organization", tags = WebSecurityConfig.OWNER)
    public List<Setting> getOrgLevelSettings(@PathVariable("orgShortName") String orgShortName, Principal principal) {
        return null;
    }

    @GetMapping("/organization/{orgShortName}/event/{eventShortName}")
    @ApiOperation(value = "Retrieves all settings for the selected event", tags = WebSecurityConfig.OWNER)
    public List<Setting> getEventLevelSettings(@PathVariable("orgShortName") String orgShortName,
                                             @PathVariable("eventShortName") String eventShortName,
                                             Principal principal) {
        return null;
    }

    @GetMapping("/organization/{orgShortName}/event/{eventShortName}/category/{categoryId}")
    @ApiOperation(value = "Retrieves all settings for the selected category", tags = WebSecurityConfig.OWNER)
    public List<Setting> getCategoryLevelSettings(@PathVariable("orgShortName") String orgShortName,
                                             @PathVariable("eventShortName") String eventShortName,
                                             @PathVariable("categoryId") String categoryId,
                                             Principal principal) {
        return null;
    }

    @PostMapping("/{settingId}/preview")
    @ApiOperation(value = "Request preview for template settings", tags = WebSecurityConfig.OWNER)
    public void previewTemplate(@PathVariable("settingId") int settingId, Principal principal) {

    }

    @PostMapping("/system/{KEY}")
    @ApiOperation(value = "Set a value for setting at SYSTEM level", tags = WebSecurityConfig.ADMIN)
    public Setting setSystemSettingValue(@PathVariable("KEY") String settingKey, Principal principal) {
        return null;
    }

    @PostMapping("/organization/{orgShortName}/{KEY}")
    @ApiOperation(value = "Set a value for setting at ORGANIZATION level", tags = WebSecurityConfig.OWNER)
    public Setting setOrganizationSettingValue(@PathVariable("orgShortName") String shortName,
                                         @PathVariable("KEY") String settingKey,
                                         Principal principal) {
        return null;
    }

    @PostMapping("/organization/{orgShortName}/event/{eventShortName}/{KEY}")
    @ApiOperation(value = "Set a value for setting at EVENT level", tags = WebSecurityConfig.OWNER)
    public Setting setEventSettingValue(@PathVariable("orgShortName") String orgShortName,
                                         @PathVariable("eventShortName") String eventShortName,
                                         @PathVariable("KEY") String settingKey,
                                         Principal principal) {
        return null;
    }

    @PostMapping("/organization/{orgShortName}/event/{eventShortName}/category/{categoryId}/{KEY}")
    @ApiOperation(value = "Set a value for setting at TICKET_CATEGORY level", tags = WebSecurityConfig.OWNER)
    public Setting setCategorySettingValue(@PathVariable("orgShortName") String orgShortName,
                                         @PathVariable("eventShortName") String eventShortName,
                                         @PathVariable("categoryId") String categoryId,
                                         @PathVariable("KEY") String settingKey,
                                         Principal principal) {
        return null;
    }

    @DeleteMapping("/system/{KEY}")
    @ApiOperation(value = "Removes a setting at SYSTEM level", tags = WebSecurityConfig.ADMIN)
    public boolean deleteSystemSettingValue(@PathVariable("KEY") String settingKey, Principal principal) {
        return false;
    }

    @DeleteMapping("/organization/{orgShortName}/{KEY}")
    @ApiOperation(value = "Removes a setting at ORGANIZATION level", tags = WebSecurityConfig.OWNER)
    public boolean deleteOrganizationSettingValue(@PathVariable("orgShortName") String shortName,
                                         @PathVariable("KEY") String settingKey,
                                         Principal principal) {
        return false;
    }

    @DeleteMapping("/organization/{orgShortName}/event/{eventShortName}/{KEY}")
    @ApiOperation(value = "Removes a setting at EVENT level", tags = WebSecurityConfig.OWNER)
    public boolean deleteEventSettingValue(@PathVariable("orgShortName") String orgShortName,
                                         @PathVariable("eventShortName") String eventShortName,
                                         @PathVariable("KEY") String settingKey,
                                         Principal principal) {
        return false;
    }

    @DeleteMapping("/organization/{orgShortName}/event/{eventShortName}/category/{categoryId}/{KEY}")
    @ApiOperation(value = "Removes a setting at TICKET_CATEGORY level", tags = WebSecurityConfig.OWNER)
    public boolean deleteCategorySettingValue(@PathVariable("orgShortName") String orgShortName,
                                         @PathVariable("eventShortName") String eventShortName,
                                         @PathVariable("categoryId") String categoryId,
                                         @PathVariable("KEY") String settingKey,
                                         Principal principal) {
        return false;
    }

    private class Setting {
    }
}
