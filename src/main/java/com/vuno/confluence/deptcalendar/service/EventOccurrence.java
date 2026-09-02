package com.vuno.confluence.deptcalendar.service;

import com.vuno.confluence.deptcalendar.ao.Event;

import java.util.Date;

/**
 * One dated instance of an event. A non-recurring event has exactly one occurrence
 * carrying its own dates; a recurring one has an occurrence per expanded date, all
 * sharing the same stored {@link Event}.
 */
public class EventOccurrence
{
    private final Event event;
    private final Date start;
    private final Date end;
    private final boolean recurring;

    public EventOccurrence(Event event, Date start, Date end, boolean recurring)
    {
        this.event = event;
        this.start = start;
        this.end = end;
        this.recurring = recurring;
    }

    public Event getEvent()
    {
        return event;
    }

    public Date getStart()
    {
        return start;
    }

    public Date getEnd()
    {
        return end;
    }

    public boolean isRecurring()
    {
        return recurring;
    }
}
