package com.vpn.vkaccess.activity;

import android.os.Bundle;
import android.util.Log;
import android.widget.PopupWindow;
import android.widget.Toast;

import com.vpn.R;
import com.vpn.vkaccess.App;
import com.vpn.vkaccess.model.Server;
import com.vpn.vkaccess.util.PropertiesService;


public class HomeActivity extends BaseActivity {

    public static final String EXTRA_COUNTRY = "country";
    private PopupWindow popupWindow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Server randomServer = getRandomServer();
        if (randomServer != null) {
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
