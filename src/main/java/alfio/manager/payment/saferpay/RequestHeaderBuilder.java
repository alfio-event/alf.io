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

import com.google.gson.stream.JsonWriter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor
class RequestHeaderBuilder {
    private static final String SPEC_VERSION = "1.18";
    private final String customerId;
    private final String requestId;
    private final Integer retryIndicator;

    @SneakyThrows
    JsonWriter appendTo(JsonWriter writer) {
        writer.name("RequestHeader").beginObject() //
            .name("SpecVersion").value(SPEC_VERSION) //
            .name("CustomerId").value(customerId) //
            .name("RequestId").value(requestId);
        if(retryIndicator != null) {
            writer.name("RetryIndicator").value(retryIndicator);
        }
        return writer.endObject();
    }
}
