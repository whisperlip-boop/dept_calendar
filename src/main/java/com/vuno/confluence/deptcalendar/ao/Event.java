package com.vuno.confluence.deptcalendar.ao;

import com.vuno.confluence.deptcalendar.model.EventType;
import com.vuno.confluence.deptcalendar.model.EventVisibility;
import net.java.ao.Entity;
import net.java.ao.schema.Table;

import java.util.Date;

@Table("DC_EVENT")
public interface Event extends Entity
{
    Calendar getCalendar();
    void setCalendar(Calendar calendar);

    EventType getEventType();
    void setEventType(EventType eventType);

    String getWhat();
    void setWhat(String what);

    String getWho();
    void setWho(String who);

    Date getStartDate();
    void setStartDate(Date startDate);

    Date getEndDate();
    void setEndDate(Date endDate);

    boolean isAllDay();
    void setAllDay(boolean allDay);

    String getRepeatRule();
    void setRepeatRule(String repeatRule);

    /** Comma-separated yyyyMMdd dates skipped by this series (deleted or detached occurrences). */
    String getExceptionDates();
    void setExceptionDates(String exceptionDates);

    /** Null on events created before visibility existed; treat those as SPACE. */
    EventVisibility getVisibility();
    void setVisibility(EventVisibility visibility);

    String getCreatorKey();
    void setCreatorKey(String creatorKey);

    Date getCreationDate();
    void setCreationDate(Date creationDate);

    Date getUpdatedDate();
    void setUpdatedDate(Date updatedDate);
}
