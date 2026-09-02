package com.vuno.confluence.deptcalendar.service;

import com.vuno.confluence.deptcalendar.model.RepeatFrequency;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Minimal RFC 5545 RRULE subset: a frequency plus an optional inclusive end date,
 * serialized as "FREQ=WEEKLY" or "FREQ=MONTHLY;UNTIL=20261231". Unknown parts are
 * ignored so rules written by a future, richer version still parse here.
 */
public class RecurrenceRule
{
    private static final String UNTIL_FORMAT = "yyyyMMdd";

    private final RepeatFrequency frequency;
    private final Date until;

    public RecurrenceRule(RepeatFrequency frequency, Date until)
    {
        this.frequency = frequency;
        this.until = until;
    }

    /**
     * @return the parsed rule, or null when the value is blank or carries no usable FREQ
     *         (both mean "does not repeat")
     */
    public static RecurrenceRule parse(String rule)
    {
        if (rule == null || rule.trim().isEmpty())
        {
            return null;
        }
        RepeatFrequency frequency = null;
        Date until = null;
        for (String part : rule.trim().split(";"))
        {
            int separator = part.indexOf('=');
            if (separator < 0)
            {
                continue;
            }
            String key = part.substring(0, separator).trim().toUpperCase();
            String value = part.substring(separator + 1).trim();
            if ("FREQ".equals(key))
            {
                frequency = parseFrequency(value);
            }
            else if ("UNTIL".equals(key))
            {
                until = parseUntil(value);
            }
        }
        return frequency == null ? null : new RecurrenceRule(frequency, until);
    }

    public String serialize()
    {
        StringBuilder rule = new StringBuilder("FREQ=").append(frequency.name());
        if (until != null)
        {
            rule.append(";UNTIL=").append(new SimpleDateFormat(UNTIL_FORMAT).format(until));
        }
        return rule.toString();
    }

    public RepeatFrequency getFrequency()
    {
        return frequency;
    }

    public Date getUntil()
    {
        return until;
    }

    private static RepeatFrequency parseFrequency(String value)
    {
        for (RepeatFrequency candidate : RepeatFrequency.values())
        {
            if (candidate.name().equalsIgnoreCase(value))
            {
                return candidate;
            }
        }
        return null;
    }

    private static Date parseUntil(String value)
    {
        SimpleDateFormat format = new SimpleDateFormat(UNTIL_FORMAT);
        format.setLenient(false);
        try
        {
            return format.parse(value);
        }
        catch (ParseException e)
        {
            throw new IllegalArgumentException("Invalid UNTIL value in repeat rule: " + value);
        }
    }
}
