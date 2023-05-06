/**
 * This file is part of alf.io.
 *
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */
package alfio.model;

import alfio.util.checkin.NameNormalizer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TicketTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void ticketCode(boolean caseInsensitive) {
        var fullName = "Full Name";
        var email = "email@example.org";
        var regularCase = Ticket.generateHmacTicketInfo("eventKey", caseInsensitive, fullName, email, "id", "uuid");
        var modifiedCase = Ticket.generateHmacTicketInfo("eventKey", caseInsensitive, fullName.toUpperCase(), email.toUpperCase(), "id", "uuid");
        assertEquals(caseInsensitive, regularCase.equals(modifiedCase));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        // format: UPPERCASE/lowercase,UPPERCASE/lowercase...
        "A/a,B/b,C/c,D/d,E/e,F/f,G/g,H/h,I/i,J/j,K/k,L/l,M/m,N/n,O/o,P/p,Q/q,R/r,S/s,T/t,U/u,V/v,W/w,X/x,Y/y,Z/z",
        "Α/α,Β/β,Γ/γ,Δ/δ,Ε/ε,Ζ/ζ,Η/η,Θ/θ,Ι/ι,Κ/κ,Λ/λ,Μ/μ,Ν/ν,Ξ/ξ,Ο/ο,Π/π,Ρ/ρ,Σ/σς,Τ/τ,Υ/υ,Φ/φ,Χ/χ,Ψ/ψ,Ω/ω", //greek, https://en.wikipedia.org/wiki/Greek_alphabet
        "Ç/ç,Ğ/ğ,I/i,İ/i,J/j,Ö/ö,Ş/ş,Ü/ü", // turkish https://en.wikipedia.org/wiki/Turkish_alphabet, except character ı, see NameNormalizer
        "А/а,Б/б,В/в,Г/г,Д/д,Е/е,Ж/ж,З/з,И/и,Й/й,К/к,Л/л,М/м,Н/н,О/о,П/п,Р/р,С/с,Т/т,У/у,Ф/ф,Х/х,Ц/ц,Ч/ч,Ш/ш,Щ/щ,Ъ/ъ,Ь/ь,Ю/ю,Я/я", // bulgarian, https://en.wikipedia.org/wiki/Bulgarian_alphabet
        "Å/å,Ä/ä,Ö/ö", // Swedish https://en.wikipedia.org/wiki/Swedish_alphabet
        "Ă/ă,Â/â,I/i,Î/î,Ș/ș,Ț/ț", // Romanian https://en.wikipedia.org/wiki/Romanian_alphabet
        "Ä/ä,Ö/ö,Ü/ü,ẞ/ß", // German https://en.wikipedia.org/wiki/German_orthography
        "Æ/æ,Ø/ø,Å/å", //Danish / Norwegian https://en.wikipedia.org/wiki/Danish_and_Norwegian_alphabet
        "Ą/ą,Ć/ć,Ę/ę,Ł/ł,Ń/ń,Ó/ó,Ś/ś,Ź/ź,Ż/ż", // Polish https://en.wikipedia.org/wiki/Polish_alphabet
        "À/à,È/è,É/é,Ì/ì,Ò/ò,Ù/ù", // Italian / French vowels
        "万/万,丈/丈,三/三,上/上", // some random characters with no support for lowerCase
        "❤/❤,✅/✅" // emojis
    })
    void validateCase(String letters) {
        var uppercase = new StringBuilder();
        var lowercase = new StringBuilder();
        Arrays.stream(letters.split(",")).forEach(str -> {
            var split = str.split("/");
            uppercase.append(split[0]);
            lowercase.append(split[1]);
        });
        assertEquals(lowercase.toString(), NameNormalizer.normalize(uppercase.toString()));
        var unmodifiedLowercase = Ticket.generateHmacTicketInfo("eventKey", false, lowercase.toString(), "mail@example.org", "id", "uuid");
        var processedUppercase = Ticket.generateHmacTicketInfo("eventKey", true, uppercase.toString(), "mail@example.org", "id", "uuid");
        assertEquals(unmodifiedLowercase, processedUppercase);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void ticketCodeUTF8Chars(boolean caseInsensitive) {
        var fullName = "çÇĞğIıİiÖöŞşÜüΑ";
        var fullNameSwappedCase = "ÇçğĞıIiİöÖşŞüÜα";
        var email = "email@example.org";
        var regularCase = Ticket.generateHmacTicketInfo("eventKey", caseInsensitive, fullName, email, "id", "uuid");
        var modifiedCase = Ticket.generateHmacTicketInfo("eventKey", caseInsensitive, fullNameSwappedCase, email.toUpperCase(), "id", "uuid");
        assertEquals(caseInsensitive, regularCase.equals(modifiedCase));
    }
}