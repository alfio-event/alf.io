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
package alfio.manager.payment.saferpay;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("Request Header")
class RequestHeaderBuilderTest {

    @Test
    void appendToComplete() throws IOException {
        var requestBuilder = new RequestHeaderBuilder("customerId", "requestId", 1);
        var out = new StringWriter();
        requestBuilder.appendTo(new JsonWriter(out).beginObject()).endObject().flush();
        checkJson(JsonParser.parseString(out.toString()).getAsJsonObject(), true);
    }

    @Test
    void appendToNoRetry() throws IOException {
        var requestBuilder = new RequestHeaderBuilder("customerId", "requestId", null);
        var out = new StringWriter();
        requestBuilder.appendTo(new JsonWriter(out).beginObject()).endObject().flush();
        checkJson(JsonParser.parseString(out.toString()).getAsJsonObject(), false);
    }

    private void checkJson(JsonObject json, boolean expectRetry) {
        var requestHeader = json.get("RequestHeader").getAsJsonObject();
        assertEquals("customerId", requestHeader.get("CustomerId").getAsString());
        assertEquals("requestId", requestHeader.get("RequestId").getAsString());
        if(expectRetry) {
            assertEquals("1", requestHeader.get("RetryIndicator").getAsString());
        } else {
            assertNull(requestHeader.get("RetryIndicator"));
        }
    }
}
