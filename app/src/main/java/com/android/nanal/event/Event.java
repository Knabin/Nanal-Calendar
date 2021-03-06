// 다 옮김
package com.android.nanal.event;

/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Debug;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Instances;
import androidx.core.content.ContextCompat;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;

import com.android.nanal.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;


// TODO: should Event be Parcelable so it can be passed via Intents?
public class Event implements Cloneable {

    private static final String TAG = "CalEvent";
    private static final boolean PROFILE = false;

    /**
     * The sort order is:
     * 1) events with an earlier start (begin for normal events, startday for allday)
     * 2) events with a later end (end for normal events, endday for allday)
     * 3) the title (unnecessary, but nice)
     * 정렬 순서:
     * 1) 이전에 시작한 이벤트(일반 이벤트의 경우 시작, 종일 이벤트의 경우 시작일)
     * 2) 이후에 끝나는 이벤트(일반 이벤트의 경우 종료, 종일 이벤트의 경우 종료일)
     * 3) 제목 (필요없지만 좋음)
     *
     * The start and end day is sorted first so that all day events are
     * sorted correctly with respect to events that are >24 hours (and
     * therefore show up in the allday area).
     * 시작일과 종료일을 먼저 분류하여, 종일 이벤트(인지 모든 날짜 이벤트인지)가
     * >24시간(종일 이벤트 영역에 표시)인 이벤트와 관련하여 올바르게 정렬되도록 함
     * 종일 이벤트랑 며칠 걸치는 그런 이벤트를 정렬하는 듯
     */
    private static final String SORT_EVENTS_BY =
            "begin ASC, end DESC, title ASC";
    private static final String SORT_ALLDAY_BY =
            "startDay ASC, endDay DESC, title ASC";
    private static final String DISPLAY_AS_ALLDAY = "dispAllday";
    // The projection to use when querying instances to build a list of events
    // 이벤트 리스트를 작성하기 위해서 인스턴스를 쿼리할 때 사용할 projection
    public static final String[] EVENT_PROJECTION = new String[] {
            Instances.TITLE,                 // 0
            Instances.EVENT_LOCATION,        // 1
            Instances.ALL_DAY,               // 2
            Instances.DISPLAY_COLOR,         // 3
            Instances.EVENT_TIMEZONE,        // 4
            Instances.EVENT_ID,              // 5
            Instances.BEGIN,                 // 6
            Instances.END,                   // 7
            Instances._ID,                   // 8
            Instances.START_DAY,             // 9
            Instances.END_DAY,               // 10
            Instances.START_MINUTE,          // 11
            Instances.END_MINUTE,            // 12
            Instances.HAS_ALARM,             // 13
            Instances.RRULE,                 // 14
            Instances.RDATE,                 // 15
            Instances.SELF_ATTENDEE_STATUS,  // 16
            Events.ORGANIZER,                // 17
            Events.GUESTS_CAN_MODIFY,        // 18
            Instances.ALL_DAY + "=1 OR (" + Instances.END + "-" + Instances.BEGIN + ")>="
                    + DateUtils.DAY_IN_MILLIS + " AS " + DISPLAY_AS_ALLDAY, // 19
            "event_id",                      // 20
    };
    private static final String EVENTS_WHERE = DISPLAY_AS_ALLDAY + "=0";
    private static final String ALLDAY_WHERE = DISPLAY_AS_ALLDAY + "=1";
    // The indices for the projection array above.
    // 위의 projection 배열을 위한 indices
    private static final int PROJECTION_TITLE_INDEX = 0;
    private static final int PROJECTION_LOCATION_INDEX = 1;
    private static final int PROJECTION_ALL_DAY_INDEX = 2;
    private static final int PROJECTION_COLOR_INDEX = 3;
    private static final int PROJECTION_TIMEZONE_INDEX = 4;
    private static final int PROJECTION_EVENT_ID_INDEX = 5;
    private static final int PROJECTION_BEGIN_INDEX = 6;
    private static final int PROJECTION_END_INDEX = 7;
    private static final int PROJECTION_START_DAY_INDEX = 9;
    private static final int PROJECTION_END_DAY_INDEX = 10;
    private static final int PROJECTION_START_MINUTE_INDEX = 11;
    private static final int PROJECTION_END_MINUTE_INDEX = 12;
    private static final int PROJECTION_HAS_ALARM_INDEX = 13;
    private static final int PROJECTION_RRULE_INDEX = 14;
    private static final int PROJECTION_RDATE_INDEX = 15;
    private static final int PROJECTION_SELF_ATTENDEE_STATUS_INDEX = 16;
    private static final int PROJECTION_ORGANIZER_INDEX = 17;
    private static final int PROJECTION_GUESTS_CAN_INVITE_OTHERS_INDEX = 18;
    private static final int PROJECTION_DISPLAY_AS_ALLDAY = 19;
    private static final int PROJECTION_EVENTN_ID_INDEX = 21;
    private static String mNoTitleString;
    private static int mNoColorColor;


