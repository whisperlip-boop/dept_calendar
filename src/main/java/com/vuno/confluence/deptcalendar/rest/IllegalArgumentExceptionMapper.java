package com.vuno.confluence.deptcalendar.rest;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.Collections;

@Provider
public class IllegalArgumentExceptionMapper implements ExceptionMapper<IllegalArgumentException>
{
    @Override
    public Response toResponse(IllegalArgumentException exception)
    {
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(Collections.singletonMap("error", exception.getMessage()))
                .build();
    }
}
