package alfio.controller.api.v1.admin;

import lombok.Getter;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.NotBlank;

@Getter
public class TestEmailRequest {

    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "Subject is required")
    private String subject;

    @NotBlank(message = "Text is required")
    private String text;
}

