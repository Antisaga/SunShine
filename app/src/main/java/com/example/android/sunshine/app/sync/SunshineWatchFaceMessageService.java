package com.example.android.sunshine.app.sync;

import android.util.Log;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Created by olgakuklina on 2016-08-11.
 */
public class SunshineWatchFaceMessageService extends WearableListenerService {


        public final String LOG_TAG = SunshineWatchFaceMessageService.class.getSimpleName();

        private static final String WEATHER_REQ = "/sunshine/watchface/weatherreq";

        public SunshineWatchFaceMessageService() {
        }

        @Override
        public void onMessageReceived( MessageEvent messageEvent )
        {
            super.onMessageReceived( messageEvent );

            Log.d(LOG_TAG, "onMessageReceived" + messageEvent.getPath());

            if ( messageEvent.getPath().equals( WEATHER_REQ ) )
            {
                SunshineSyncAdapter.syncImmediately(this);
            }
        }
}
