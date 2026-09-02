package com.vuno.confluence.deptcalendar.action;

import com.atlassian.confluence.core.ConfluenceActionSupport;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.spaces.Space;
import com.atlassian.confluence.spaces.SpaceManager;
import com.atlassian.spring.container.ContainerManager;
import com.vuno.confluence.deptcalendar.model.CalendarPage;

public class CalendarSpaceAction extends ConfluenceActionSupport
{
    private static final String MACRO_BODY =
            "<ac:structured-macro ac:name=\"dept-calendar\"><ac:parameter ac:name=\"view\">month</ac:parameter></ac:structured-macro>";

    private String key;
    private String redirectUrl = "/";

    @Override
    public String execute()
    {
        SpaceManager spaceManager = (SpaceManager) ContainerManager.getComponent("spaceManager");
        Space space = spaceManager.getSpace(key);
        if (space == null)
        {
            return ERROR;
        }

        PageManager pageManager = (PageManager) ContainerManager.getComponent("pageManager");
        Page page = pageManager.getPage(space.getKey(), CalendarPage.TITLE);
        if (page == null)
        {
            page = new Page();
            page.setSpace(space);
            page.setTitle(CalendarPage.TITLE);
            page.setBodyAsString(MACRO_BODY);
            pageManager.saveContentEntity(page, null);
        }

        redirectUrl = page.getUrlPath();
        return SUCCESS;
    }

    public String getKey()
    {
        return key;
    }

    public void setKey(String key)
    {
        this.key = key;
    }

    public String getRedirectUrl()
    {
        return redirectUrl;
    }
}
