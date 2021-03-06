// 가져오기 완료
package com.android.nanal.event;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Events;
import android.text.TextUtils;
import android.text.format.Time;
import android.widget.ArrayAdapter;
import android.widget.Button;

import com.android.nanal.R;
import com.android.nanal.calendar.CalendarEventModel;
import com.android.nanal.query.AsyncQueryService;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * A helper class for deleting events.  If a normal event is selected for
 * deletion, then this pops up a confirmation dialog.  If the user confirms,
 * then the normal event is deleted.
 * 이벤트 삭제를 위한 헬퍼 클래스
 * 일반 이벤트가 삭제하기 위해 선택되면, 확인 대화 상자가 나타남
 * 사용자가 확인을 누르면 일반 이벤트가 삭제됨
 *
 * <p>
 * If a repeating event is selected for deletion, then this pops up dialog
 * asking if the user wants to delete just this one instance, or all the
 * events in the series, or this event plus all following events.  The user
 * may also cancel the delete.
 * </p>
 * 반복 이벤트가 삭제하도록 선택한 경우, 사용자가 이 인스턴스 하나만 삭제할기, 또는 해당 시리즈의
 * 모든 이벤트를 삭제할지, 아니면 이 이벤트와 다음 이벤트를 모두(과거에 지나간 건 삭제X) 삭제할지
 * 묻는 대화상자가 나타남
 * 사용자는 삭제를 취소할 수 있음
 *
 * <p>
 * To use this class, create an instance, passing in the parent activity
 * and a boolean that determines if the parent activity should exit if the
 * event is deleted.  Then to use the instance, call one of the
 * delete() methods on this class.
 * 이 클래스를 사용하려면, 부모 activity를 전달하고, 이벤트를 삭제한 경우 부모 activity가 종료되어야
 * 하는지 그 여부를 결정하는 boolean을 생성하기
 * 그런 다음 인스턴스를 사용하려면 이 클래스의 메소드 delete()를 호출함
 *
 * An instance of this class may be created once and reused (by calling
 * delete() multiple times).
 * 이 클래스의 인스턴스를 한 번 생성하고 재사용이 가능함 (delete()를 여러 번 호출함)
 */
public class DeleteEventHelper {

    /**
     * These are the corresponding indices into the array of strings
     * "R.array.delete_repeating_labels" in the resource file.
     * 리소스 파일의 스트링 배열 "R.array..._labels"에 해당하는 인덱스임
     */
    public static final int DELETE_SELECTED = 0;
    public static final int DELETE_ALL_FOLLOWING = 1;
    public static final int DELETE_ALL = 2;
    private final Activity mParent;
    private Context mContext;
    private long mStartMillis;
    private long mEndMillis;
    private CalendarEventModel mModel;
    /**
     * If true, then call finish() on the parent activity when done.
     * true인 경우, 완료되면 부모 activity에 대한 finish()를 호출함
     */
    private boolean mExitWhenDone;
    // the runnable to execute when the delete is confirmed
    // 삭제가 확인되었을 때 실행할 runnable
    private Runnable mCallback;
    private int mWhichDelete;
    private ArrayList<Integer> mWhichIndex;
    private AlertDialog mAlertDialog;
    private Dialog.OnDismissListener mDismissListener;

    private String mSyncId;

    private AsyncQueryService mService;