    public long id;
    public int color;
    public CharSequence title;
    public CharSequence location;
    public boolean allDay;
    public String organizer;
    public boolean guestsCanModify;

    public int startDay;       // start Julian day
    public int endDay;         // end Julian day
    public int startTime;      // Start and end time are in minutes since midnight 시작 및 종료 시간(분 단위, 자정 기준)
    public int endTime;

    public long startMillis;   // UTC milliseconds since the epoch
    public long endMillis;     // UTC milliseconds since the epoch
    public boolean hasAlarm;
    public boolean isRepeating;
    public int selfAttendeeStatus;
    // The coordinates of the event rectangle drawn on the screen.
    // 화면에 그려진 이벤트 사각형의 좌표
    public float left;
    public float right;
    public float top;
    public float bottom;
    // These 4 fields are used for navigating among events within the selected
    // hour in the Day and Week view.
    // 이 네 개의 필드는 Day, Week view에서 선택한 시간 내에 이벤트 간의 탐색에 사용됨
    public Event nextRight;
    public Event nextLeft;
    public Event nextUp;
    public Event nextDown;
    private int mColumn;
    private int mMaxColumns;

    public static final Event newInstance() {
        Event e = new Event();

        e.id = 0;
        e.title = null;
        e.color = 0;
        e.location = null;
        e.allDay = false;
        e.startDay = 0;
        e.endDay = 0;
        e.startTime = 0;
        e.endTime = 0;
        e.startMillis = 0;
        e.endMillis = 0;
        e.hasAlarm = false;
        e.isRepeating = false;
        e.selfAttendeeStatus = Attendees.ATTENDEE_STATUS_NONE;

        return e;
    }

    /**
     * Loads <i>days</i> days worth of instances starting at <i>startDay</i>.
     * startDay에서 시작하는 인스턴스의 days일 단위 로드
     */
    public static void loadEvents(Context context, ArrayList<Event> events, int startDay, int days,
                                  int requestId, AtomicInteger sequenceNumber) {

        if (PROFILE) {
            Debug.startMethodTracing("loadEvents");
        }

        if (Build.VERSION.SDK_INT >= 23 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
            //If permission is not granted then just return.
            // 허가가 나지 않으면 그냥 반환만
            return;
        }

        Cursor cEvents = null;
        Cursor cAllday = null;

        events.clear();
        try {
            int endDay = startDay + days - 1;

            // We use the byDay instances query to get a list of all events for
            // the days we're interested in.
            // The sort order is: events with an earlier start time occur
            // first and if the start times are the same, then events with
            // a later end time occur first. The later end time is ordered
            // first so that long rectangles in the calendar views appear on
            // the left side.  If the start and end times of two events are
            // the same then we sort alphabetically on the title.  This isn't
            // required for correctness, it just adds a nice touch.
            // 관심 있는 날의 모든 이벤트 목록을 얻기 위해 byDay 인스턴스 쿼리를 사용함
            // 정렬 순서: 시작 시간이 더 이른 이벤트가 먼저, 시작 시간이 같으면 종료 시간이 더 늦은 이벤트가 먼저
            //            만약 두 이벤트의 시작 시간과 종료 시간이 같다면 제목을 알파벳순으로 정렬함
            //            정확성을 위해서 필요한 게 아니라 멋진 느낌을 더해 주는 것임

            // Respect the preference to show/hide declined events
            // 거부된 이벤트를 표시/숨기기 설정을 존중함
            SharedPreferences prefs = GeneralPreferences.getSharedPreferences(context);
            boolean hideDeclined = prefs.getBoolean(GeneralPreferences.KEY_HIDE_DECLINED,
                    false);

            String where = EVENTS_WHERE;
            String whereAllday = ALLDAY_WHERE;
            if (hideDeclined) {
                String hideString = " AND " + Instances.SELF_ATTENDEE_STATUS + "!="
                        + Attendees.ATTENDEE_STATUS_DECLINED;
                where += hideString;
                whereAllday += hideString;
            }

            cEvents = instancesQuery(context.getContentResolver(), EVENT_PROJECTION, startDay,
                    endDay, where, null, SORT_EVENTS_BY);
            cAllday = instancesQuery(context.getContentResolver(), EVENT_PROJECTION, startDay,
                    endDay, whereAllday, null, SORT_ALLDAY_BY);

            // Check if we should return early because there are more recent
            // load requests waiting.
            // 더 많은 최근 load 요청이 있기 때문에 일찍 반환해 줘야 하는지 체크함
            if (requestId != sequenceNumber.get()) {
                return;
            }

            buildEventsFromCursor(events, cEvents, context, startDay, endDay);
            buildEventsFromCursor(events, cAllday, context, startDay, endDay);

        } finally {
            if (cEvents != null) {
                cEvents.close();
            }
            if (cAllday != null) {
                cAllday.close();
            }
            if (PROFILE) {
                Debug.stopMethodTracing();
            }
        }
    }

