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
package alfiov2.builder;

import alfio.model.ContentLanguage;
import alfiov2.command.admin.EventDescriptor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.builder.CompareToBuilder;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

public class EventBuilder {

    public static class StrictAllocation  {

        private final String name;
        private final NavigableSet<TimeSlot> dates = new TreeSet<>();
        private final ZoneId timeZone;
        private final int seats;
        private final List<Category> categories = new ArrayList<>();
        private LocalDateTime publishDate = LocalDateTime.now();

        private StrictAllocation(String name, int seats, ZoneId timeZone) {
            this.name = name;
            this.seats = seats;
            this.timeZone = timeZone;
        }

        public static StrictAllocation newEvent(String name, int seats, ZoneId timeZone) {
            return new StrictAllocation(name, seats, timeZone);
        }

        public StrictAllocation addTimeSlot(LocalDateTime start, LocalDateTime end) {
            this.dates.add(new TimeSlot(start, end));
            return this;
        }

        public StrictAllocation addCategory(Set<I18nTextBuilder.TitleDescriptionPair> titleDescriptions, int seats, PaymentOptionsBuilder.PaymentOptions paymentOptions) {
            return addCategory(titleDescriptions, seats, paymentOptions, LocalDateTime.now(), null);
        }

        public StrictAllocation addCategory(Set<I18nTextBuilder.TitleDescriptionPair> titleDescriptions, int seats, PaymentOptionsBuilder.PaymentOptions paymentOptions, LocalDateTime inception, LocalDateTime expiration) {
            categories.add(new Category(titleDescriptions, seats, paymentOptions, inception, expiration));
            return this;
        }

        public StrictAllocation publishFrom(LocalDateTime publishDateTime) {
            this.publishDate = publishDateTime;
            return this;
        }

        public EventDescriptor createEventDescriptor() {
            return null;
        }

        public StrictAllocation enableCombiWithDefault(PaymentOptionsBuilder.PaymentOptions of) {
            return this;
        }

        public StrictAllocation addAdditionalItem(Set<I18nTextBuilder.TitleDescriptionPair> text, PaymentOptionsBuilder.PaymentOptions of) {
            return this;
        }

        public StrictAllocation startSellingTickets(PaymentOptionsBuilder.PaymentOptions paymentOptions) {
            if(categories.isEmpty()) {
                return addCategory(new HashSet<>(Collections.singleton(new I18nTextBuilder.TitleDescriptionPair("Standard", "", ContentLanguage.ENGLISH))), seats, paymentOptions);
            }
            return this;
        }

        @RequiredArgsConstructor
        private static class Category {
            private final Set<I18nTextBuilder.TitleDescriptionPair> titleDescriptions;
            private final int seats;
            private final PaymentOptionsBuilder.PaymentOptions paymentOptions;
            private final LocalDateTime inception;
            private final LocalDateTime expiration;
        }

        @RequiredArgsConstructor
        @Data
        private static class TimeSlot implements Comparable<TimeSlot> {
            private final LocalDateTime start;
            private final LocalDateTime end;

            @Override
            public int compareTo(TimeSlot o) {
                return new CompareToBuilder().append(start, o.start).append(end, o.end).toComparison();
            }
        }
    }




    public static class RelaxedAllocation {
        public static RelaxedAllocation createEvent(String s, ZonedDateTime of) {
            return new RelaxedAllocation();
        }
    }
}