    private DeleteNotifyListener mDeleteStartedListener = null;
    /**
     * This callback is used when a normal event is deleted.
     * 이 콜백은 일반 이벤트가 삭제될 때 사용됨
     */
    private DialogInterface.OnClickListener mDeleteNormalDialogListener =
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int button) {
                    deleteStarted();
                    long id = mModel.mId; // mCursor.getInt(mEventIndexId);
                    Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id);
                    mService.startDelete(mService.getNextToken(), null, uri, null, null, Utils.UNDO_DELAY);
                    if (mCallback != null) {
                        mCallback.run();
                    }
                    if (mExitWhenDone) {
                        mParent.finish();
                    }
                }
            };
    /**
     * This callback is used when an exception to an event is deleted
     * 이 콜백은 이벤트에 대한 예외가 삭제될 때 사용됨
     */
    private DialogInterface.OnClickListener mDeleteExceptionDialogListener =
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int button) {
                    deleteStarted();
                    deleteExceptionEvent();
                    if (mCallback != null) {
                        mCallback.run();
                    }
                    if (mExitWhenDone) {
                        mParent.finish();
                    }
                }
            };
    /**
     * This callback is used when a list item for a repeating event is selected
     * 이 콜백은 반복 이벤트에 대한 리스트 아이템을 선택할 때 사용됨
     */
    private DialogInterface.OnClickListener mDeleteListListener =
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int button) {
                    // set mWhichDelete to the delete type at that index
                    // mWhichDelete를 해당 인덱스의 삭제 유형으로 설정함
                    mWhichDelete = mWhichIndex.get(button);

                    // Enable the "ok" button now that the user has selected which
                    // events in the series to delete.
                    // 사용자가 시리즈에서 삭제할 이벤트를 선택했으므로 "확인" 버튼을 활성화함
                    Button ok = mAlertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                    ok.setEnabled(true);
                }
            };
    /**
     * This callback is used when a repeating event is deleted.
     * 이 콜백은 반복 이벤트가 삭제될 때 사용됨
     */
    private DialogInterface.OnClickListener mDeleteRepeatingDialogListener =
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int button) {
                    deleteStarted();
                    if (mWhichDelete != -1) {
                        deleteRepeatingEvent(mWhichDelete);
                    }
                }
            };
    public DeleteEventHelper(Context context, Activity parentActivity, boolean exitWhenDone) {
        if (exitWhenDone && parentActivity == null) {
            throw new IllegalArgumentException("parentActivity is required to exit when done");
        }

        mContext = context;
        mParent = parentActivity;
        // TODO move the creation of this service out into the activity.
        mService = new AsyncQueryService(mContext) {
            @Override
            protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
                if (cursor == null) {
                    return;
                }
                cursor.moveToFirst();
                CalendarEventModel mModel = new CalendarEventModel();
                EditEventHelper.setModelFromCursor(mModel, cursor);
                cursor.close();
                DeleteEventHelper.this.delete(mStartMillis, mEndMillis, mModel, mWhichDelete);
            }
        };
        mExitWhenDone = exitWhenDone;
    }

    public void setExitWhenDone(boolean exitWhenDone) {
        mExitWhenDone = exitWhenDone;
    }

    /**
     * Does the required processing for deleting an event, which includes
     * first popping up a dialog asking for confirmation (if the event is
     * a normal event) or a dialog asking which events to delete (if the
     * event is a repeating event).  The "which" parameter is used to check
     * the initial selection and is only used for repeating events.  Set
     * "which" to -1 to have nothing selected initially.
     * 확인을 요청하는 대화상자(이벤트가 일반 이벤트인 경우) 또는 삭제할 이벤트를 묻는 대화상자
     * (반복 이벤트인 경우)를 먼저 팝업하는 기능을 포함한 이벤트 삭제시 필요한 처리를 수행함
     * "which" 매개변수는 초기 선택을 확인하는 데 사용되며 반복 이벤트에만 사용됨
     * 처음에 아무것도 선택하지 않으려면 "which"를 -1로 설정하기
     *
     * @param begin the begin time of the event, in UTC milliseconds
     * @param end the end time of the event, in UTC milliseconds
     * @param eventId the event id
     * @param which one of the values DELETE_SELECTED,
     *  DELETE_ALL_FOLLOWING, DELETE_ALL, or -1
     */
    public void delete(long begin, long end, long eventId, int which) {
        Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);
        mService.startQuery(mService.getNextToken(), null, uri, EditEventHelper.EVENT_PROJECTION,
                null, null, null);
        mStartMillis = begin;
        mEndMillis = end;
        mWhichDelete = which;
    }

    public void delete(long begin, long end, long eventId, int which, Runnable callback) {
        delete(begin, end, eventId, which);
        mCallback = callback;
    }


    /**
     * Does the required processing for deleting an event.  This method
     * takes a {@link CalendarEventModel} object, which must have a valid
     * uri for referencing the event in the database and have the required
     * fields listed below.
     * 이벤트를 삭제하는 데 필요한 처리 수행
     * 이 메소드는 데이터베이스에서 이벤트를 참조하는 데 유효한 uri가 있고,
     * 아래에 있는 필수 필드를 가지고 있는 CalendarEventModel 개체를 사용함
     * The required fields for a normal event are:
     * 일반 이벤트에 필요한 필드:
     *
     * <ul>
     *   <li> Events._ID </li>
     *   <li> Events.TITLE </li>
     *   <li> Events.RRULE </li>
     * </ul>
     *
     * The required fields for a repeating event include the above plus the
     * following fields:
     * 반복 이벤트에 필요한 필드는 위의 필드 + 다음 필드:
     *
     * <ul>
     *   <li> Events.ALL_DAY </li>
     *   <li> Events.CALENDAR_ID </li>
     *   <li> Events.DTSTART </li>
     *   <li> Events._SYNC_ID </li>
     *   <li> Events.EVENT_TIMEZONE </li>
     * </ul>
     *
     * If the event no longer exists in the db this will still prompt
     * the user but will return without modifying the db after the query
     * returns.
     * 이벤트가 db에 더 이상 존재하지 않는 경우, 여전히 사용자에게 메시지를 표시하지만
     * 쿼리가 반환된 후 db를 수정하지 않고 반환될 것임
     *
     * @param begin the begin time of the event, in UTC milliseconds
     * @param end the end time of the event, in UTC milliseconds
     * //@param cursor the database cursor containing the required fields
     * @param which one of the values {link DELETE_SELECTED},
     *  {link DELETE_ALL_FOLLOWING}, {link DELETE_ALL}, or -1
     */
    public void delete(long begin, long end, CalendarEventModel model, int which) {
        mWhichDelete = which;
        mStartMillis = begin;
        mEndMillis = end;
        mModel = model;
        mSyncId = model.mSyncId;

        // If this is a repeating event, then pop up a dialog asking the
        // user if they want to delete all of the repeating events or
        // just some of them.
        // 반복 이벤트인 경우, 사용자에게 모든 반복 이벤트를 삭제할지 아니면 일부만 삭제할지 묻는
        // 대화상자를 표시함
        String rRule = model.mRrule;
        String originalEvent = model.mOriginalSyncId;
        if (TextUtils.isEmpty(rRule)) {
            AlertDialog dialog = new AlertDialog.Builder(mContext)
                    .setMessage(R.string.delete_this_event_title)
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setNegativeButton(android.R.string.cancel, null).create();

            if (originalEvent == null) {
                // This is a normal event. Pop up a confirmation dialog.
                // 일반 이벤트임, 확인 대화상자 표시하기
                dialog.setButton(DialogInterface.BUTTON_POSITIVE,
                        mContext.getText(android.R.string.ok),
                        mDeleteNormalDialogListener);
            } else {
                // This is an exception event. Pop up a confirmation dialog.
                // 예외 이벤트(공휴일?)임, 확인 대화상자 표시하기
                dialog.setButton(DialogInterface.BUTTON_POSITIVE,
                        mContext.getText(android.R.string.ok),
                        mDeleteExceptionDialogListener);
            }
            dialog.setOnDismissListener(mDismissListener);
            dialog.show();
            mAlertDialog = dialog;
        } else {
            // This is a repeating event.  Pop up a dialog asking which events
            // to delete.
            // 반복 이벤트임, 삭제할 이벤트를 묻는 대화상자 표시하기
            Resources res = mContext.getResources();
            ArrayList<String> labelArray = new ArrayList<String>(Arrays.asList(res
                    .getStringArray(R.array.delete_repeating_labels)));
            // asList doesn't like int[] so creating it manually.
            // asList는 int[]를 좋아하지 않으므로 수동으로 생성함
            int[] labelValues = res.getIntArray(R.array.delete_repeating_values);
            ArrayList<Integer> labelIndex = new ArrayList<Integer>();
            for (int val : labelValues) {
                labelIndex.add(val);
            }

            if (mSyncId == null) {
                // remove 'Only this event' item
                // '이 이벤트' 아이템만 삭제
                labelArray.remove(0);
                labelIndex.remove(0);
                if (!model.mIsOrganizer) {
                    // remove 'This and future events' item
                    // '이 이벤트와 미래의 이벤트' 아이템 삭제
                    labelArray.remove(0);
                    labelIndex.remove(0);
                }
            } else if (!model.mIsOrganizer) {
                // remove 'This and future events' item
                labelArray.remove(1);
                labelIndex.remove(1);
            }
            if (which != -1) {
                // transform the which to the index in the array
                // 배열의 인텍스로 변환함
                which = labelIndex.indexOf(which);
            }
            mWhichIndex = labelIndex;
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(mContext,
                    android.R.layout.simple_list_item_single_choice, labelArray);
            AlertDialog dialog = new AlertDialog.Builder(mContext)
                    .setTitle(
                            mContext.getString(R.string.delete_recurring_event_title,model.mTitle))
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setSingleChoiceItems(adapter, which, mDeleteListListener)
                    .setPositiveButton(android.R.string.ok, mDeleteRepeatingDialogListener)
                    .setNegativeButton(android.R.string.cancel, null).show();
            dialog.setOnDismissListener(mDismissListener);
            mAlertDialog = dialog;

            if (which == -1) {
                // Disable the "Ok" button until the user selects which events
                // to delete.
                // 사용자가 삭제할 이벤트를 선택할 때까지 "확인" 버튼을 비활성화함
                Button ok = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                ok.setEnabled(false);
            }
        }
    }


    private void deleteExceptionEvent() {
        long id = mModel.mId; // mCursor.getInt(mEventIndexId);

        // update a recurrence exception by setting its status to "canceled"
        // 상태를 "취소됨"으로 설정하여 반복 예외 업데이트
        ContentValues values = new ContentValues();
        values.put(Events.STATUS, Events.STATUS_CANCELED);

        Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id);
        mService.startUpdate(mService.getNextToken(), null, uri, values, null, null,
                Utils.UNDO_DELAY);
    }

    private void deleteRepeatingEvent(int which) {
        String rRule = mModel.mRrule;
        boolean allDay = mModel.mAllDay;
        long dtstart = mModel.mStart;
        long id = mModel.mId; // mCursor.getInt(mEventIndexId);

        switch (which) {
            case DELETE_SELECTED: {
                // If we are deleting the first event in the series, then
                // instead of creating a recurrence exception, just change
                // the start time of the recurrence.
                // 시리즈에서 첫 번째 이벤트를 삭제하는 경우,
                // 반복 예외를 만드는 대신 반복 시작 시간을 변경함
                if (dtstart == mStartMillis) {
                    // TODO
                }

                // Create a recurrence exception by creating a new event
                // with the status "cancelled".
                // 상태가 "취소됨"인 새 이벤트를 생성하여 반복 예외를 생성함
                ContentValues values = new ContentValues();

                // The title might not be necessary, but it makes it easier
                // to find this entry in the database when there is a problem.
                // 제목은 필요하지 않을 수 있지만, 문제가 있을 때 db에서 항목을 쉽게 찾을 수 있음
                String title = mModel.mTitle;
                values.put(Events.TITLE, title);

                String timezone = mModel.mTimezone;
                long calendarId = mModel.mCalendarId;
                values.put(Events.EVENT_TIMEZONE, timezone);
                values.put(Events.ALL_DAY, allDay ? 1 : 0);
                values.put(Events.ORIGINAL_ALL_DAY, allDay ? 1 : 0);
                values.put(Events.CALENDAR_ID, calendarId);
                values.put(Events.DTSTART, mStartMillis);
                values.put(Events.DTEND, mEndMillis);
                values.put(Events.ORIGINAL_SYNC_ID, mSyncId);
                values.put(Events.ORIGINAL_ID, id);
                values.put(Events.ORIGINAL_INSTANCE_TIME, mStartMillis);
                values.put(Events.STATUS, Events.STATUS_CANCELED);

                mService.startInsert(mService.getNextToken(), null, Events.CONTENT_URI, values,
                        Utils.UNDO_DELAY);
                break;
            }
            case DELETE_ALL: {
                Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id);
                mService.startDelete(mService.getNextToken(), null, uri, null, null,
                        Utils.UNDO_DELAY);
                break;
            }
            case DELETE_ALL_FOLLOWING: {
                // If we are deleting the first event in the series and all
                // following events, then delete them all.
                // 시리즈에서 첫 번째 이벤트와 다음 이벤트를 모두 삭제하는 경우 처리함
                if (dtstart == mStartMillis) {
                    Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id);
                    mService.startDelete(mService.getNextToken(), null, uri, null, null,
                            Utils.UNDO_DELAY);
                    break;
                }

                // Modify the repeating event to end just before this event time
                // 반복 이벤트를 이 이벤트 시간 직전에 종료하도록 수정함
                EventRecurrence eventRecurrence = new EventRecurrence();
                eventRecurrence.parse(rRule);
                Time date = new Time();
                if (allDay) {
                    date.timezone = Time.TIMEZONE_UTC;
                }
                date.set(mStartMillis);
                date.second--;
                date.normalize(false);

                // Google calendar seems to require the UNTIL string to be
                // in UTC.
                // 구글 캘린더는 UNTIL 문자열이 UTC에 있어야 하는 것처럼 보임
                date.switchTimezone(Time.TIMEZONE_UTC);
                eventRecurrence.until = date.format2445();

                ContentValues values = new ContentValues();
                values.put(Events.DTSTART, dtstart);
                values.put(Events.RRULE, eventRecurrence.toString());
                Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id);
                mService.startUpdate(mService.getNextToken(), null, uri, values, null, null,
                        Utils.UNDO_DELAY);
                break;
            }
        }
        if (mCallback != null) {
            mCallback.run();
        }
        if (mExitWhenDone) {
            mParent.finish();
        }
    }

    public void setDeleteNotificationListener(DeleteNotifyListener listener) {
        mDeleteStartedListener = listener;
    }

    private void deleteStarted() {
        if (mDeleteStartedListener != null) {
            mDeleteStartedListener.onDeleteStarted();
        }
    }

    public void setOnDismissListener(Dialog.OnDismissListener listener) {
        if (mAlertDialog != null) {
            mAlertDialog.setOnDismissListener(listener);
        }
        mDismissListener = listener;
    }

    public void dismissAlertDialog() {
        if (mAlertDialog != null) {
            mAlertDialog.dismiss();
        }
    }

    public interface DeleteNotifyListener {
        public void onDeleteStarted();
    }
}