    /**
     * Performs a query to return all visible instances in the given range
     * that match the given selection. This is a blocking function and
     * should not be done on the UI thread. This will cause an expansion of
     * recurring events to fill this time range if they are not already
     * expanded and will slow down for larger time ranges with many
     * recurring events.
     * 지정된 selection과 일치하는 지정된 범위의 모든 visible 인스턴스를 반환하는 쿼리 수행
     * 차단 기능, UI 스레드에서 수행해서는 안 됨
     * 미리 확장돼 있지 않은 경우, 반복 이벤트의 확장이 이 시간 범위를 채우게 될 것이며
     * 반복 이벤트가 많을수록 더 많은 시간을 느리게 할 것임 뭐 그런 느낌인 듯
     *
     * @param cr The ContentResolver to use for the query
     *           쿼리에 사용할 ContenResolver
     * @param projection The columns to return
     * @param startDay The start of the time range to query in UTC millis since
     *            epoch
     *                 쿼리할 시간 범위의 시작(UTC, 밀리초, epoch 기준)
     * @param endDay The end of the time range to query in UTC millis since
     *            epoch
     * @param selection Filter on the query as an SQL WHERE statement
     *                  쿼리를 필터링할 SQL의 WHERE문
     * @param selectionArgs Args to replace any '?'s in the selection
     *                      선택 항목에서 '?'를 대체할 Args
     * @param orderBy How to order the rows as an SQL ORDER BY statement
     *                행을 정렬할 SQL ORDER BY문
     * @return A Cursor of instances matching the selection
     *          선택 항목과 일치하는 인스턴스의 커서
     */
    private static final Cursor instancesQuery(ContentResolver cr, String[] projection,
                                               int startDay, int endDay, String selection, String[] selectionArgs, String orderBy) {
        String WHERE_CALENDARS_SELECTED = Calendars.VISIBLE + "=?";
        String[] WHERE_CALENDARS_ARGS = {"1"};
        String DEFAULT_SORT_ORDER = "begin ASC";

        Uri.Builder builder = Instances.CONTENT_BY_DAY_URI.buildUpon();
        ContentUris.appendId(builder, startDay);
        ContentUris.appendId(builder, endDay);
        if (TextUtils.isEmpty(selection)) {
            selection = WHERE_CALENDARS_SELECTED;
            selectionArgs = WHERE_CALENDARS_ARGS;
        } else {
            selection = "(" + selection + ") AND " + WHERE_CALENDARS_SELECTED;
            if (selectionArgs != null && selectionArgs.length > 0) {
                selectionArgs = Arrays.copyOf(selectionArgs, selectionArgs.length + 1);
                selectionArgs[selectionArgs.length - 1] = WHERE_CALENDARS_ARGS[0];
            } else {
                selectionArgs = WHERE_CALENDARS_ARGS;
            }
        }
        return cr.query(builder.build(), projection, selection, selectionArgs,
                orderBy == null ? DEFAULT_SORT_ORDER : orderBy);
    }

