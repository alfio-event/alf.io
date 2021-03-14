package alfio.controller.form;

import lombok.Getter;

import java.io.Serializable;

import static org.apache.commons.lang3.StringUtils.trimToNull;

@Getter
public class UpdateSubscriptionOwnerForm implements Serializable {
    private String email;
    private String firstName;
    private String lastName;

    public void setEmail(String email) {
        this.email = trimToNull(email);
    }

    public void setFirstName(String firstName) {
        this.firstName = trimToNull(firstName);
    }

    public void setLastName(String lastName) {
        this.lastName = trimToNull(lastName);
    }

    public boolean validate() {
        return this.email != null && this.firstName != null && this.lastName != null;
    }
}
