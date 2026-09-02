package com.vuno.confluence.deptcalendar.service;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.confluence.user.ConfluenceUser;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.vuno.confluence.deptcalendar.ao.Calendar;
import com.vuno.confluence.deptcalendar.ao.Event;
import com.vuno.confluence.deptcalendar.model.EditScope;
import com.vuno.confluence.deptcalendar.model.EventType;
import com.vuno.confluence.deptcalendar.model.EventVisibility;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Named
public class EventServiceImpl implements EventService
{
    private final ActiveObjects ao;
    private final CalendarService calendarService;
    private final SpaceAccessGuard accessGuard;

    @Inject
    public EventServiceImpl(@ComponentImport ActiveObjects ao, CalendarService calendarService, SpaceAccessGuard accessGuard)
    {
        this.ao = ao;
        this.calendarService = calendarService;
        this.accessGuard = accessGuard;
    }

    @Override
    public List<EventOccurrence> findBySpace(String spaceKey, Date start, Date end, ConfluenceUser user)
    {
        accessGuard.requireView(user, spaceKey);

        // Spaces normally hold a single calendar, but any left over from the days of
        // user-created calendars still contribute their events.
        Set<Integer> calendarIds = new HashSet<>();
        for (Calendar calendar : ao.find(Calendar.class))
        {
            if (spaceKey.equals(calendar.getSpaceKey()))
            {
                calendarIds.add(calendar.getID());
            }
        }

        List<EventOccurrence> result = new ArrayList<>();
        for (Event event : ao.find(Event.class))
        {
            if (event.getCalendar() == null || !calendarIds.contains(event.getCalendar().getID()))
            {
                continue;
            }
            if (!isVisibleTo(event, user))
            {
                continue;
            }
            result.addAll(occurrencesInRange(event, start, end));
        }
        return result;
    }

    private static boolean isVisibleTo(Event event, ConfluenceUser user)
    {
        if (event.getVisibility() != EventVisibility.PRIVATE)
        {
            return true;
        }
        return user != null && userKey(user).equals(event.getCreatorKey());
    }

    private List<EventOccurrence> occurrencesInRange(Event event, Date start, Date end)
    {
        List<EventOccurrence> occurrences = new ArrayList<>();
        RecurrenceRule rule = RecurrenceRule.parse(event.getRepeatRule());
        if (rule == null)
        {
            if (overlapsRange(event.getStartDate(), event.getEndDate(), start, end))
            {
                occurrences.add(new EventOccurrence(event, event.getStartDate(), event.getEndDate(), false));
            }
            return occurrences;
        }

        long duration = durationMillis(event);
        for (Date occurrenceStart : RecurrenceExpander.expand(event.getStartDate(), duration, rule,
                exceptionDates(event), start, end))
        {
            occurrences.add(new EventOccurrence(event, occurrenceStart,
                    new Date(occurrenceStart.getTime() + duration), true));
        }
        return occurrences;
    }

    @Override
    public Event create(String spaceKey, EventType eventType, String what, String who,
                         Date start, Date end, boolean allDay, String repeatRule,
                         EventVisibility visibility, ConfluenceUser user)
    {
        accessGuard.requireEdit(user, spaceKey);
        Calendar calendar = calendarService.getOrCreateDefault(spaceKey, user);
        return store(ao.create(Event.class), calendar, eventType, what, who, start, end, allDay,
                normalizeRepeatRule(repeatRule), visibility, user);
    }

    @Override
    public Event update(int id, EventType eventType, String what, String who,
                         Date start, Date end, boolean allDay, String repeatRule,
                         EventVisibility visibility, EditScope scope, Date occurrenceStart, ConfluenceUser user)
    {
        Event event = findById(id);
        Calendar calendar = event.getCalendar();
        accessGuard.requireEdit(user, calendar.getSpaceKey());
        requireOwnerForPrivate(event, user);

        // A non-recurring event has a single occurrence, so editing "this occurrence"
        // is the same as editing the series.
        if (scope == EditScope.OCCURRENCE && RecurrenceRule.parse(event.getRepeatRule()) != null)
        {
            requireOccurrenceStart(occurrenceStart);
            addExceptionDate(event, occurrenceStart);
            return store(ao.create(Event.class), calendar, eventType, what, who, start, end, allDay,
                    null, visibility, user);
        }

        event.setEventType(eventType);
        event.setWhat(what);
        event.setWho(who);
        event.setStartDate(start);
        event.setEndDate(end);
        event.setAllDay(allDay);
        event.setRepeatRule(normalizeRepeatRule(repeatRule));
        event.setVisibility(visibility);
        event.setUpdatedDate(new Date());
        event.save();
        return event;
    }

