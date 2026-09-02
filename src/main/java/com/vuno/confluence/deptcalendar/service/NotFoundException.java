package com.vuno.confluence.deptcalendar.service;

public class NotFoundException extends RuntimeException
{
    public NotFoundException(String message)
    {
        super(message);
    }
}
