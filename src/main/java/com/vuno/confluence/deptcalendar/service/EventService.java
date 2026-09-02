package com.vuno.confluence.deptcalendar.service;

import com.atlassian.confluence.user.ConfluenceUser;
import com.vuno.confluence.deptcalendar.ao.Event;
import com.vuno.confluence.deptcalendar.model.EditScope;
import com.vuno.confluence.deptcalendar.model.EventType;
import com.vuno.confluence.deptcalendar.model.EventVisibility;

import java.util.Date;
import java.util.List;

public interface EventService
{
    /**
     * Every event in the space, with recurring ones expanded into one occurrence per
     * repeat date inside the range. Events marked PRIVATE are only returned to the
     * person who created them.
     */
    List<EventOccurrence> findBySpace(String spaceKey, Date start, Date end, ConfluenceUser user);

    Event create(String spaceKey, EventType eventType, String what, String who,
                 Date start, Date end, boolean allDay, String repeatRule,
                 EventVisibility visibility, ConfluenceUser user);

    /**
     * @param scope           SERIES edits the stored event; OCCURRENCE leaves the series
     *                        alone, skips that one date and stores the edited values as a
     *                        standalone event
     * @param occurrenceStart which occurrence to detach; required for OCCURRENCE scope
     * @return the event that now holds the submitted values
     */
    Event update(int id, EventType eventType, String what, String who,
                 Date start, Date end, boolean allDay, String repeatRule,
                 EventVisibility visibility, EditScope scope, Date occurrenceStart, ConfluenceUser user);

    /** SERIES deletes the event; OCCURRENCE only skips {@code occurrenceStart}. */
    void delete(int id, EditScope scope, Date occurrenceStart, ConfluenceUser user);
}
