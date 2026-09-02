(function ($) {
    'use strict';

    // Display order only — most-used first. The type picker and the legend both follow
    // this; the server's EventType enum is unordered as far as the UI is concerned.
    var EVENT_TYPES = ['WORK', 'DEVELOP', 'TESTING', 'EVENT', 'MEETING', 'LEAVE', 'TRAVEL', 'BIRTHDAY'];
    var EVENT_TYPE_ICONS = {
        LEAVE: 'leave.svg',
        TRAVEL: 'travel.svg',
        BIRTHDAY: 'birthday.svg',
        EVENT: 'event.svg',
        MEETING: 'meeting.svg',
        WORK: 'work.svg',
        DEVELOP: 'development.svg',
        TESTING: 'testing.svg'
    };
    var REST_BASE = AJS.contextPath() + '/rest/dept-calendar/1.0';
    var CONFLUENCE_SEARCH_URL = AJS.contextPath() + '/rest/api/search';
    var EVENT_TYPE_ICON_BASE = AJS.contextPath() +
        '/download/resources/com.vuno.confluence.dept_calendar:dept_calendar-resources/images/event-types/';

    function eventTypeIconUrl(type) {
        return EVENT_TYPE_ICON_BASE + (EVENT_TYPE_ICONS[type] || EVENT_TYPE_ICONS.EVENT);
    }

    function eventTypeLabel(type) {
        return type.charAt(0) + type.slice(1).toLowerCase();
    }

    // "Who" is a comma-separated list of Confluence usernames, which here are the local
    // part of the account's email. An address typed in by hand is cut at the "@" so the
    // calendar reads the same either way.
    function formatWho(who) {
        if (!who) {
            return '';
        }
        return who.split(',')
            .map(function (name) { return name.trim().split('@')[0].trim(); })
            .filter(function (name) { return name.length > 0; })
            .join(', ');
    }

    function eventLabel(event) {
        var who = formatWho(event.who);
        return who ? event.what + ' (' + who + ')' : event.what;
    }

    // Which types the viewer has switched off. Kept per space in localStorage so the
    // choice survives a reload; it is a display preference only, never a permission.
    function hiddenTypesStorageKey(spaceKey) {
        return 'dept-calendar.hiddenTypes.' + (spaceKey || '');
    }

    function loadHiddenTypes(spaceKey) {
        try {
            var raw = window.localStorage.getItem(hiddenTypesStorageKey(spaceKey));
            var parsed = raw ? JSON.parse(raw) : [];
            return $.isArray(parsed) ? parsed : [];
        } catch (e) {
            return [];
        }
    }

    function saveHiddenTypes(spaceKey, hiddenTypes) {
        try {
            window.localStorage.setItem(hiddenTypesStorageKey(spaceKey), JSON.stringify(hiddenTypes));
        } catch (e) {
            // Private browsing or a browser with site data blocked — the filter still
            // works for this page view, it just will not be remembered.
        }
    }

    var MONTH_SHORT = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    var MONTH_LONG = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];

    // Converts a Confluence "date.format" Java SimpleDateFormat pattern (e.g. "MMM dd, yyyy")
    // into the token syntax the AUI date picker expects (e.g. "M dd, yy").
    function javaDateFormatToAuiFormat(javaFormat) {
        if (!javaFormat) {
            return 'M dd, yy';
        }
        return javaFormat.replace(/[yMd]+/g, function (token) {
            var ch = token.charAt(0);
            var len = token.length;
            if (ch === 'y') {
                return len >= 4 ? 'yy' : 'y';
            }
            if (ch === 'M') {
                if (len >= 4) { return 'MM'; }
                if (len === 3) { return 'M'; }
                return 'mm';
            }
            return len >= 2 ? 'dd' : 'd';
        });
    }

    var AUI_DATE_FORMAT = javaDateFormatToAuiFormat(window.AJS && AJS.Meta ? AJS.Meta.get('date.format') : null);

    function formatDateWithAuiFormat(date, format) {
        return format.replace(/yy|y|MM|M|mm|m|dd|d|[^a-zA-Z]+/g, function (token) {
            switch (token) {
                case 'yy': return '' + date.getFullYear();
                case 'y': return '' + date.getFullYear();
                case 'MM': return MONTH_LONG[date.getMonth()];
                case 'M': return MONTH_SHORT[date.getMonth()];
                case 'mm': return pad(date.getMonth() + 1);
                case 'm': return '' + (date.getMonth() + 1);
                case 'dd': return pad(date.getDate());
                case 'd': return '' + date.getDate();
                default: return token;
            }
        });
    }

    function parseDateWithAuiFormat(str, format) {
        var groups = [];
        var pattern = format.replace(/yy|y|MM|M|mm|m|dd|d|[^a-zA-Z]+/g, function (token) {
            switch (token) {
                case 'yy': groups.push('year'); return '(\\d{4})';
                case 'y': groups.push('year'); return '(\\d{1,4})';
                case 'MM': groups.push('monthLong'); return '(' + MONTH_LONG.join('|') + ')';
                case 'M': groups.push('monthShort'); return '(' + MONTH_SHORT.join('|') + ')';
                case 'mm': groups.push('month'); return '(\\d{2})';
                case 'm': groups.push('month'); return '(\\d{1,2})';
                case 'dd': groups.push('day'); return '(\\d{2})';
                case 'd': groups.push('day'); return '(\\d{1,2})';
                default:
                    return token.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
            }
        });
        var match = new RegExp('^' + pattern + '$').exec((str || '').trim());
        if (!match) {
            return null;
        }
        var year = null, month = null, day = null;
        groups.forEach(function (name, idx) {
            var value = match[idx + 1];
            if (name === 'year') {
                year = parseInt(value, 10);
            } else if (name === 'monthLong') {
                month = MONTH_LONG.indexOf(value);
            } else if (name === 'monthShort') {
                month = MONTH_SHORT.indexOf(value);
            } else if (name === 'month') {
                month = parseInt(value, 10) - 1;
            } else if (name === 'day') {
                day = parseInt(value, 10);
            }
        });
        if (year === null || month === null || day === null) {
            return null;
        }
        return new Date(year, month, day);
    }

    function pad(n) { return n < 10 ? '0' + n : '' + n; }

    function toIsoDate(date) {
        return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate());
    }

    // Repeat rules use the same minimal RRULE subset the server stores:
    // "FREQ=WEEKLY" or "FREQ=MONTHLY;UNTIL=20261231".
    function buildRepeatRule(frequency, untilDate) {
        if (!frequency) {
            return null;
        }
        var rule = 'FREQ=' + frequency;
        if (untilDate) {
            rule += ';UNTIL=' + untilDate.getFullYear() + pad(untilDate.getMonth() + 1) + pad(untilDate.getDate());
        }
        return rule;
    }

    function parseRepeatRule(rule) {
        var parsed = { frequency: '', until: null };
        if (!rule) {
            return parsed;
        }
        rule.split(';').forEach(function (part) {
            var separator = part.indexOf('=');
            if (separator < 0) {
                return;
            }
            var key = part.substring(0, separator).trim().toUpperCase();
            var value = part.substring(separator + 1).trim();
            if (key === 'FREQ') {
                parsed.frequency = value.toUpperCase();
            } else if (key === 'UNTIL' && /^\d{8}$/.test(value)) {
                parsed.until = new Date(
                    parseInt(value.substring(0, 4), 10),
                    parseInt(value.substring(4, 6), 10) - 1,
                    parseInt(value.substring(6, 8), 10));
            }
        });
        return parsed;
    }

    function startOfDay(date) {
        var d = new Date(date);
        d.setHours(0, 0, 0, 0);
        return d;
    }

    function addDays(date, n) {
        var d = new Date(date);
        d.setDate(d.getDate() + n);
        return d;
    }

    function startOfMonth(year, month) {
        return new Date(year, month, 1);
    }

    function escapeHtml(value) {
        return $('<div></div>').text(value === null || value === undefined ? '' : value).html();
    }

    function ajax(method, url, data) {
        return $.ajax({
            url: url,
            type: method,
            contentType: 'application/json',
            data: data ? JSON.stringify(data) : undefined,
            dataType: 'json'
        });
    }

    // Marks a dialog field as invalid with a red border, cleared the moment the user
    // starts fixing it.
    function markFieldError($field) {
        $field.addClass('dept-calendar-field-error')
            .one('input change', function () {
                $field.removeClass('dept-calendar-field-error');
            });
    }

    function showError(xhr) {
        var message = 'Request failed';
        try {
            var body = JSON.parse(xhr.responseText);
            if (body && body.error) {
                message = body.error;
            }
        } catch (e) {
            // ignore parse failure, use default message
        }
        if (window.AJS && AJS.flag) {
            AJS.flag({ type: 'error', title: 'Dept Calendar', body: escapeHtml(message), close: 'auto' });
        } else {
            window.alert(message);
        }
    }

    function searchConfluenceUsers(query) {
        var cql = 'user.fullname~"' + query.replace(/"/g, '\\"') + '*"';
        return $.ajax({
            url: CONFLUENCE_SEARCH_URL,
            type: 'GET',
            data: { cql: cql, limit: 8 },
            dataType: 'json'
        });
    }

    // Attaches a lightweight comma-separated user typeahead to a text input,
    // backed by Confluence's own user-search CQL endpoint.
    function attachUserAutocomplete($input) {
        var timer = null;
        var $dropdown = null;

        function closeDropdown() {
            if ($dropdown) {
                $dropdown.remove();
                $dropdown = null;
            }
            $(document).off('click.dept-calendar-who-autocomplete');
        }

        function currentToken() {
            var parts = ($input.val() || '').split(',');
            return parts[parts.length - 1].trim();
        }

        // Stores the username, not the display name: that is what the calendar shows in
        // the "(bskim, test)" suffix, and it stays stable if someone renames themselves.
        function selectUser(user) {
            var parts = ($input.val() || '').split(',');
            parts[parts.length - 1] = ' ' + user.username;
            $input.val(parts.join(',').replace(/^,\s*/, '') + ', ');
            closeDropdown();
            $input.focus();
        }

        function showResults(results) {
            closeDropdown();
            if (!results.length) {
                return;
            }
            $dropdown = $('<div class="dept-calendar-user-autocomplete"></div>');
            results.forEach(function (result) {
                var user = result.user;
                if (!user) {
                    return;
                }
                $('<div class="dept-calendar-user-autocomplete-item"></div>')
                    .text(user.displayName + ' (' + user.username + ')')
                    .on('mousedown', function (e) {
                        e.preventDefault();
                        selectUser(user);
                    })
                    .appendTo($dropdown);
            });
            $('body').append($dropdown);
            var offset = $input.offset();
            $dropdown.css({
                position: 'absolute',
                top: offset.top + $input.outerHeight(),
                left: offset.left,
                minWidth: $input.outerWidth(),
                zIndex: 4000
            });
            setTimeout(function () {
                $(document).on('click.dept-calendar-who-autocomplete', closeDropdown);
            }, 0);
        }

        $input.on('input', function () {
            var token = currentToken();
            clearTimeout(timer);
            if (token.length < 2) {
                closeDropdown();
                return;
            }
            timer = setTimeout(function () {
                searchConfluenceUsers(token)
                    .done(function (data) {
                        showResults(data.results || []);
                    })
                    .fail(closeDropdown);
            }, 250);
        });
    }

    function CalendarApp(container) {
        this.container = $(container);
        this.spaceKey = this.container.data('space-key') || null;
        this.view = this.container.data('view') || 'month';
        // Optional macro filter: show only these event types on this page.
        this.eventTypes = (this.container.data('event-types') || '')
            .toString()
            .split(',')
            .map(function (s) { return s.trim().toUpperCase(); })
            .filter(function (s) { return EVENT_TYPES.indexOf(s) >= 0; });
        this.cursorDate = startOfDay(new Date());
        // The legend lists whatever this embed is allowed to show; the macro filter, when
        // present, is a hard limit the viewer's own toggles work inside of. Kept in
        // EVENT_TYPES order so the legend reads the same however the macro was written.
        var allowed = this.eventTypes;
        this.availableTypes = allowed.length
            ? EVENT_TYPES.filter(function (type) { return allowed.indexOf(type) >= 0; })
            : EVENT_TYPES;
        this.hiddenTypes = loadHiddenTypes(this.spaceKey).filter(function (type) {
            return EVENT_TYPES.indexOf(type) >= 0;
        });
        // Assume read-only until the server says otherwise, so the write controls never
        // flash into view for someone the calendar page's restrictions exclude.
        this.canEdit = false;

        var self = this;
        ajax('GET', REST_BASE + '/permission?spaceKey=' + encodeURIComponent(this.spaceKey))
            .done(function (permission) {
                self.canEdit = !!(permission && permission.canEdit);
            })
            .always(function () {
                self.render();
            });
    }

    CalendarApp.prototype.render = function () {
        this.container.empty();
        this.container.toggleClass('dept-calendar-readonly', !this.canEdit);
        this.toolbar = $('<div class="dept-calendar-toolbar"></div>').appendTo(this.container);
        this.body = $('<div class="dept-calendar-body"></div>').appendTo(this.container);
        this.legend = $('<div class="dept-calendar-legend"></div>').appendTo(this.container);
        this.renderToolbar();
        this.renderLegend();
        this.renderView();
    };

    // One chip per event type, coloured like the bars in the grid so the legend doubles
    // as the key. Clicking a chip only re-draws what is already loaded — the fetch does
    // not depend on the toggles.
    CalendarApp.prototype.renderLegend = function () {
        var self = this;
        this.legend.empty();

        // A single-type embed needs no key and has nothing to filter.
        if (this.availableTypes.length < 2) {
            return;
        }

        this.availableTypes.forEach(function (type) {
            var isOn = self.hiddenTypes.indexOf(type) < 0;
            // The chip stays neutral and carries a colour swatch rather than taking the
            // type colour itself: the supplied icons are dark navy and would vanish on it.
            $('<button type="button" class="dept-calendar-legend-item"></button>')
                .toggleClass('dept-calendar-legend-off', !isOn)
                .attr('aria-pressed', isOn ? 'true' : 'false')
                .append($('<span class="dept-calendar-legend-swatch"></span>')
                    .addClass('dept-calendar-type-' + type.toLowerCase()))
                .append($('<img class="dept-calendar-legend-icon">').attr('src', eventTypeIconUrl(type)))
                .append($('<span></span>').text(eventTypeLabel(type)))
                .on('click', function () {
                    self.toggleType(type);
                })
                .appendTo(self.legend);
        });
    };

    CalendarApp.prototype.toggleType = function (type) {
        var idx = this.hiddenTypes.indexOf(type);
        if (idx < 0) {
            this.hiddenTypes.push(type);
        } else {
            this.hiddenTypes.splice(idx, 1);
        }
        saveHiddenTypes(this.spaceKey, this.hiddenTypes);
        this.renderLegend();
        if (this.range) {
            this.renderGrid(this.range, this.events);
        }
    };

    CalendarApp.prototype.renderToolbar = function () {
        var self = this;
        this.toolbar.empty();

        var todayBtn = $('<button type="button" class="aui-button">Today</button>').on('click', function () {
            self.cursorDate = startOfDay(new Date());
            self.renderView();
        });
        var prevBtn = $('<button type="button" class="aui-button aui-button-subtle">&lsaquo;</button>').on('click', function () {
            self.shiftCursor(-1);
        });
        var nextBtn = $('<button type="button" class="aui-button aui-button-subtle">&rsaquo;</button>').on('click', function () {
            self.shiftCursor(1);
        });
        this.labelEl = $('<span class="dept-calendar-label"></span>');

        this.toolbar.append(todayBtn, prevBtn, nextBtn, this.labelEl);

        var viewSelect = $('<select class="select dept-calendar-view-select">' +
            '<option value="month">Month</option><option value="week">Week</option></select>')
            .val(this.view)
            .on('change', function () {
                self.view = $(this).val();
                self.renderView();
            });
        this.toolbar.append(viewSelect);

        if (this.canEdit) {
            var addEventBtn = $('<button type="button" class="aui-button aui-button-primary">Add Event</button>').on('click', function () {
                self.openEventDialog(null, self.cursorDate);
            });
            this.toolbar.append(addEventBtn);
        }
    };

    CalendarApp.prototype.shiftCursor = function (direction) {
        if (this.view === 'week') {
            this.cursorDate = addDays(this.cursorDate, direction * 7);
        } else {
            var d = new Date(this.cursorDate);
            d.setDate(1);
            d.setMonth(d.getMonth() + direction);
            this.cursorDate = d;
        }
        this.renderView();
    };

    CalendarApp.prototype.renderView = function () {
        var self = this;
        var range = this.view === 'week' ? this.getWeekRange() : this.getMonthRange();
        this.labelEl.text(range.label);

        var url = REST_BASE + '/event?spaceKey=' + encodeURIComponent(this.spaceKey) +
            '&start=' + encodeURIComponent(range.start.toISOString()) +
            '&end=' + encodeURIComponent(range.end.toISOString());
        if (this.eventTypes.length) {
            url += '&eventTypes=' + encodeURIComponent(this.eventTypes.join(','));
        }

        ajax('GET', url)
            .done(function (events) {
                // Held so a legend toggle can redraw without another round trip.
                self.range = range;
                self.events = events;
                self.renderGrid(range, events);
            })
            .fail(showError);
    };

    CalendarApp.prototype.getMonthRange = function () {
        var year = this.cursorDate.getFullYear();
        var month = this.cursorDate.getMonth();
        var first = startOfMonth(year, month);
        var gridStart = addDays(first, -first.getDay());
        var last = new Date(year, month + 1, 0);
        var gridEnd = addDays(last, 6 - last.getDay());
        return {
            start: gridStart,
            end: addDays(gridEnd, 1),
            days: this.enumerateDays(gridStart, gridEnd),
            label: first.toLocaleString(undefined, { month: 'long', year: 'numeric' })
        };
    };

    CalendarApp.prototype.getWeekRange = function () {
        var start = addDays(this.cursorDate, -this.cursorDate.getDay());
        var end = addDays(start, 6);
        return {
            start: start,
            end: addDays(end, 1),
            days: this.enumerateDays(start, end),
            label: toIsoDate(start) + ' ~ ' + toIsoDate(end)
        };
    };

    CalendarApp.prototype.enumerateDays = function (start, end) {
        var days = [];
        var cursor = start;
        while (cursor <= end) {
            days.push(new Date(cursor));
            cursor = addDays(cursor, 1);
        }
        return days;
    };

    CalendarApp.prototype.eventCoversDay = function (event, day) {
        var dayStart = startOfDay(day).getTime();
        var dayEnd = addDays(startOfDay(day), 1).getTime();
        var eventStart = new Date(event.start).getTime();
        var eventEnd = new Date(event.end).getTime();
        return eventStart < dayEnd && eventEnd >= dayStart;
    };

    CalendarApp.prototype.renderGrid = function (range, events) {
        var self = this;
        this.body.empty();

        var visibleEvents = events.filter(function (event) {
            return self.hiddenTypes.indexOf(event.eventType) < 0;
        });

        var wrapper = $('<div class="dept-calendar-grid-wrapper"></div>').addClass('dept-calendar-grid-' + this.view);

        var header = $('<div class="dept-calendar-grid-header"></div>');
        ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].forEach(function (day) {
            header.append($('<div class="dept-calendar-headcell"></div>').text(day));
        });
        wrapper.append(header);

        for (var i = 0; i < range.days.length; i += 7) {
            wrapper.append(self.renderWeek(range.days.slice(i, i + 7), visibleEvents));
        }

        this.body.append(wrapper);
    };

    // Lays out one week with plain absolute positioning (every offset computed in JS,
    // in px/%) rather than CSS Grid row-spanning, which proved unreliable across
    // browsers when the number of lanes varies per week. Day cells form the background
    // grid; event bars are a separate absolutely-positioned overlay so a multi-day
    // event renders as one continuous bar instead of per-day fragments.
    CalendarApp.prototype.renderWeek = function (weekDays, events) {
        var self = this;
        var todayIso = toIsoDate(new Date());
        var colWidthPct = 100 / 7;

        var segments = [];
        events.forEach(function (event) {
            var coveredIdx = [];
            weekDays.forEach(function (day, idx) {
                if (self.eventCoversDay(event, day)) {
                    coveredIdx.push(idx);
                }
            });
            if (!coveredIdx.length) {
                return;
            }
            segments.push({
                event: event,
                startCol: Math.min.apply(null, coveredIdx),
                endCol: Math.max.apply(null, coveredIdx)
            });
        });

        segments.sort(function (a, b) {
            if (a.startCol !== b.startCol) {
                return a.startCol - b.startCol;
            }
            return (b.endCol - b.startCol) - (a.endCol - a.startCol);
        });

        var laneEnds = [];
        segments.forEach(function (segment) {
            var laneIndex = -1;
            for (var l = 0; l < laneEnds.length; l++) {
                if (laneEnds[l] < segment.startCol) {
                    laneIndex = l;
                    break;
                }
            }
            if (laneIndex === -1) {
                laneIndex = laneEnds.length;
                laneEnds.push(-1);
            }
            laneEnds[laneIndex] = segment.endCol;
            segment.lane = laneIndex;
        });

        // Bars start just under the day number, and the row only grows once the lanes
        // need more than the minimum height — so a row with events has no dead space
        // between the date and its first bar.
        var laneCount = laneEnds.length;
        var laneRowPx = 22;
        var dayNumberPx = 22;
        var minWeekHeightPx = this.view === 'week' ? 200 : 90;
        var weekHeightPx = Math.max(minWeekHeightPx, dayNumberPx + laneCount * laneRowPx + 4);
        var week = $('<div class="dept-calendar-week"></div>').css({
            position: 'relative',
            height: weekHeightPx + 'px'
        });

        weekDays.forEach(function (day, idx) {
            var iso = toIsoDate(day);
            var cell = $('<div class="dept-calendar-daycell"></div>')
                .toggleClass('dept-calendar-today', iso === todayIso)
                .attr('data-date', iso)
                .css({
                    position: 'absolute',
                    left: (idx * colWidthPct) + '%',
                    width: colWidthPct + '%',
                    top: 0,
                    height: weekHeightPx + 'px'
                });
            cell.append($('<div class="dept-calendar-daynum"></div>').text(day.getDate()));
            if (self.canEdit) {
                cell.on('click', function () {
                    self.openEventDialog(null, day);
                });
            }
            week.append(cell);
        });

        segments.forEach(function (segment) {
            var bar = $('<div class="dept-calendar-eventbar"></div>')
                .addClass('dept-calendar-type-' + segment.event.eventType.toLowerCase())
                .text(eventLabel(segment.event))
                .css({
                    position: 'absolute',
                    left: (segment.startCol * colWidthPct) + '%',
                    width: ((segment.endCol - segment.startCol + 1) * colWidthPct) + '%',
                    top: (dayNumberPx + segment.lane * laneRowPx) + 'px',
                    height: (laneRowPx - 2) + 'px'
                })
                .on('click', function (e) {
                    e.stopPropagation();
                    // The popup only offers Edit/Delete, so it has nothing to show a
                    // read-only viewer — the bar already carries the event's title.
                    if (self.canEdit) {
                        self.openEventPopup(segment.event, this);
                    }
                });
            week.append(bar);
        });

        return week;
    };

    CalendarApp.prototype.openEventPopup = function (event, targetEl) {
        var self = this;
        $('.dept-calendar-event-popup').remove();

        var popup = $('<div class="dept-calendar-event-popup"></div>');
        popup.append($('<div class="dept-calendar-event-popup-title"></div>').text(eventLabel(event)));

        function editButton(label, scope) {
            return $('<button type="button" class="aui-button aui-button-subtle"></button>')
                .text(label)
                .on('click', function (e) {
                    e.stopPropagation();
                    popup.remove();
                    self.openEventDialog(event, null, scope);
                });
        }

        function deleteButton(label, scope, confirmMessage) {
            return $('<button type="button" class="aui-button aui-button-subtle"></button>')
                .text(label)
                .on('click', function (e) {
                    e.stopPropagation();
                    popup.remove();
                    if (!window.confirm(confirmMessage)) {
                        return;
                    }
                    var url = REST_BASE + '/event/' + event.id + '?scope=' + scope;
                    if (scope === 'occurrence') {
                        url += '&occurrenceStart=' + encodeURIComponent(event.occurrenceStart || event.start);
                    }
                    ajax('DELETE', url).done(function () {
                        self.renderView();
                    }).fail(showError);
                });
        }

        if (event.recurring) {
            popup.append(
                $('<div class="dept-calendar-event-popup-group"></div>')
                    .append($('<span class="dept-calendar-event-popup-scope"></span>').text('This occurrence'))
                    .append(editButton('Edit', 'occurrence'))
                    .append(deleteButton('Delete', 'occurrence', 'Delete this occurrence of "' + event.what + '"?')),
                $('<div class="dept-calendar-event-popup-group"></div>')
                    .append($('<span class="dept-calendar-event-popup-scope"></span>').text('Entire series'))
                    .append(editButton('Edit', 'series'))
                    .append(deleteButton('Delete', 'series', 'Delete the entire repeating series "' + event.what + '"?'))
            );
        } else {
            popup.append(
                editButton('Edit', 'series'),
                deleteButton('Delete', 'series', 'Delete "' + event.what + '"?')
            );
        }

        $('body').append(popup);

        var offset = $(targetEl).offset();
        popup.css({ position: 'absolute', top: offset.top + $(targetEl).outerHeight(), left: offset.left, zIndex: 3000 });

        setTimeout(function () {
            $(document).one('click.dept-calendar-popup', function () {
                popup.remove();
            });
        }, 0);
    };

    // editScope is 'series' (default) or 'occurrence'. Editing a single occurrence of a
    // repeating event detaches it into a standalone event, so the repeat fields are hidden
    // and the clicked occurrence's dates are prefilled instead of the series' own dates.
    CalendarApp.prototype.openEventDialog = function (existingEvent, defaultDate, editScope) {
        var self = this;
        var dialogId = 'dept-calendar-event-dialog';
        $('#' + dialogId).remove();

        var isEdit = !!existingEvent;
        var scope = editScope === 'occurrence' ? 'occurrence' : 'series';
        var editingSeries = isEdit && scope === 'series';
        var prefillStart = editingSeries && existingEvent.seriesStart ? existingEvent.seriesStart : (existingEvent ? existingEvent.start : null);
        var prefillEnd = editingSeries && existingEvent.seriesEnd ? existingEvent.seriesEnd : (existingEvent ? existingEvent.end : null);
        var defaultStart = prefillStart ? new Date(prefillStart) : (defaultDate || new Date());
        var defaultEnd = prefillEnd ? new Date(prefillEnd) : (defaultDate || new Date());

        var html =
            '<section id="' + dialogId + '" class="aui-dialog2 aui-dialog2-small" role="dialog">' +
            '  <header class="aui-dialog2-header"><h2 class="aui-dialog2-header-main">' + (isEdit ? 'Edit Event' : 'Add Event') + '</h2></header>' +
            '  <div class="aui-dialog2-content">' +
            '    <form class="aui">' +
            '      <div class="field-group">' +
            '        <label>Event Type</label>' +
            '        <input type="hidden" name="eventType" value="EVENT">' +
            '        <button type="button" class="aui-button dept-calendar-eventtype-trigger">' +
            '          <img class="dept-calendar-type-icon-inline" src="' + eventTypeIconUrl('EVENT') + '">' +
            '          <span class="dept-calendar-eventtype-label">EVENT</span>' +
            '          <span class="dept-calendar-eventtype-caret">&#9662;</span>' +
            '        </button>' +
            '      </div>' +
            '      <div class="field-group"><label>What</label><input class="text" type="text" name="what"></div>' +
            '      <div class="field-group"><label>Who</label><input class="text" type="text" name="who"></div>' +
            '      <div class="field-group"><label for="dept-calendar-allday" style="visibility:hidden;">All day event</label>' +
            '        <input type="checkbox" name="allDay" id="dept-calendar-allday" style="width:auto;margin:0 4px 0 0;vertical-align:middle;">' +
            '        <span class="dept-calendar-allday-clicklabel">All day event</span>' +
            '      </div>' +
            '      <div class="field-group"><label>Start</label>' +
            '        <input class="text dept-calendar-date-field" type="text" name="startDate">' +
            '        <input class="text dept-calendar-time-field" type="text" name="startTime" placeholder="HH:mm">' +
            '      </div>' +
            '      <div class="field-group"><label>End</label>' +
            '        <input class="text dept-calendar-date-field" type="text" name="endDate">' +
            '        <input class="text dept-calendar-time-field" type="text" name="endTime" placeholder="HH:mm">' +
            '      </div>' +
            '      <div class="field-group dept-calendar-repeat-row"><label>Repeat</label>' +
            '        <select class="select" name="repeatFreq">' +
            '          <option value="">Does not repeat</option>' +
            '          <option value="DAILY">Daily</option>' +
            '          <option value="WEEKLY">Weekly</option>' +
            '          <option value="MONTHLY">Monthly</option>' +
            '          <option value="YEARLY">Yearly</option>' +
            '        </select>' +
            '      </div>' +
            '      <div class="field-group dept-calendar-repeat-until-row"><label>Repeat until</label>' +
            '        <input class="text dept-calendar-date-field" type="text" name="repeatUntil" placeholder="(no end)">' +
            '      </div>' +
            '      <div class="field-group"><label>Visible to</label>' +
            '        <select class="select" name="visibility">' +
            '          <option value="SPACE">Everyone who can see this space</option>' +
            '          <option value="PRIVATE">Only me</option>' +
            '        </select>' +
            '      </div>' +
            '    </form>' +
            '  </div>' +
            '  <footer class="aui-dialog2-footer">' +
            '    <div class="aui-dialog2-footer-actions">' +
            '      <button class="aui-button aui-button-primary dept-calendar-dialog-submit">' + (isEdit ? 'Save' : 'Add') + '</button>' +
            '      <button class="aui-button aui-button-link dept-calendar-dialog-cancel">Cancel</button>' +
            '    </div>' +
            '  </footer>' +
            '</section>';

        $('body').append(html);
        var $dialog = $('#' + dialogId);
        var dialog = AJS.dialog2('#' + dialogId);

        var setEventType = function (type) {
            $dialog.find('input[name="eventType"]').val(type);
            $dialog.find('.dept-calendar-eventtype-trigger img').attr('src', eventTypeIconUrl(type));
            $dialog.find('.dept-calendar-eventtype-trigger .dept-calendar-eventtype-label').text(type);
        };

        var $eventTypeMenu = null;
        function closeEventTypeMenu() {
            if ($eventTypeMenu) {
                $eventTypeMenu.remove();
                $eventTypeMenu = null;
            }
            $(document).off('click.dept-calendar-eventtype-menu');
        }
        $dialog.find('.dept-calendar-eventtype-trigger').on('click', function (e) {
            e.preventDefault();
            e.stopPropagation();
            if ($eventTypeMenu) {
                closeEventTypeMenu();
                return;
            }
            var $trigger = $(this);
            $eventTypeMenu = $('<div class="dept-calendar-eventtype-menu"></div>');
            EVENT_TYPES.forEach(function (t) {
                $('<div class="dept-calendar-eventtype-menu-item"></div>')
                    .append($('<img class="dept-calendar-type-icon-inline">').attr('src', eventTypeIconUrl(t)))
                    .append($('<span></span>').text(t))
                    .on('click', function (e2) {
                        e2.stopPropagation();
                        setEventType(t);
                        closeEventTypeMenu();
                    })
                    .appendTo($eventTypeMenu);
            });
            $('body').append($eventTypeMenu);
            var offset = $trigger.offset();
            $eventTypeMenu.css({
                position: 'absolute',
                top: offset.top + $trigger.outerHeight(),
                left: offset.left,
                minWidth: $trigger.outerWidth(),
                zIndex: 4000
            });
            setTimeout(function () {
                $(document).on('click.dept-calendar-eventtype-menu', closeEventTypeMenu);
            }, 0);
        });

        if (existingEvent) {
            setEventType(existingEvent.eventType);
            $dialog.find('input[name="what"]').val(existingEvent.what);
            $dialog.find('input[name="who"]').val(existingEvent.who);
            $dialog.find('input[name="allDay"]').prop('checked', existingEvent.allDay);
            $dialog.find('select[name="visibility"]').val(existingEvent.visibility || 'SPACE');
        }
        $dialog.find('input[name="startDate"]').val(formatDateWithAuiFormat(defaultStart, AUI_DATE_FORMAT));
        $dialog.find('input[name="endDate"]').val(formatDateWithAuiFormat(defaultEnd, AUI_DATE_FORMAT));
        $dialog.find('input[name="startTime"]').val(pad(defaultStart.getHours()) + ':' + pad(defaultStart.getMinutes()));
        $dialog.find('input[name="endTime"]').val(pad(defaultEnd.getHours()) + ':' + pad(defaultEnd.getMinutes()));
        // Initialize each date field's picker with its own options object/call —
        // initializing both fields in a single .datePicker() call caused them to
        // share state and mirror each other's selected date.
        $dialog.find('input[name="startDate"]').datePicker({ overrideBrowserDefault: true, dateFormat: AUI_DATE_FORMAT });
        $dialog.find('input[name="endDate"]').datePicker({ overrideBrowserDefault: true, dateFormat: AUI_DATE_FORMAT });
        $dialog.find('input[name="repeatUntil"]').datePicker({ overrideBrowserDefault: true, dateFormat: AUI_DATE_FORMAT });

        if (scope === 'occurrence') {
            $dialog.find('.dept-calendar-repeat-row, .dept-calendar-repeat-until-row').hide();
        } else {
            var repeat = parseRepeatRule(existingEvent ? existingEvent.repeatRule : null);
            $dialog.find('select[name="repeatFreq"]').val(repeat.frequency);
            if (repeat.until) {
                $dialog.find('input[name="repeatUntil"]').val(formatDateWithAuiFormat(repeat.until, AUI_DATE_FORMAT));
            }
            var toggleRepeatUntil = function () {
                $dialog.find('.dept-calendar-repeat-until-row').toggle(!!$dialog.find('select[name="repeatFreq"]').val());
            };
            $dialog.find('select[name="repeatFreq"]').on('change', toggleRepeatUntil);
            toggleRepeatUntil();
        }

        var toggleTimeFields = function () {
            var isAllDay = $dialog.find('input[name="allDay"]').is(':checked');
            $dialog.find('.dept-calendar-time-field').toggle(!isAllDay);
        };
        $dialog.find('input[name="allDay"]').on('change', toggleTimeFields);
        toggleTimeFields();
        $dialog.find('.dept-calendar-allday-clicklabel').on('click', function () {
            var $checkbox = $dialog.find('input[name="allDay"]');
            $checkbox.prop('checked', !$checkbox.is(':checked')).trigger('change');
        });

        attachUserAutocomplete($dialog.find('input[name="who"]'));

        dialog.show();

        $dialog.find('.dept-calendar-dialog-cancel').on('click', function () {
            dialog.hide();
        });

        function submitEvent() {
            var $what = $dialog.find('input[name="what"]');
            var what = $what.val();
            if (!what || !what.trim()) {
                markFieldError($what);
                $what.focus();
                return;
            }

            var allDay = $dialog.find('input[name="allDay"]').is(':checked');
            var $startDate = $dialog.find('input[name="startDate"]');
            var $endDate = $dialog.find('input[name="endDate"]');
            var startDateObj = parseDateWithAuiFormat($startDate.val(), AUI_DATE_FORMAT);
            var endDateObj = parseDateWithAuiFormat($endDate.val(), AUI_DATE_FORMAT);
            if (!startDateObj || !endDateObj) {
                // The border alone cannot convey the expected pattern, so the dates keep
                // their message as well.
                if (!startDateObj) { markFieldError($startDate); }
                if (!endDateObj) { markFieldError($endDate); }
                window.alert('Please enter valid start/end dates (format: ' + AUI_DATE_FORMAT + ').');
                return;
            }
            var startTimeParts = (allDay ? '00:00' : ($dialog.find('input[name="startTime"]').val() || '00:00')).split(':');
            var endTimeParts = (allDay ? '23:59' : ($dialog.find('input[name="endTime"]').val() || '23:59')).split(':');
            var startInstant = new Date(startDateObj.getFullYear(), startDateObj.getMonth(), startDateObj.getDate(),
                parseInt(startTimeParts[0], 10) || 0, parseInt(startTimeParts[1], 10) || 0, 0);
            var endInstant = new Date(endDateObj.getFullYear(), endDateObj.getMonth(), endDateObj.getDate(),
                parseInt(endTimeParts[0], 10) || 0, parseInt(endTimeParts[1], 10) || 0, allDay ? 59 : 0);
            var payload = {
                spaceKey: self.spaceKey,
                eventType: $dialog.find('input[name="eventType"]').val(),
                what: what,
                who: $dialog.find('input[name="who"]').val(),
                start: startInstant.toISOString(),
                end: endInstant.toISOString(),
                allDay: allDay,
                visibility: $dialog.find('select[name="visibility"]').val(),
                repeatRule: scope === 'occurrence' ? null : buildRepeatRule(
                    $dialog.find('select[name="repeatFreq"]').val(),
                    parseDateWithAuiFormat($dialog.find('input[name="repeatUntil"]').val(), AUI_DATE_FORMAT))
            };
            if (scope === 'occurrence' && existingEvent) {
                payload.occurrenceStart = existingEvent.occurrenceStart || existingEvent.start;
            }

            var request = isEdit
                ? ajax('PUT', REST_BASE + '/event/' + existingEvent.id + '?scope=' + scope, payload)
                : ajax('POST', REST_BASE + '/event', payload);

            request.done(function () {
                dialog.hide();
                self.renderView();
            }).fail(showError);
        }

        $dialog.find('.dept-calendar-dialog-submit').on('click', function () {
            // Anything thrown in here used to surface as a button that simply does
            // nothing, which is the hardest possible symptom to diagnose.
            try {
                submitEvent();
            } catch (e) {
                window.alert('Could not save the event: ' + (e && e.message ? e.message : e));
            }
        });

        dialog.on('hide', function () {
            closeEventTypeMenu();
            $('.dept-calendar-user-autocomplete').remove();
            $dialog.remove();
        });
    };

    AJS.toInit(function () {
        $('.dept-calendar-app').each(function () {
            new CalendarApp(this);
        });
    });
})(AJS.$);
