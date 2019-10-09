package com.android.nanal.diary;

import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Colors;
import android.text.format.Time;
import android.util.Log;

import com.android.nanal.activity.AbstractCalendarActivity;
import com.android.nanal.calendar.CalendarDiaryModel;
import com.android.nanal.event.Utils;
import com.android.nanal.query.AsyncQueryService;

import java.util.ArrayList;
import java.util.TimeZone;

public class EditDiaryHelper {
    private static final String TAG = "EditDiaryHelper";
    private static final String NO_DIARY_COLOR = "";

    public static final String[] DIARY_PROJECTION = new String[]{
            "diary_id",
            "account_id",
            "connect_type",
            "color",
            "location",
            "day",
            "title",
            "content",
            "weather",
            "image",
            "group_id",
    };
    private static final int PROJECTION_DIARY_ID_INDEX = 0;
    static final int PROJECTION_ACCOUNT_ID_INDEX = 1;
    private static final int PROJECTION_CONNECT_TYPE_INDEX = 2;
    private static final int PROJECTION_COLOR_INDEX = 3;
    private static final int PROJECTION_LOCATION_INDEX = 4;
    private static final int PROJECTION_DAY_INDEX = 5;
    private static final int PROJECTION_TITLE_INDEX = 6;
    private static final int PROJECTION_CONTENT_INDEX = 7;
    private static final int PROJECTION_WEATHER_INDEX = 8;
    private static final int PROJECTION_IMAGE_INDEX = 9;
    private static final int PROJECTION_GROUP_ID_INDEX = 10;

    private final AsyncQueryService mService;
    protected boolean mDiaryOk = true;

    protected static final int MODIFY_UNINITIALIZED = 0;

    static final String[] CALENDARS_PROJECTION = new String[]{
            Calendars._ID, // 0
            Calendars.CALENDAR_DISPLAY_NAME, // 1
            Calendars.OWNER_ACCOUNT, // 2
            Calendars.CALENDAR_COLOR, // 3
            Calendars.CAN_ORGANIZER_RESPOND, // 4
            Calendars.CALENDAR_ACCESS_LEVEL, // 5
            Calendars.VISIBLE, // 6
            Calendars.MAX_REMINDERS, // 7
            Calendars.ALLOWED_REMINDERS, // 8
            Calendars.ALLOWED_ATTENDEE_TYPES, // 9
            Calendars.ALLOWED_AVAILABILITY, // 10
            Calendars.ACCOUNT_NAME, // 11
            Calendars.ACCOUNT_TYPE, //12
    };
    static final int CALENDARS_INDEX_ID = 0;
    static final int CALENDARS_INDEX_DISPLAY_NAME = 1;
    static final int CALENDARS_INDEX_OWNER_ACCOUNT = 2;
    static final int CALENDARS_INDEX_COLOR = 3;
    static final int CALENDARS_INDEX_CAN_ORGANIZER_RESPOND = 4;
    static final int CALENDARS_INDEX_ACCESS_LEVEL = 5;
    static final int CALENDARS_INDEX_VISIBLE = 6;
    static final int CALENDARS_INDEX_MAX_REMINDERS = 7;
    static final int CALENDARS_INDEX_ALLOWED_REMINDERS = 8;
    static final int CALENDARS_INDEX_ALLOWED_ATTENDEE_TYPES = 9;
    static final int CALENDARS_INDEX_ALLOWED_AVAILABILITY = 10;
    static final int CALENDARS_INDEX_ACCOUNT_NAME = 11;
    static final int CALENDARS_INDEX_ACCOUNT_TYPE = 12;

    static final String[] GROUP_PROJECTION = new String[] {
            "group_id",
            "group_name",
            "group_color",
            "account_id"
    };
    static final int GROUP_INDEX_ID = 0;
    static final int GROUP_INDEX_NAME = 1;
    static final int GROUP_INDEX_COLOR = 2;
    static final int GROUP_INDEX_ACCOUNT_ID = 3;

    static final String CALENDARS_WHERE_WRITEABLE_VISIBLE = Calendars.CALENDAR_ACCESS_LEVEL + ">="
            + Calendars.CAL_ACCESS_CONTRIBUTOR + " AND " + Calendars.VISIBLE + "=1";


    static final String CALENDARS_WHERE = Calendars._ID + "=?";

    static final String[] COLORS_PROJECTION = new String[]{
            Colors._ID, // 0
            Colors.ACCOUNT_NAME,
            Colors.ACCOUNT_TYPE,
            Colors.COLOR, // 1
            Colors.COLOR_KEY // 2
    };

    static final String COLORS_WHERE = Colors.ACCOUNT_NAME + "=? AND " + Colors.ACCOUNT_TYPE +
            "=? AND " + Colors.COLOR_TYPE + "=" + Colors.TYPE_EVENT;

    static final int COLORS_INDEX_ACCOUNT_NAME = 1;
    static final int COLORS_INDEX_ACCOUNT_TYPE = 2;
    static final int COLORS_INDEX_COLOR = 3;
    static final int COLORS_INDEX_COLOR_KEY = 4;


    public EditDiaryHelper(Context context) {
        mService = ((AbstractCalendarActivity) context).getAsyncQueryService();
    }

