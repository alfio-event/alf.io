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
package alfio.model.poll;

import alfio.util.MonetaryUtil;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
public class PollStatistics {
    private final int totalVotes;
    private final int allowedParticipants;
    private final List<PollOptionStatistics> countByOption;

    public int getTotalVotes() {
        return totalVotes;
    }

    public int getAllowedParticipants() {
        return allowedParticipants;
    }

    public String getParticipationPercentage() {
        return getVotesPercentage(new BigDecimal(this.allowedParticipants), totalVotes);
    }

    public List<StatisticDetail> getOptionStatistics() {
        BigDecimal totalVotes = new BigDecimal(this.totalVotes);
        return countByOption.stream()
            .map(o -> {
                var percentage = getVotesPercentage(totalVotes, o.getVotes());
                return new StatisticDetail(o.getVotes(), o.getOptionId(), percentage);
            })
            .collect(Collectors.toList());
    }

    private static String getVotesPercentage(BigDecimal totalVotes, int votes) {
        if(votes == 0) {
            return "0";
        }
        return new BigDecimal(votes)
            .setScale(3, RoundingMode.HALF_UP)
            .divide(totalVotes, RoundingMode.HALF_UP)
            .multiply(MonetaryUtil.HUNDRED)
            .setScale(2, RoundingMode.HALF_UP)
            .toPlainString();
    }


    public static class StatisticDetail {
        private final int votes;
        private final long optionId;
        private final String percentage;


        public StatisticDetail(int votes, long optionId, String percentage) {
            this.votes = votes;
            this.optionId = optionId;
            this.percentage = percentage;
        }

        public int getVotes() {
            return votes;
        }

        public long getOptionId() {
            return optionId;
        }

        public String getPercentage() {
            return percentage;
        }
    }

}
