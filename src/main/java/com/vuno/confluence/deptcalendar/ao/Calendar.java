package com.vuno.confluence.deptcalendar.ao;

import net.java.ao.Entity;
import net.java.ao.schema.Table;

import java.util.Date;

@Table("DC_CALENDAR")
public interface Calendar extends Entity
{
    String getSpaceKey();
    void setSpaceKey(String spaceKey);

    String getName();
    void setName(String name);

    String getDescription();
    void setDescription(String description);

    String getCreatorKey();
    void setCreatorKey(String creatorKey);

    Date getCreationDate();
    void setCreationDate(Date creationDate);
}
