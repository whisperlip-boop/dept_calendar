package com.vuno.confluence.deptcalendar.rest.dto;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class PermissionDto
{
    private boolean canView;
    private boolean canEdit;

    public boolean isCanView()
    {
        return canView;
    }

    public void setCanView(boolean canView)
    {
        this.canView = canView;
    }

    public boolean isCanEdit()
    {
        return canEdit;
    }

    public void setCanEdit(boolean canEdit)
    {
        this.canEdit = canEdit;
    }
}
