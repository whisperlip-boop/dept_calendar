package com.vuno.confluence.deptcalendar.model;

/**
 * The per-space page that hosts the calendar macro. It is created on demand by the space
 * sidebar link and doubles as the permission anchor: its Confluence restrictions decide
 * who may read the space's events and who may change them.
 */
public final class CalendarPage
{
    public static final String TITLE = "Dept Calendar";

    private CalendarPage()
    {
    }
}
