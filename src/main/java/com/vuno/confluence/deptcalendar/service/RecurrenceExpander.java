package com.vuno.confluence.deptcalendar.service;

import com.vuno.confluence.deptcalendar.model.RepeatFrequency;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Expands a recurring event into the occurrence start dates that fall inside a query
 * range. Each occurrence is computed as an offset from the series start (never from the
 * previous occurrence) so month/year steps clamp without drifting: a series starting on
 * Jan 31 yields Feb 28, then Mar 31 again.
 */
public final class RecurrenceExpander
{
    private static final String EXCEPTION_DATE_FORMAT = "yyyyMMdd";
    private static final int MAX_OCCURRENCES = 500;
    private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;

    private RecurrenceExpander()
    {
    }

    public static String formatExceptionDate(Date date)
    {
        return new SimpleDateFormat(EXCEPTION_DATE_FORMAT).format(date);
    }

    /**
     * @param rangeStart inclusive lower bound, or null for "no lower bound"
     * @param rangeEnd   inclusive upper bound, or null to just take the first
     *                   {@value #MAX_OCCURRENCES} occurrences
     * @return occurrence start dates overlapping the range, excluding exception dates
     */
    public static List<Date> expand(Date seriesStart, long durationMillis, RecurrenceRule rule,
                                    Set<String> exceptionDates, Date rangeStart, Date rangeEnd)
    {
        List<Date> occurrences = new ArrayList<>();
        int index = firstIndexNear(seriesStart, durationMillis, rule.getFrequency(), rangeStart);
        while (occurrences.size() < MAX_OCCURRENCES)
        {
            Date occurrenceStart = occurrenceAt(seriesStart, rule.getFrequency(), index);
            index++;

            if (rule.getUntil() != null && occurrenceStart.getTime() > endOfDay(rule.getUntil()))
            {
                break;
            }
            if (rangeEnd != null && occurrenceStart.after(rangeEnd))
            {
                break;
            }
            if (rangeStart != null && occurrenceStart.getTime() + durationMillis < rangeStart.getTime())
            {
                continue;
            }
            if (exceptionDates.contains(formatExceptionDate(occurrenceStart)))
            {
                continue;
            }
            occurrences.add(occurrenceStart);
        }
        return occurrences;
    }

    private static Date occurrenceAt(Date seriesStart, RepeatFrequency frequency, int index)
    {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(seriesStart);
        switch (frequency)
        {
            case DAILY:
                calendar.add(Calendar.DAY_OF_MONTH, index);
                break;
            case WEEKLY:
                calendar.add(Calendar.DAY_OF_MONTH, index * 7);
                break;
            case MONTHLY:
                calendar.add(Calendar.MONTH, index);
                break;
            case YEARLY:
                calendar.add(Calendar.YEAR, index);
                break;
        }
        return calendar.getTime();
    }

    /**
     * Skips ahead to roughly the first occurrence that can still touch the range, so a
     * long-running series does not burn its occurrence budget on dates nobody asked for.
     * Deliberately conservative — it steps back far enough to catch occurrences that
     * start before the range but run into it.
     */
    private static int firstIndexNear(Date seriesStart, long durationMillis, RepeatFrequency frequency, Date rangeStart)
    {
        if (rangeStart == null || !rangeStart.after(seriesStart))
        {
            return 0;
        }
        long periodDays = periodDays(frequency);
        long elapsedDays = (rangeStart.getTime() - seriesStart.getTime()) / DAY_MILLIS;
        long durationPeriods = (durationMillis / DAY_MILLIS) / periodDays + 1;
        long index = elapsedDays / periodDays - durationPeriods;
        return index < 0 ? 0 : (int) index;
    }

    private static long periodDays(RepeatFrequency frequency)
    {
        switch (frequency)
        {
            case WEEKLY:
                return 7;
            case MONTHLY:
                return 28;
            case YEARLY:
                return 365;
            default:
                return 1;
        }
    }

    private static long endOfDay(Date date)
    {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTimeInMillis();
    }
}
