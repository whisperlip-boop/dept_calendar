# Dept Calendar

A department calendar plugin for **Confluence Server / Data Center**, built as a replacement
for Atlassian's discontinued Team Calendars.

Built against Confluence **7.12.3**; developed and tested against a Confluence **7.8.1** instance.

## Features

- One calendar per space, created automatically on first use
- Month and week views, with multi-day events drawn as a single continuous bar
- 8 event types — Work, Develop, Testing, Event, Meeting, Leave, Travel, Birthday
- Recurring events (daily / weekly / monthly / yearly, with an optional end date), editable
  and deletable either for a single occurrence or for the whole series
- Per-event visibility: everyone who can see the space, or only the author
- Access follows the space's "Dept Calendar" page restrictions — view-only users can browse
  but not add or change events
- Legend below the calendar doubles as a per-type show/hide filter
- Embeddable anywhere via the `dept-calendar` macro, optionally narrowed to a subset of types

## Installing

Download the jar from the [releases](../../releases) or build it yourself, then upload it
through **Confluence Administration → Manage apps → Upload app**.

## Building

Requires the [Atlassian Plugin SDK](https://developer.atlassian.com/server/framework/atlassian-sdk/)
(8.2.7) and JDK 8.

```bash
atlas-mvn clean package
# -> target/dept_calendar-1.0.0.jar
```

## Macro parameters

| Parameter    | Values                          | Default | Description                              |
|--------------|---------------------------------|---------|------------------------------------------|
| `view`       | `month`, `week`                 | `month` | Which view the embed opens in            |
| `eventTypes` | comma-separated type names      | (all)   | Restricts the embed to these event types |

Use `eventTypes` to show different slices of the same space calendar on different pages,
e.g. `eventTypes=LEAVE,TRAVEL` for an out-of-office page.

## Development notes

`CLAUDE.md` records the scope decisions, the build/deploy procedure used during development,
and the platform quirks that shaped the implementation.
