/**
 * This file is part of bagarino.
 *
 * bagarino is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * bagarino is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with bagarino.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.bagarino.manager;

import io.bagarino.manager.system.ConfigurationManager;
import io.bagarino.model.SpecialPrice;
import io.bagarino.model.system.ConfigurationKeys;
import io.bagarino.repository.SpecialPriceRepository;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Class SpecialPriceTokenGenerator.
 * This class generates a bunch of tokens which will be used for
 * granting a special price to a specific user category.
 */
@Component
@Log4j2
public class SpecialPriceTokenGenerator {

    private static final char[] ADMITTED_CHARACTERS = new char[]{
            'A', 'B', 'C', 'D', 'E', 'F',
            'G', 'H', 'J', 'K', 'M', 'N',
            'P', 'Q', 'R', 'S', 'T', 'W',
            'X', 'Y', 'Z', '2', '3', '4',
            '5', '6', '7', '8', '9'
    };

    private final AtomicReference<Integer> codeLength = new AtomicReference<>();
    private final SpecialPriceRepository specialPriceRepository;
    private final ConfigurationManager configurationManager;

    @Autowired
    public SpecialPriceTokenGenerator(ConfigurationManager configurationManager,
                                      SpecialPriceRepository specialPriceRepository) {
        this.specialPriceRepository = specialPriceRepository;
        this.configurationManager = configurationManager;
    }

    public void generatePendingCodes() {
        StopWatch stopWatch = new StopWatch();
        log.debug("start pending codes generation");
        stopWatch.start();
        specialPriceRepository.findWaitingElements().stream()
                .forEach(this::generateCode);
        stopWatch.stop();
        log.debug("end. Took {} ms", stopWatch.getTime());
    }

    private void generateCode(SpecialPrice specialPrice) {
        while (true) {
            try {
                log.debug("generate code for special price with id {}", specialPrice.getId());
                specialPriceRepository.updateCode(nextValidCode(), specialPrice.getId());
                log.debug("done.");
                return;
            } catch (DataAccessException e) {
                log.warn("got a duplicate. Retrying...", e);
            }
        }
    }

    private String nextValidCode() {
        while (true) {
            String code = generateRandomCode();
            if (specialPriceRepository.countByCode(code) == 0) {
                return code;
            }
        }
    }

    private String generateRandomCode() {
        return RandomStringUtils.random(getCodeLength(), ADMITTED_CHARACTERS);
    }

    private int getCodeLength() {
        Integer length = codeLength.get();
        if (!Optional.ofNullable(length).isPresent()) {
            codeLength.compareAndSet(length, configurationManager.getIntConfigValue(ConfigurationKeys.SPECIAL_PRICE_CODE_LENGTH, 6));
            length = codeLength.get();
        }
        return length;
    }
}
