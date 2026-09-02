package com.vuno.confluence.deptcalendar.service;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.confluence.user.ConfluenceUser;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.vuno.confluence.deptcalendar.ao.Calendar;
import com.vuno.confluence.deptcalendar.ao.Event;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Named
public class CalendarServiceImpl implements CalendarService
{
    private static final String DEFAULT_CALENDAR_NAME = "Dept Calendar";

    private final ActiveObjects ao;
    private final SpaceAccessGuard accessGuard;

    @Inject
    public CalendarServiceImpl(@ComponentImport ActiveObjects ao, SpaceAccessGuard accessGuard)
    {
        this.ao = ao;
        this.accessGuard = accessGuard;
    }

    @Override
    public List<Calendar> findBySpace(String spaceKey, ConfluenceUser user)
    {
        accessGuard.requireView(user, spaceKey);
        List<Calendar> result = new ArrayList<>();
        for (Calendar calendar : ao.find(Calendar.class))
        {
            if (spaceKey.equals(calendar.getSpaceKey()))
            {
                result.add(calendar);
            }
        }
        return result;
    }

    @Override
    public Calendar getOrCreateDefault(String spaceKey, ConfluenceUser user)
    {
        Calendar oldest = null;
        for (Calendar calendar : ao.find(Calendar.class))
        {
            if (spaceKey.equals(calendar.getSpaceKey()) && (oldest == null || calendar.getID() < oldest.getID()))
            {
                oldest = calendar;
            }
        }
        if (oldest != null)
        {
            accessGuard.requireView(user, spaceKey);
            return oldest;
        }
        return create(spaceKey, DEFAULT_CALENDAR_NAME, null, user);
    }

    @Override
    public Calendar get(int id, ConfluenceUser user)
    {
        Calendar calendar = findById(id);
        accessGuard.requireView(user, calendar.getSpaceKey());
        return calendar;
    }

    @Override
    public Calendar create(String spaceKey, String name, String description, ConfluenceUser user)
    {
        accessGuard.requireEdit(user, spaceKey);
        Calendar calendar = ao.create(Calendar.class);
        calendar.setSpaceKey(spaceKey);
        calendar.setName(name);
        calendar.setDescription(description);
        calendar.setCreatorKey(user == null ? null : user.getName());
        calendar.setCreationDate(new Date());
        calendar.save();
        return calendar;
    }

    @Override
    public Calendar update(int id, String name, String description, ConfluenceUser user)
    {
        Calendar calendar = findById(id);
        accessGuard.requireEdit(user, calendar.getSpaceKey());
        calendar.setName(name);
        calendar.setDescription(description);
        calendar.save();
        return calendar;
    }

    @Override
    public void delete(int id, ConfluenceUser user)
    {
        Calendar calendar = findById(id);
        accessGuard.requireEdit(user, calendar.getSpaceKey());
        for (Event event : ao.find(Event.class))
        {
            if (event.getCalendar() != null && event.getCalendar().getID() == id)
            {
                ao.delete(event);
            }
        }
        ao.delete(calendar);
    }

    private Calendar findById(int id)
    {
        for (Calendar calendar : ao.find(Calendar.class))
        {
            if (calendar.getID() == id)
            {
                return calendar;
            }
        }
        throw new NotFoundException("Calendar not found: " + id);
    }
}
