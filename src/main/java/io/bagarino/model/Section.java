package io.bagarino.model;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class Section {

    private final String description;
    private final LocalDate date;
    private final LocalTime start;
    private final LocalTime end;
    private final String latitude;
    private final String longitude;

}
