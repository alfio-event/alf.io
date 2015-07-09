package alfio.controller.form;

import lombok.Data;

import java.util.Locale;

@Data
public class WaitingQueueSubscriptionForm {
    private String fullName;
    private String email;
    private Locale userLanguage;
}
