package io.cloudpos.identity.shift;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class BusinessDay {

    private BusinessDay() {
    }

    public static LocalDate of(Instant moment, String timezone, LocalTime cutoff) {
        ZonedDateTime local = moment.atZone(ZoneId.of(timezone));
        return local.toLocalTime().isBefore(cutoff)
                ? local.toLocalDate().minusDays(1)
                : local.toLocalDate();
    }
}