    /**
     * Adds all the events from the cursors to the events list.
     * 커서의 모든 이벤트를 이벤트 리스트에 추가함
     *
     * @param events The list of events
     * @param cEvents Events to add to the list
     * @param context
     * @param startDay
     * @param endDay
     */
    public static void buildEventsFromCursor(
            ArrayList<Event> events, Cursor cEvents, Context context, int startDay, int endDay) {
        if (cEvents == null || events == null) {
            Log.e(TAG, "buildEventsFromCursor: null cursor or null events list!");
            return;
        }

        int count = cEvents.getCount();

        if (count == 0) {
            return;
        }

        Resources res = context.getResources();
        mNoTitleString = res.getString(R.string.no_title_label);
        mNoColorColor = res.getColor(R.color.event_center);
        // Sort events in two passes so we ensure the allday and standard events
        // get sorted in the correct order
        // 이벤트를 두 개의 패스?로 정렬하여 종일 및 기본 이벤트가 올바른 순서로 정렬되도록 함
        cEvents.moveToPosition(-1);
        while (cEvents.moveToNext()) {
            Event e = generateEventFromCursor(cEvents);
            if (e.startDay > endDay || e.endDay < startDay) {
                continue;
            }
            events.add(e);
        }
    }

    /**
     * @param cEvents Cursor pointing at event
     *                이벤트를 가리키는 커서
     * @return An event created from the cursor
     *          커서로부터 만들어진 이벤트
     */
    private static Event generateEventFromCursor(Cursor cEvents) {
        Event e = new Event();

        e.id = cEvents.getLong(PROJECTION_EVENT_ID_INDEX);
        e.title = cEvents.getString(PROJECTION_TITLE_INDEX);
        e.location = cEvents.getString(PROJECTION_LOCATION_INDEX);
        e.allDay = cEvents.getInt(PROJECTION_ALL_DAY_INDEX) != 0;
        e.organizer = cEvents.getString(PROJECTION_ORGANIZER_INDEX);
        e.guestsCanModify = cEvents.getInt(PROJECTION_GUESTS_CAN_INVITE_OTHERS_INDEX) != 0;

        if (e.title == null || e.title.length() == 0) {
            e.title = mNoTitleString;
        }

        if (!cEvents.isNull(PROJECTION_COLOR_INDEX)) {
            // Read the color from the database
            // 데이터베이스에서 색상 읽기
            e.color = Utils.getDisplayColorFromColor(cEvents.getInt(PROJECTION_COLOR_INDEX));
        } else {
            e.color = mNoColorColor;
        }

        long eStart = cEvents.getLong(PROJECTION_BEGIN_INDEX);
        long eEnd = cEvents.getLong(PROJECTION_END_INDEX);

        e.startMillis = eStart;
        e.startTime = cEvents.getInt(PROJECTION_START_MINUTE_INDEX);
        e.startDay = cEvents.getInt(PROJECTION_START_DAY_INDEX);

        e.endMillis = eEnd;
        e.endTime = cEvents.getInt(PROJECTION_END_MINUTE_INDEX);
        e.endDay = cEvents.getInt(PROJECTION_END_DAY_INDEX);

        e.hasAlarm = cEvents.getInt(PROJECTION_HAS_ALARM_INDEX) != 0;

        // Check if this is a repeating event
        // 이 이벤트가 반복적인 이벤트인지 확인함
        String rrule = cEvents.getString(PROJECTION_RRULE_INDEX);
        String rdate = cEvents.getString(PROJECTION_RDATE_INDEX);
        if (!TextUtils.isEmpty(rrule) || !TextUtils.isEmpty(rdate)) {
            e.isRepeating = true;
        } else {
            e.isRepeating = false;
        }

        e.selfAttendeeStatus = cEvents.getInt(PROJECTION_SELF_ATTENDEE_STATUS_INDEX);
        return e;
    }

    /**
     * Computes a position for each event.  Each event is displayed
     * as a non-overlapping rectangle.  For normal events, these rectangles
     * are displayed in separate columns in the week view and day view.  For
     * all-day events, these rectangles are displayed in separate rows along
     * the top.  In both cases, each event is assigned two numbers: N, and
     * Max, that specify that this event is the Nth event of Max number of
     * events that are displayed in a group. The width and position of each
     * rectangle depend on the maximum number of rectangles that occur at
     * the same time.
     * 각 이벤트에 대한 위치를 계산함, 각 이벤트는 겹치지 않는 직사각형으로 표시됨
     * 보통 이벤트의 경우, 직사각형이 주 view와 일 view에서 별도의 열에 표시됨
     * 종일 이벤트의 경우, 직사각형이 상단을 따라 별도의 행으로 표시됨
     * 두 경우 모두 이 이벤트가 그룹에 표시되는 최대 이벤트 수의 N번째 이벤트임을 지정하는 N 및 Max의 두 숫자가 할당됨
     * 각 직사각형의 폭과 위치는 동시에 발생하는 최대 직사각형의 수에 따라 달라짐
     *
     * @param eventsList the list of events, sorted into increasing time order
     *                    증가하는 시간순으로 정렬된 이벤트 리스트
     * @param minimumDurationMillis minimum duration acceptable as cell height of each event
     * rectangle in millisecond. Should be 0 when it is not determined.
     *                              각 이벤트 직사각형의 셀 높이로 허용되는 최소 지속 시간(밀리초)
     *                              결정되지 않은 경우 0이어야 함
     */
    /* package */ public static void computePositions(ArrayList<Event> eventsList,
                                               long minimumDurationMillis) {
        if (eventsList == null) {
            return;
        }

        // Compute the column positions separately for the all-day events
        // 종일 이벤트에 대해 열 위치를 별도로 계산함
        doComputePositions(eventsList, minimumDurationMillis, false);
        doComputePositions(eventsList, minimumDurationMillis, true);
    }

