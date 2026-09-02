package com.vuno.confluence.deptcalendar.service;

import com.atlassian.confluence.user.ConfluenceUser;
import com.vuno.confluence.deptcalendar.ao.Calendar;

import java.util.List;

public interface CalendarService
{
    List<Calendar> findBySpace(String spaceKey, ConfluenceUser user);

    /**
     * The space's single calendar, created on first use. Events are no longer grouped
     * into user-managed calendars, but each one still needs a calendar to hang off.
     * Spaces that already have several calendars keep the oldest as the default.
     */
    Calendar getOrCreateDefault(String spaceKey, ConfluenceUser user);

    Calendar get(int id, ConfluenceUser user);

    Calendar create(String spaceKey, String name, String description, ConfluenceUser user);

    Calendar update(int id, String name, String description, ConfluenceUser user);

    void delete(int id, ConfluenceUser user);
}
