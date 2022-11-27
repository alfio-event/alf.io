package alfio.controller.support;

public record UserStatus(boolean authenticated,
                         String username,
                         String alfioVersion,
                         boolean demoModeEnabled,
                         boolean devModeEnabled,
                         boolean prodModeEnabled) {

}
