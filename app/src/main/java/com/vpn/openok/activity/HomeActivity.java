package com.vpn.openok.activity;

import android.os.Bundle;
import android.util.Log;
import android.widget.PopupWindow;
import android.widget.Toast;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.vpn.R;
import com.vpn.openok.App;
import com.vpn.openok.model.Server;
import com.vpn.openok.util.PropertiesService;

public class HomeActivity extends BaseActivity {

    public static final String EXTRA_COUNTRY = "country";
    private PopupWindow popupWindow;
    private Tracker mTracker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        App application = (App) getApplication();
        mTracker = application.getDefaultTracker();
        Log.i(App.TAG, "Экран старта Home ");
        mTracker.setScreenName("ViewHome");
        mTracker.send(new HitBuilders.ScreenViewBuilder().build());

        Server randomServer = getRandomServer();
        if (randomServer != null) {
            mTracker.send(new HitBuilders.EventBuilder()
                    .setCategory("Action")
                    .setAction("ConnectToVPN")
                    .build());
            newConnecting(randomServer, true, true);
        } else {
            String randomError = String.format(getResources().getString(R.string.error_random_country), PropertiesService.getSelectedCountry());
            Toast.makeText(this, randomError, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        invalidateOptionsMenu();
    }

    @Override
    protected boolean useHomeButton() {
        return false;
    }

}
