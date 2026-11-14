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
package alfio.controller.api.admin;

import alfio.manager.AccessService;
import alfio.manager.SpecialPriceManager;
import alfio.model.modification.SendCodeModification;
import alfio.model.modification.UploadBase64FileModification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SpecialPriceApiControllerTest {

    private SpecialPriceManager specialPriceManager;
    private SpecialPriceApiController controller;
    private final Principal principal = () -> "admin";

    @BeforeEach
    void setUp() {
        specialPriceManager = mock(SpecialPriceManager.class);
        controller = new SpecialPriceApiController(specialPriceManager, mock(AccessService.class));
    }

    @Test
    void linkCodesWithoutHeaderReadsEveryLine() throws Exception {
        // no withHeader param, as sent by the admin console before the template had a header
        var response = controller.linkAssigneeToCodes("event", 1, false, csv("ABCD,Doe John,john@example.org,en\r\n,Roe Jane,jane@example.org,it\r\n"), principal);
        List<SendCodeModification> modifications = capturedModifications();
        assertEquals(2, modifications.size());
        assertEquals("ABCD", modifications.get(0).code());
        assertEquals("Doe John", modifications.get(0).assignee());
        assertEquals("john@example.org", modifications.get(0).email());
        assertEquals("en", modifications.get(0).language());
        assertNull(modifications.get(1).code());
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void linkCodesWithHeaderSkipsTheFirstLine() throws Exception {
        controller.linkAssigneeToCodes("event", 1, true, csv("\"code\",\"assignee\",\"email\",\"language\"\r\nABCD,Doe John,john@example.org,en\r\n"), principal);
        List<SendCodeModification> modifications = capturedModifications();
        assertEquals(1, modifications.size());
        assertEquals("ABCD", modifications.get(0).code());
        assertEquals("Doe John", modifications.get(0).assignee());
    }

    @Test
    void linkCodesWithHeaderAndHeaderOnlyFileYieldsNoRow() throws Exception {
        controller.linkAssigneeToCodes("event", 1, true, csv("code,assignee,email,language\r\n"), principal);
        assertEquals(0, capturedModifications().size());
    }

    @Test
    void linkCodesWithHeaderParsesQuotedValues() throws Exception {
        controller.linkAssigneeToCodes("event", 1, true, csv("code,assignee,email,language\r\nABCD,\"Doe, John\",john@example.org,en\r\n"), principal);
        List<SendCodeModification> modifications = capturedModifications();
        assertEquals(1, modifications.size());
        assertEquals("Doe, John", modifications.get(0).assignee());
    }

    @Test
    void emptyLinesAreSkipped() throws Exception {
        controller.linkAssigneeToCodes("event", 1, true, csv("code,assignee,email,language\r\n\r\nABCD,Doe John,john@example.org,en\r\n"), principal);
        assertEquals(1, capturedModifications().size());
    }

    @SuppressWarnings("unchecked")
    private List<SendCodeModification> capturedModifications() {
        var captor = ArgumentCaptor.forClass(List.class);
        verify(specialPriceManager).linkAssigneeToCode(captor.capture(), eq("event"), eq(1), eq("admin"));
        return captor.getValue();
    }

    private static UploadBase64FileModification csv(String content) {
        var file = new UploadBase64FileModification();
        file.setFileAsString(content);
        file.setName("invitations.csv");
        file.setType("text/csv");
        return file;
    }
}
