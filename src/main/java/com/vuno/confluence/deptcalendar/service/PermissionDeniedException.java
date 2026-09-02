package com.vuno.confluence.deptcalendar.service;

public class PermissionDeniedException extends RuntimeException
{
    public PermissionDeniedException(String message)
    {
        super(message);
    }
}
