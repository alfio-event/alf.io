package alfio.controller.support;

import lombok.RequiredArgsConstructor;

import java.util.Map;

@RequiredArgsConstructor
public class FormattedEventDates {
    public final Map<String, String> beginDate;
    public final Map<String, String> beginTime;
    public final Map<String, String> endDate;
    public final Map<String, String> endTime;
}
