/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.example.android.sunshinewear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    private static final String LOG_TAG = MyWatchFace.class.getSimpleName();
    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        private static final String DATE_FORMAT = "EEE, MMM d yyyy";
        private static final String TIME_FORMAT_AMBIENT = "%02d:%02d";
        private static final String TIME_FORMAT = "%02d:%02d:%02d";
        private static final char DEGREES = (char) 0x00B0;
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mTextPaint;
        Paint mTimePaint;
        Paint mDatePaint;

        Paint mHighDegreesPaint;
        Paint mLowDegreesPaint;

        boolean mAmbient;


        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
               String timeZoneId = intent.getStringExtra("time-zone");
                mCalendar.setTimeZone(TimeZone.getTimeZone(timeZoneId));
            }
        };
        int mTapCount;

        float mYTimeOffset;
        float mYDateOffset;
        float mYLineOffset;
        float mYIconOffset;
        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        //Time and date format
        private Calendar mCalendar;
        private DateFormat mDateFormat;



        // Weather icons
        private Bitmap mWFWeatherIcon;
        private Bitmap mWFWeatherIconAmbient;
        private int mWFWeatherId = 800;



        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = MyWatchFace.this.getResources();
            mYTimeOffset = resources.getDimension(R.dimen.digital_y_offset);
            mYDateOffset = resources.getDimension(R.dimen.digital_y_date_offset);
            mYLineOffset = resources.getDimension(R.dimen.digital_y_line_offset);
            mYIconOffset = resources.getDimension(R.dimen.digital_y_icon_offset);
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mTimePaint = createTextPaint(resources.getColor(R.color.digital_text));
            mDatePaint = createTextPaint(resources.getColor(R.color.digital_min_text));
            mHighDegreesPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mLowDegreesPaint = createTextPaint(resources.getColor(R.color.digital_min_text));
            mCalendar = Calendar.getInstance();
            mDateFormat = new SimpleDateFormat(DATE_FORMAT, Locale.getDefault());
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            float timeTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_time_text_size_round : R.dimen.digital_time_text_size);
            float dateTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_date_text_size_round : R.dimen.digital_date_text_size);
            float degreesTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_degrees_text_size_round : R.dimen.digital_degrees_text_size);

            mTextPaint.setTextSize(timeTextSize);
            mDatePaint.setTextSize(dateTextSize);
            mHighDegreesPaint.setTextSize(degreesTextSize);
            mLowDegreesPaint.setTextSize(degreesTextSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Resources resources = MyWatchFace.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    mTapCount++;
                    mBackgroundPaint.setColor(resources.getColor(mTapCount % 2 == 0 ?
                            R.color.background : R.color.background2));
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            mCalendar.setTimeInMillis(System.currentTimeMillis());

            // Time formatting
            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            String text;
            int hours =  mCalendar.get(Calendar.HOUR) == 0 ? 12 : mCalendar.get(Calendar.HOUR_OF_DAY);
            int minutes =  mCalendar.get(Calendar.MINUTE);
            if (!mAmbient) {
                int seconds =  mCalendar.get(Calendar.SECOND);
                text = String.format(TIME_FORMAT, hours, minutes, seconds);
            }
            else {
                text = String.format(TIME_FORMAT_AMBIENT, hours, minutes);
            }
            float textWidth = mTextPaint.measureText(text);
            int textXOffset = (int)(bounds.width() - textWidth)/2;
            canvas.drawText(text, textXOffset, mYTimeOffset, mTextPaint);

            String date = mDateFormat.format(mCalendar.getTime()).toUpperCase();
            float datetWidth = mDatePaint.measureText(date);
            int xDateOffset = (int)(bounds.width() - datetWidth)/2;
            canvas.drawText(date, xDateOffset, mYDateOffset, mDatePaint);
            int xlineOffset = (int)(bounds.width() - bounds.width()/5)/2;
            canvas.drawLine(xlineOffset, mYLineOffset, (xlineOffset + bounds.width()/5),mYLineOffset, mDatePaint);


            mWFWeatherIcon = BitmapFactory.decodeResource(
                    getResources(),
                    getIconResourceForWeatherCondition(mWFWeatherId));
            int xIconOffset = (bounds.width() - mWFWeatherIcon.getWidth())/2 - (mWFWeatherIcon.getWidth()) +10;
            canvas.drawBitmap(mWFWeatherIcon, xIconOffset, mYIconOffset, new Paint());

            Resources resources = MyWatchFace.this.getResources();
            float yDegreesOffset = mYIconOffset + resources.getDimension(R.dimen.digital_y_degrees_offset);;
            String highTemperature = "18" + DEGREES;
            String lowTemperature = "15" + DEGREES;


            float xDegreesOffsetHigh = xIconOffset + mWFWeatherIcon.getWidth() + resources.getDimension(R.dimen.digital_x_min_offset);
            float xDegreesOffsetLow = xDegreesOffsetHigh + resources.getDimension(R.dimen.digital_x_space_offset) + mLowDegreesPaint.measureText(lowTemperature);

            Log.v(LOG_TAG, "xh = " + xDegreesOffsetHigh);
            Log.v(LOG_TAG, "xl = " + xDegreesOffsetLow);

            Log.v(LOG_TAG, "xIconOffset = " + xIconOffset);
            canvas.drawText(highTemperature, xDegreesOffsetHigh, yDegreesOffset, mHighDegreesPaint);
            canvas.drawText(lowTemperature, xDegreesOffsetLow, yDegreesOffset, mLowDegreesPaint);


        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
    /**
     * Helper method to provide the icon resource id according to the weather condition id returned
     * by the OpenWeatherMap call.
     * @param weatherId from OpenWeatherMap API response
     * @return resource id for the corresponding icon. -1 if no relation is found.
     */
    public static int getIconResourceForWeatherCondition(int weatherId) {
        // Based on weather code data found at:
        // http://bugs.openweathermap.org/projects/api/wiki/Weather_Condition_Codes
        if (weatherId >= 200 && weatherId <= 232) {
            return R.drawable.ic_storm;
        } else if (weatherId >= 300 && weatherId <= 321) {
            return R.drawable.ic_light_rain;
        } else if (weatherId >= 500 && weatherId <= 504) {
            return R.drawable.ic_rain;
        } else if (weatherId == 511) {
            return R.drawable.ic_snow;
        } else if (weatherId >= 520 && weatherId <= 531) {
            return R.drawable.ic_rain;
        } else if (weatherId >= 600 && weatherId <= 622) {
            return R.drawable.ic_snow;
        } else if (weatherId >= 701 && weatherId <= 761) {
            return R.drawable.ic_fog;
        } else if (weatherId == 761 || weatherId == 781) {
            return R.drawable.ic_storm;
        } else if (weatherId == 800) {
            return R.drawable.ic_clear;
        } else if (weatherId == 801) {
            return R.drawable.ic_light_clouds;
        } else if (weatherId >= 802 && weatherId <= 804) {
            return R.drawable.ic_cloudy;
        }
        return -1;
    }

}