    public boolean saveDiary(CalendarDiaryModel model, CalendarDiaryModel originalModel) {
        if (!mDiaryOk) {
            return false;
        }

        if (model == null) {
            Log.e(TAG, "Attempted to save null model.");
            return false;
        }

        if (originalModel != null) {
            Log.e(TAG, "Attempted to update existing event but models didn't refer to the same "
                    + "event.");
            return false;
        }
        if (originalModel != null && model.isUnchanged(originalModel)) {
            return false;
        }

        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
        int diaryIdIndex = -1;

        ContentValues values = getContentValuesFromModel(model);

        if (model.mUri != null && originalModel == null) {
            Log.e(TAG, "Existing event but no originalModel provided. Aborting save.");
            return false;
        }

        Uri uri = null;
        if (model.mUri != null) {
            uri = Uri.parse(model.mUri);
        }

        diaryIdIndex = ops.size();

        if(uri == null) {
            Uri CONTENT_URI = Uri.parse("content://" + "com.android.nanal" + "/diary");
            //todo:수정
            ContentProviderOperation.Builder b = ContentProviderOperation.newInsert(CONTENT_URI).withValues(values);
            ops.add(b.build());
        } else {
            ContentProviderOperation.Builder b = ContentProviderOperation.newInsert(uri).withValues(values);
            ops.add(b.build());
        }

        // New Event or New Exception to an existing event
        boolean newDiary = (diaryIdIndex != -1);

        ContentProviderOperation.Builder b;

        mService.startBatch(mService.getNextToken(), null, android.provider.CalendarContract.AUTHORITY, ops,
                Utils.UNDO_DELAY);

        return true;
    }

    public static boolean isSameDiary(CalendarDiaryModel model, CalendarDiaryModel originalModel) {
        if (originalModel == null) {
            return true;
        }

        if (model.mDiaryUserId != originalModel.mDiaryUserId) {
            return false;
        }
        if (model.mDiaryId != originalModel.mDiaryId) {
            return false;
        }

        return true;
    }

    public static void setModelFromCursor(CalendarDiaryModel model, Cursor cursor) {
        if (model == null || cursor == null || cursor.getCount() != 1) {
            Log.wtf(TAG, "Attempted to build non-existent model or from an incorrect query.");
            return;
        }
        model.clear();
        cursor.moveToFirst();

        model.mDiaryId = cursor.getInt(PROJECTION_DIARY_ID_INDEX);
        model.mDiaryUserId = cursor.getString(PROJECTION_ACCOUNT_ID_INDEX);
        model.mConnectType = cursor.getString(PROJECTION_CONNECT_TYPE_INDEX);
        model.mDiaryColor = cursor.getInt(PROJECTION_COLOR_INDEX);
        model.mDiaryLocation = cursor.getString(PROJECTION_LOCATION_INDEX);
        model.mDiaryDay = cursor.getInt(PROJECTION_DAY_INDEX);
        model.mDiaryTitle = cursor.getString(PROJECTION_TITLE_INDEX);
        model.mDiaryContent = cursor.getString(PROJECTION_CONTENT_INDEX);
        model.mDiaryImg = cursor.getString(PROJECTION_IMAGE_INDEX);
        model.mDiaryGroupId = cursor.getInt(PROJECTION_GROUP_ID_INDEX);

        model.mModelUpdatedWithDiaryCursor = true;
    }

    ContentValues getContentValuesFromModel(CalendarDiaryModel model) {
        String timezone = model.mTimezone;
        if (timezone == null) {
            timezone = TimeZone.getDefault().getID();
        }
        Time time = new Time(timezone);
        time.set(model.mDiaryDay);

        ContentValues values = new ContentValues();

        values.put("diary_id", model.mDiaryId);
        values.put("account_id", model.mDiaryUserId);
        values.put("connect_type", model.mConnectType);
        values.put("day", model.mDiaryDay);

        if (model.mDiaryColor > 0) {
            values.put("color", model.mDiaryColor);
        }
        if (model.mDiaryLocation != null) {
            values.put("location", model.mDiaryLocation);
        }
        if (model.mDiaryTitle != null) {
            values.put("title", model.mDiaryTitle);
        }
        if (model.mDiaryContent != null) {
            values.put("content", model.mDiaryContent);
        }
        if (model.mDiaryWeather != null) {
            values.put("weather", model.mDiaryWeather);
        }
        if (model.mDiaryGroupId > 0) {
            values.put("group_id", model.mDiaryGroupId);
        }
        return values;
    }

    public static boolean setModelFromCalendarCursor(CalendarDiaryModel model, Cursor cursor) {
        if (model == null || cursor == null) {
            Log.wtf(TAG, "Attempted to build non-existent model or from an incorrect query.");
            return false;
        }


        if (!model.mModelUpdatedWithDiaryCursor) {
            Log.wtf(TAG,
                    "Can't update model with a Calendar cursor until it has seen an Event cursor.");
            return false;
        }

        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            model.mCalendarDisplayName = cursor.getString(CALENDARS_INDEX_DISPLAY_NAME);
            model.mCalendarAccountName = cursor.getString(CALENDARS_INDEX_ACCOUNT_NAME);
            model.mCalendarAccountType = cursor.getString(CALENDARS_INDEX_ACCOUNT_TYPE);
            return true;
        }
        return false;
    }

    public interface EditDoneRunnable extends Runnable {
        public void setDoneCode(int code);
    }

    protected long constructDefaultStartTime(long now) {
        Time defaultStart = new Time();
        defaultStart.set(now);
        defaultStart.hour = 0;
        defaultStart.second = 0;
        defaultStart.minute = 0;
        long defaultStartMillis = defaultStart.toMillis(false);
        return defaultStartMillis;
    }
}