    @Override
    public void delete(int id, EditScope scope, Date occurrenceStart, ConfluenceUser user)
    {
        Event event = findById(id);
        accessGuard.requireEdit(user, event.getCalendar().getSpaceKey());
        requireOwnerForPrivate(event, user);

        if (scope == EditScope.OCCURRENCE && RecurrenceRule.parse(event.getRepeatRule()) != null)
        {
            requireOccurrenceStart(occurrenceStart);
            addExceptionDate(event, occurrenceStart);
            return;
        }
        ao.delete(event);
    }

    private Event store(Event event, Calendar calendar, EventType eventType, String what, String who,
                        Date start, Date end, boolean allDay, String repeatRule,
                        EventVisibility visibility, ConfluenceUser user)
    {
        event.setCalendar(calendar);
        event.setEventType(eventType);
        event.setWhat(what);
        event.setWho(who);
        event.setStartDate(start);
        event.setEndDate(end);
        event.setAllDay(allDay);
        event.setRepeatRule(repeatRule);
        event.setVisibility(visibility == null ? EventVisibility.SPACE : visibility);
        event.setCreatorKey(user == null ? null : userKey(user));
        event.setCreationDate(new Date());
        event.setUpdatedDate(new Date());
        event.save();
        return event;
    }

    /** Someone else's private event stays theirs, even for space admins. */
    private static void requireOwnerForPrivate(Event event, ConfluenceUser user)
    {
        if (!isVisibleTo(event, user))
        {
            throw new NotFoundException("Event not found: " + event.getID());
        }
    }

    private static String userKey(ConfluenceUser user)
    {
        return user.getKey() == null ? user.getName() : user.getKey().getStringValue();
    }

    private static void requireOccurrenceStart(Date occurrenceStart)
    {
        if (occurrenceStart == null)
        {
            throw new IllegalArgumentException("occurrenceStart is required when scope is occurrence");
        }
    }

    private void addExceptionDate(Event event, Date occurrenceStart)
    {
        Set<String> dates = exceptionDates(event);
        dates.add(RecurrenceExpander.formatExceptionDate(occurrenceStart));
        event.setExceptionDates(String.join(",", dates));
        event.setUpdatedDate(new Date());
        event.save();
    }

    private static Set<String> exceptionDates(Event event)
    {
        Set<String> dates = new LinkedHashSet<>();
        String stored = event.getExceptionDates();
        if (stored != null && !stored.trim().isEmpty())
        {
            for (String date : stored.split(","))
            {
                if (!date.trim().isEmpty())
                {
                    dates.add(date.trim());
                }
            }
        }
        return dates;
    }

    private static String normalizeRepeatRule(String repeatRule)
    {
        RecurrenceRule rule = RecurrenceRule.parse(repeatRule);
        return rule == null ? null : rule.serialize();
    }

    private static long durationMillis(Event event)
    {
        if (event.getStartDate() == null || event.getEndDate() == null)
        {
            return 0;
        }
        return event.getEndDate().getTime() - event.getStartDate().getTime();
    }

    private static boolean overlapsRange(Date eventStart, Date eventEnd, Date rangeStart, Date rangeEnd)
    {
        if (rangeStart != null && eventEnd != null && eventEnd.before(rangeStart))
        {
            return false;
        }
        return !(rangeEnd != null && eventStart != null && eventStart.after(rangeEnd));
    }

    private Event findById(int id)
    {
        for (Event event : ao.find(Event.class))
        {
            if (event.getID() == id)
            {
                return event;
            }
        }
        throw new NotFoundException("Event not found: " + id);
    }
}
