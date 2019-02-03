package alfio.manager.payment;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

@UtilityClass
class MetadataBuilder {

    static Map<String, String> buildMetadata(PaymentSpecification spec) {
        Map<String, String> initialMetadata = new HashMap<>();
        initialMetadata.put("reservationId", spec.getReservationId());
        initialMetadata.put("email", spec.getEmail());
        initialMetadata.put("fullName", spec.getEmail());
        if (StringUtils.isNotBlank(spec.getBillingAddress())) {
            initialMetadata.put("billingAddress", spec.getBillingAddress());
        }
        return initialMetadata;
    }

}
