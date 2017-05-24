package alfio.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class VatDetail {
    private final String vatNr;
    private final String country;
    private final boolean isValid;
    private final String name;
    private final String address;
    @JsonIgnore
    private final boolean vatExempt;

}
