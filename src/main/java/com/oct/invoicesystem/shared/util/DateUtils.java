package com.oct.invoicesystem.shared.util;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class DateUtils {

    public static Instant addBusinessDays(Instant start, int days) {
        ZonedDateTime zdt = start.atZone(ZoneId.systemDefault());
        int added = 0;
        // if days is negative, loop backwards (not needed but safe)
        while (added < days) {
            zdt = zdt.plusDays(1);
            if (zdt.getDayOfWeek() != DayOfWeek.SATURDAY && zdt.getDayOfWeek() != DayOfWeek.SUNDAY) {
                added++;
            }
        }
        return zdt.toInstant();
    }
}
