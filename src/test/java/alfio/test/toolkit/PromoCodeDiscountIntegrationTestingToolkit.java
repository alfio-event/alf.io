package alfio.test.toolkit;

import alfio.model.PromoCodeDiscount;
import alfio.repository.PromoCodeDiscountRepository;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.ZonedDateTime;
import java.util.Map;

public class PromoCodeDiscountIntegrationTestingToolkit {
    public static final String TEST_PROMO_CODE = "test-promo-code";

    private final PromoCodeDiscountRepository promoCodeDiscountRepository;
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public PromoCodeDiscountIntegrationTestingToolkit(final PromoCodeDiscountRepository promoCodeDiscountRepository, final NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.promoCodeDiscountRepository = promoCodeDiscountRepository;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public void createPromoCodeDiscount(final int eventId, final int organizationId, final String email) {
        promoCodeDiscountRepository.addPromoCode(TEST_PROMO_CODE, eventId, organizationId, ZonedDateTime.now(), ZonedDateTime.now()
                                                                                                                             .plusDays(3), 10, PromoCodeDiscount.DiscountType.FIXED_AMOUNT, "[1,2,3]", 1, "test promo code", "test-email@gmail.com", PromoCodeDiscount.CodeType.DISCOUNT, 21, "usd");
        var promoCodeId = promoCodeDiscountRepository.findAllInEvent(eventId).get(0).getId();
        namedParameterJdbcTemplate.update("""
                 update tickets_reservation set promo_code_id_fk = :promotionCodeDiscountId
                 where email_address = :email
            """, Map.of("promotionCodeDiscountId", promoCodeId, "email", email));
    }
}
