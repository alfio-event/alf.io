package alfio.manager.i18n;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Component
public class I18nManager {

    public List<Locale> getAvailableLocales() {
        return Arrays.asList(Locale.ITALIAN, Locale.ENGLISH);
    }
}
