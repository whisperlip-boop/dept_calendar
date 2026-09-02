package com.vuno.confluence.deptcalendar.rest.dto;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class EventDto
{
    private Integer id;
    private String spaceKey;
    private String eventType;
    private String what;
    private String who;
    private String start;
    private String end;
    private boolean allDay;
    private String repeatRule;
    private String visibility;
    private boolean recurring;
    private String occurrenceStart;
    /** The stored event's own dates — differ from start/end when this is an expanded occurrence. */
    private String seriesStart;
    private String seriesEnd;

    public Integer getId()
    {
        return id;
    }

    public void setId(Integer id)
    {
        this.id = id;
    }

    public String getSpaceKey()
    {
        return spaceKey;
    }

    public void setSpaceKey(String spaceKey)
    {
        this.spaceKey = spaceKey;
    }

    public String getEventType()
    {
        return eventType;
    }

    public void setEventType(String eventType)
    {
        this.eventType = eventType;
    }

    public String getWhat()
    {
        return what;
    }

    public void setWhat(String what)
    {
        this.what = what;
    }

    public String getWho()
    {
        return who;
    }

    public void setWho(String who)
    {
        this.who = who;
    }

    public String getStart()
    {
        return start;
    }

    public void setStart(String start)
    {
        this.start = start;
    }

    public String getEnd()
    {
        return end;
    }

    public void setEnd(String end)
    {
        this.end = end;
    }

    public boolean isAllDay()
    {
        return allDay;
    }

    public void setAllDay(boolean allDay)
    {
        this.allDay = allDay;
    }

    public String getRepeatRule()
    {
        return repeatRule;
    }

    public void setRepeatRule(String repeatRule)
    {
        this.repeatRule = repeatRule;
    }

    public String getVisibility()
    {
        return visibility;
    }

    public void setVisibility(String visibility)
    {
        this.visibility = visibility;
    }

    public boolean isRecurring()
    {
        return recurring;
    }

    public void setRecurring(boolean recurring)
    {
        this.recurring = recurring;
    }

    public String getOccurrenceStart()
    {
        return occurrenceStart;
    }

    public void setOccurrenceStart(String occurrenceStart)
    {
        this.occurrenceStart = occurrenceStart;
    }

    public String getSeriesStart()
    {
        return seriesStart;
    }

    public void setSeriesStart(String seriesStart)
    {
        this.seriesStart = seriesStart;
    }

    public String getSeriesEnd()
    {
        return seriesEnd;
    }

    public void setSeriesEnd(String seriesEnd)
    {
        this.seriesEnd = seriesEnd;
    }
}
