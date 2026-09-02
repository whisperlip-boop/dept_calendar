package com.vuno.confluence.deptcalendar.rest;

import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.confluence.user.ConfluenceUser;
import com.vuno.confluence.deptcalendar.ao.Event;
import com.vuno.confluence.deptcalendar.model.EditScope;
import com.vuno.confluence.deptcalendar.model.EventType;
import com.vuno.confluence.deptcalendar.model.EventVisibility;
import com.vuno.confluence.deptcalendar.rest.dto.EventDto;
import com.vuno.confluence.deptcalendar.service.EventOccurrence;
import com.vuno.confluence.deptcalendar.service.EventService;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Path("/event")
public class EventResource
{
    private final EventService eventService;

    @Inject
    public EventResource(EventService eventService)
    {
        this.eventService = eventService;
    }

    /**
     * @param eventTypes optional comma-separated filter, used by macro embeds that only
     *                   want part of the space's calendar
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response list(@QueryParam("spaceKey") String spaceKey,
                          @QueryParam("start") String start,
                          @QueryParam("end") String end,
                          @QueryParam("eventTypes") String eventTypes)
    {
        if (spaceKey == null || spaceKey.isEmpty())
        {
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(Collections.singletonMap("error", "spaceKey is required"))
                    .build();
        }

        Set<EventType> typeFilter = parseEventTypes(eventTypes);
        List<EventDto> dtos = new ArrayList<>();
        for (EventOccurrence occurrence : eventService.findBySpace(spaceKey, parseDate(start), parseDate(end), currentUser()))
        {
            if (typeFilter.isEmpty() || typeFilter.contains(occurrence.getEvent().getEventType()))
            {
                dtos.add(toDto(occurrence));
            }
        }
        return Response.ok(dtos).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(EventDto dto)
    {
        Event event = eventService.create(
                dto.getSpaceKey(),
                EventType.valueOf(dto.getEventType()),
                dto.getWhat(),
                dto.getWho(),
                Date.from(Instant.parse(dto.getStart())),
                Date.from(Instant.parse(dto.getEnd())),
                dto.isAllDay(),
                dto.getRepeatRule(),
                parseVisibility(dto.getVisibility()),
                currentUser());
        return Response.status(Response.Status.CREATED).entity(toDto(event)).build();
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response update(@PathParam("id") int id, @QueryParam("scope") String scope, EventDto dto)
    {
        Event event = eventService.update(
                id,
                EventType.valueOf(dto.getEventType()),
                dto.getWhat(),
                dto.getWho(),
                Date.from(Instant.parse(dto.getStart())),
                Date.from(Instant.parse(dto.getEnd())),
                dto.isAllDay(),
                dto.getRepeatRule(),
                parseVisibility(dto.getVisibility()),
                parseScope(scope),
                parseDate(dto.getOccurrenceStart()),
                currentUser());
        return Response.ok(toDto(event)).build();
    }

    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete(@PathParam("id") int id,
                            @QueryParam("scope") String scope,
                            @QueryParam("occurrenceStart") String occurrenceStart)
    {
        eventService.delete(id, parseScope(scope), parseDate(occurrenceStart), currentUser());
        return Response.noContent().build();
    }

    private ConfluenceUser currentUser()
    {
        return AuthenticatedUserThreadLocal.get();
    }

    private static Set<EventType> parseEventTypes(String eventTypes)
    {
        Set<EventType> types = new LinkedHashSet<>();
        if (eventTypes == null || eventTypes.trim().isEmpty())
        {
            return types;
        }
        for (String type : eventTypes.split(","))
        {
            if (!type.trim().isEmpty())
            {
                types.add(EventType.valueOf(type.trim().toUpperCase()));
            }
        }
        return types;
    }

    private static EventVisibility parseVisibility(String visibility)
    {
        if (visibility == null || visibility.trim().isEmpty())
        {
            return EventVisibility.SPACE;
        }
        return EventVisibility.valueOf(visibility.trim().toUpperCase());
    }

    private static EditScope parseScope(String scope)
    {
        if (scope == null || scope.trim().isEmpty())
        {
            return EditScope.SERIES;
        }
        return EditScope.valueOf(scope.trim().toUpperCase());
    }

    private static Date parseDate(String isoInstant)
    {
        if (isoInstant == null || isoInstant.isEmpty())
        {
            return null;
        }
        return Date.from(Instant.parse(isoInstant));
    }

    private EventDto toDto(EventOccurrence occurrence)
    {
        EventDto dto = toDto(occurrence.getEvent());
        dto.setStart(formatDate(occurrence.getStart()));
        dto.setEnd(formatDate(occurrence.getEnd()));
        dto.setRecurring(occurrence.isRecurring());
        dto.setOccurrenceStart(formatDate(occurrence.getStart()));
        return dto;
    }

    private EventDto toDto(Event event)
    {
        EventDto dto = new EventDto();
        dto.setId(event.getID());
        dto.setSpaceKey(event.getCalendar().getSpaceKey());
        dto.setEventType(event.getEventType().name());
        dto.setWhat(event.getWhat());
        dto.setWho(event.getWho());
        dto.setStart(formatDate(event.getStartDate()));
        dto.setEnd(formatDate(event.getEndDate()));
        dto.setAllDay(event.isAllDay());
        dto.setRepeatRule(event.getRepeatRule());
        dto.setVisibility(event.getVisibility() == null
                ? EventVisibility.SPACE.name()
                : event.getVisibility().name());
        dto.setSeriesStart(formatDate(event.getStartDate()));
        dto.setSeriesEnd(formatDate(event.getEndDate()));
        return dto;
    }

    private static String formatDate(Date date)
    {
        return date == null ? null : date.toInstant().toString();
    }
}
