package com.vuno.confluence.deptcalendar.model;

/**
 * Who can see an event. Modelled as an enum rather than a boolean so it can grow
 * (groups, named users) without a schema change.
 */
public enum EventVisibility
{
    /** Anyone who can view the space. */
    SPACE,
    /** Only the person who created it. */
    PRIVATE
}
