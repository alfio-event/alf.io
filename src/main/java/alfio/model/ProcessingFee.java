package alfio.model;

import alfio.model.transaction.PaymentProxy;
import alfio.util.Json;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.time.ZonedDateTime;
import java.util.*;

import static ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;

@Getter
public class ProcessingFee {

    public enum FeeType {
        FIXED_AMOUNT, PERCENTAGE
    }

    private final int id;
    private final Integer eventId;
    private final Integer organizationId;
    private final ZonedDateTime utcStart;
    private final ZonedDateTime utcEnd;
    private final int amount;
    private final FeeType feeType;
    private final Set<Integer> categories;
    private final PaymentProxy paymentMethod;

    public ProcessingFee(@Column("id")int id,
                         @Column("event_id_fk") Integer eventId,
                         @Column("organization_id_fk") Integer organizationId,
                         @Column("valid_from") ZonedDateTime utcStart,
                         @Column("valid_to") ZonedDateTime utcEnd,
                         @Column("amount") int amount,
                         @Column("fee_type") FeeType feeType,
                         @Column("categories") String categories,
                         @Column("payment_method") PaymentProxy paymentMethod) {
        this.id = id;
        this.eventId = eventId;
        this.organizationId = organizationId;
        this.utcStart = utcStart;
        this.utcEnd = utcEnd;
        this.amount = amount;
        this.feeType = feeType;
        if(categories != null) {
            List<Integer> categoriesId = Json.GSON.fromJson(categories, new TypeToken<List<Integer>>(){}.getType());
            this.categories = categoriesId == null ? Collections.emptySet() : new TreeSet<>(categoriesId);
        } else {
            this.categories = Collections.emptySet();
        }
        this.paymentMethod = paymentMethod;
    }

    public boolean getFixedAmount() {
        return feeType == FeeType.FIXED_AMOUNT;
    }

    private static Set<PaymentProxy> parsePaymentMethods(@Column("payment_methods") String paymentMethods) {
        PaymentProxy[] parsed = Arrays.stream(paymentMethods.split(","))
            .map(PaymentProxy::safeValueOf)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toArray(PaymentProxy[]::new);
        if(parsed.length > 1) {
            return EnumSet.of(parsed[0], Arrays.copyOfRange(parsed, 1, parsed.length));
        } else if (parsed.length == 1) {
            return EnumSet.of(parsed[0]);
        } else {
            return EnumSet.noneOf(PaymentProxy.class);
        }
    }
}
