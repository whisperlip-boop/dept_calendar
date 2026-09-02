package com.vuno.confluence.deptcalendar.macro;

import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.macro.Macro;
import com.atlassian.confluence.macro.MacroExecutionException;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.webresource.api.assembler.PageBuilderService;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Map;

@Named
public class CalendarMacro implements Macro
{
    private final PageBuilderService pageBuilderService;

    @Inject
    public CalendarMacro(@ComponentImport PageBuilderService pageBuilderService)
    {
        this.pageBuilderService = pageBuilderService;
    }

    @Override
    public String execute(Map<String, String> parameters, String body, ConversionContext conversionContext) throws MacroExecutionException
    {
        pageBuilderService.assembler().resources().requireContext("dept_calendar");

        // Every embed shows its own space's calendar; eventTypes narrows it so different
        // pages can present different slices of the same events.
        String view = parameters.getOrDefault("view", "month");
        String eventTypes = parameters.getOrDefault("eventTypes", "");

        return "<div class=\"dept-calendar-app\""
                + " data-space-key=\"" + escape(conversionContext.getSpaceKey()) + "\""
                + " data-view=\"" + escape(view) + "\""
                + " data-event-types=\"" + escape(eventTypes) + "\">"
                + "</div>";
    }

    @Override
    public BodyType getBodyType()
    {
        return BodyType.NONE;
    }

    @Override
    public OutputType getOutputType()
    {
        return OutputType.BLOCK;
    }

    private String escape(String value)
    {
        return value == null ? "" : value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }
}