    private static void doComputePositions(ArrayList<Event> eventsList,
                                           long minimumDurationMillis, boolean doAlldayEvents) {
        final ArrayList<Event> activeList = new ArrayList<Event>();
        final ArrayList<Event> groupList = new ArrayList<Event>();

        if (minimumDurationMillis < 0) {
            minimumDurationMillis = 0;
        }

        long colMask = 0;
        int maxCols = 0;
        for (Event event : eventsList) {
            // Process all-day events separately
            // 종일 이벤트를 별도로 처리함
            if (event.drawAsAllday() != doAlldayEvents)
                continue;

            if (!doAlldayEvents) {
                colMask = removeNonAlldayActiveEvents(
                        event, activeList.iterator(), minimumDurationMillis, colMask);
            } else {
                colMask = removeAlldayActiveEvents(event, activeList.iterator(), colMask);
            }

            // If the active list is empty, then reset the max columns, clear
            // the column bit mask, and empty the groupList.
            // 활성된 리스트가? 비어 있으면 최대 열을 재설정하고, 컬럼 비트 마스크를 지운 다음
            // groupList를 비움
            if (activeList.isEmpty()) {
                for (Event ev : groupList) {
                    ev.setMaxColumns(maxCols);
                }
                maxCols = 0;
                colMask = 0;
                groupList.clear();
            }

            // Find the first empty column.  Empty columns are represented by
            // zero bits in the column mask "colMask".
            // 첫 번째 빈 열을 찾음, 빈 열은 열 마스크 "colMask"에 0비트로 표시됨
            int col = findFirstZeroBit(colMask);
            if (col == 64)
                col = 63;
            colMask |= (1L << col);
            event.setColumn(col);
            activeList.add(event);
            groupList.add(event);
            int len = activeList.size();
            if (maxCols < len)
                maxCols = len;
        }
        for (Event ev : groupList) {
            ev.setMaxColumns(maxCols);
        }
    }

    private static long removeAlldayActiveEvents(Event event, Iterator<Event> iter, long colMask) {
        // Remove the inactive allday events. An event on the active list
        // becomes inactive when the end day is less than the current event's
        // start day.
        // 비활성 상태의 종일 이벤트를 제거함, 종료일이 현재 이벤트 시작일보다 작으면
        // 활성 목록의 이벤트가 비활성화됨
        while (iter.hasNext()) {
            final Event active = iter.next();
            if (active.endDay < event.startDay) {
                colMask &= ~(1L << active.getColumn());
                iter.remove();
            }
        }
        return colMask;
    }

    private static long removeNonAlldayActiveEvents(
            Event event, Iterator<Event> iter, long minDurationMillis, long colMask) {
        long start = event.getStartMillis();
        // Remove the inactive events. An event on the active list
        // becomes inactive when its end time is less than or equal to
        // the current event's start time.
        // 비활성 이벤트를 제거함, 활성 목록의 이벤트는 종료 시간이 현재 이벤트의 시작 시간보다
        // 작거나 같으면 비활성화됨
        while (iter.hasNext()) {
            final Event active = iter.next();

            final long duration = Math.max(
                    active.getEndMillis() - active.getStartMillis(), minDurationMillis);
            if ((active.getStartMillis() + duration) <= start) {
                colMask &= ~(1L << active.getColumn());
                iter.remove();
            }
        }
        return colMask;
    }

    public static int findFirstZeroBit(long val) {
        for (int ii = 0; ii < 64; ++ii) {
            if ((val & (1L << ii)) == 0)
                return ii;
        }
        return 64;
    }

