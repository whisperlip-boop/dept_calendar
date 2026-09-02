package com.vuno.confluence.deptcalendar.service;

import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.security.Permission;
import com.atlassian.confluence.security.PermissionManager;
import com.atlassian.confluence.spaces.Space;
import com.atlassian.confluence.spaces.SpaceManager;
import com.atlassian.confluence.user.ConfluenceUser;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.vuno.confluence.deptcalendar.model.CalendarPage;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Decides who may read and change a space's events.
 *
 * <p>When the space's calendar page exists, its Confluence restrictions are the authority
 * — so marking that page "view only" for someone leaves them able to browse events but not
 * add or change them. The page is deliberately the anchor rather than whichever page an
 * embed happens to sit on: otherwise anyone could sidestep the restrictions by putting the
 * macro on a fresh unrestricted page. Before that page exists, space permissions apply.
 */
@Named
public class SpaceAccessGuard
{
    private final SpaceManager spaceManager;
    private final PageManager pageManager;
    private final PermissionManager permissionManager;

    @Inject
    public SpaceAccessGuard(@ComponentImport SpaceManager spaceManager,
                             @ComponentImport PageManager pageManager,
                             @ComponentImport PermissionManager permissionManager)
    {
        this.spaceManager = spaceManager;
        this.pageManager = pageManager;
        this.permissionManager = permissionManager;
    }

    public Space requireSpace(String spaceKey)
    {
        Space space = spaceManager.getSpace(spaceKey);
        if (space == null)
        {
            throw new NotFoundException("Space not found: " + spaceKey);
        }
        return space;
    }

    public boolean canView(ConfluenceUser user, String spaceKey)
    {
        Space space = requireSpace(spaceKey);
        Page calendarPage = calendarPage(spaceKey);
        return calendarPage != null
                ? permissionManager.hasPermission(user, Permission.VIEW, calendarPage)
                : permissionManager.hasPermission(user, Permission.VIEW, space);
    }

    public boolean canEdit(ConfluenceUser user, String spaceKey)
    {
        Space space = requireSpace(spaceKey);
        Page calendarPage = calendarPage(spaceKey);
        return calendarPage != null
                ? permissionManager.hasPermission(user, Permission.EDIT, calendarPage)
                : permissionManager.hasCreatePermission(user, space, Page.class);
    }

    public void requireView(ConfluenceUser user, String spaceKey)
    {
        if (!canView(user, spaceKey))
        {
            throw new PermissionDeniedException("View permission required for space " + spaceKey);
        }
    }

    public void requireEdit(ConfluenceUser user, String spaceKey)
    {
        if (!canEdit(user, spaceKey))
        {
            throw new PermissionDeniedException("Edit permission required for space " + spaceKey);
        }
    }

    private Page calendarPage(String spaceKey)
    {
        return pageManager.getPage(spaceKey, CalendarPage.TITLE);
    }
}
