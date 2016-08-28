package alfio.controller.api.support;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class CurrencyDescriptor {
    private final String code;
    private final String name;
    private final String symbol;
    private final int fractionDigits;
}