    @Override
    public final Object clone() throws CloneNotSupportedException {
        super.clone();
        Event e = new Event();

        e.title = title;
        e.color = color;
        e.location = location;
        e.allDay = allDay;
        e.startDay = startDay;
        e.endDay = endDay;
        e.startTime = startTime;
        e.endTime = endTime;
        e.startMillis = startMillis;
        e.endMillis = endMillis;
        e.hasAlarm = hasAlarm;
        e.isRepeating = isRepeating;
        e.selfAttendeeStatus = selfAttendeeStatus;
        e.organizer = organizer;
        e.guestsCanModify = guestsCanModify;

        return e;
    }

    public final void copyTo(Event dest) {
        dest.id = id;
        dest.title = title;
        dest.color = color;
        dest.location = location;
        dest.allDay = allDay;
        dest.startDay = startDay;
        dest.endDay = endDay;
        dest.startTime = startTime;
        dest.endTime = endTime;
        dest.startMillis = startMillis;
        dest.endMillis = endMillis;
        dest.hasAlarm = hasAlarm;
        dest.isRepeating = isRepeating;
        dest.selfAttendeeStatus = selfAttendeeStatus;
        dest.organizer = organizer;
        dest.guestsCanModify = guestsCanModify;
    }

    public final void dump() {
        Log.e("Cal", "+-----------------------------------------+");
        Log.e("Cal", "+        id = " + id);
        Log.e("Cal", "+     color = " + color);
        Log.e("Cal", "+     title = " + title);
        Log.e("Cal", "+  location = " + location);
        Log.e("Cal", "+    allDay = " + allDay);
        Log.e("Cal", "+  startDay = " + startDay);
        Log.e("Cal", "+    endDay = " + endDay);
        Log.e("Cal", "+ startTime = " + startTime);
        Log.e("Cal", "+   endTime = " + endTime);
        Log.e("Cal", "+ organizer = " + organizer);
        Log.e("Cal", "+  guestwrt = " + guestsCanModify);
    }

    public final boolean intersects(int julianDay, int startMinute,
                                    int endMinute) {
        if (endDay < julianDay) {
            return false;
        }

        if (startDay > julianDay) {
            return false;
        }

        if (endDay == julianDay) {
            if (endTime < startMinute) {
                return false;
            }
            // An event that ends at the start minute should not be considered
            // as intersecting the given time span, but don't exclude
            // zero-length (or very short) events.
            // 시작 분에 끝나는 이벤트는 주어진 시간 범위를 교차하는 것으로 간주해서는 안 되며,
            // 길이가 없거나 길이가 매우 짧은 이벤트를 제외해서는 안 됨
            if (endTime == startMinute
                    && (startTime != endTime || startDay != endDay)) {
                return false;
            }
        }

        if (startDay == julianDay && startTime > endMinute) {
            return false;
        }

        return true;
    }

    /**
     * Returns the event title and location separated by a comma.  If the
     * location is already part of the title (at the end of the title), then
     * just the title is returned.
     * 이벤트 제목과 위치를 쉼표로 구분하며 반환함
     * 만약 위치가 이미 제목(제목의 끝)의 일부인 경우 제목만 반환됨
     *
     * @return the event title and location as a String
     *          이벤트 제목과 위치(String)
     */
    public String getTitleAndLocation() {
        String text = title.toString();

        // Append the location to the title, unless the title ends with the
        // location (for example, "meeting in building 42" ends with the
        // location).
        // 제목이 위치로 끝나지 않는 경우(예: "빌딩 42에서의 미팅"은 위치로 끝나는 경우임)
        // 위치를 제목에 추가함
        if (location != null) {
            String locationString = location.toString();
            if (!text.endsWith(locationString)) {
                text += ", " + locationString;
            }
        }
        return text;
    }

    public int getColumn() {
        return mColumn;
    }

    public void setColumn(int column) {
        mColumn = column;
    }

    public int getMaxColumns() {
        return mMaxColumns;
    }

    public void setMaxColumns(int maxColumns) {
        mMaxColumns = maxColumns;
    }

    public long getStartMillis() {
        return startMillis;
    }

    public void setStartMillis(long startMillis) {
        this.startMillis = startMillis;
    }

    public long getEndMillis() {
        return endMillis;
    }

    public void setEndMillis(long endMillis) {
        this.endMillis = endMillis;
    }

    public boolean drawAsAllday() {
        // Use >= so we'll pick up Exchange allday events
        // >=를 사용하여 종일 이벤트 선택
        return allDay || endMillis - startMillis >= DateUtils.DAY_IN_MILLIS;
    }
}
