package com.vpn.vkaccess.activity;

import android.content.Intent;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.widget.FrameLayout;

import com.androidnetworking.AndroidNetworking;
import com.androidnetworking.common.Priority;
import com.androidnetworking.error.ANError;
import com.androidnetworking.interfaces.JSONArrayRequestListener;
import com.vpn.vkaccess.App;
import com.vpn.R;
import com.vpn.vkaccess.database.DBHelper;
import com.vpn.vkaccess.model.Server;
import com.vpn.vkaccess.util.CountriesNames;
import com.vpn.vkaccess.util.TotalTraffic;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public abstract class BaseActivity extends AppCompatActivity {

    private DrawerLayout fullLayout;

    public static Server connectedServer = null;
    boolean hideCurrentConnection = false;

    int widthWindow;
    int heightWindow;

    static DBHelper dbHelper;
    Map<String, String> localeCountries;

    @Override
    public void setContentView(int layoutResID) {
        fullLayout = (DrawerLayout) getLayoutInflater().inflate(R.layout.activity_base, null);
        FrameLayout activityContainer = (FrameLayout) fullLayout.findViewById(R.id.activity_content);
        getLayoutInflater().inflate(layoutResID, activityContainer, true);
        super.setContentView(fullLayout);

        if (useHomeButton()) {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setDisplayShowHomeEnabled(true);
            }
        }

        dbHelper = new DBHelper(this);

        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);

        widthWindow = dm.widthPixels;
        heightWindow = dm.heightPixels;

        localeCountries = CountriesNames.getCountries();

        App application = (App) getApplication();
    }

    @Override
    protected void onPause() {
        super.onPause();
        TotalTraffic.saveTotal();
    }

    protected boolean useToolbar() {
        return true;
    }

    protected boolean useHomeButton() {
        return true;
    }

    protected boolean useMenu() {
        return true;
    }

    public Server getRandomServer() {
        Server randomServer = dbHelper.getGoodRandomServer();

        return randomServer;
    }

    public void newConnecting(Server server, boolean fastConnection, boolean autoConnection) {
        if (server != null) {
            Intent intent = new Intent(this, ServerActivity.class);
            intent.putExtra(Server.class.getCanonicalName(), server);
            intent.putExtra("fastConnection", fastConnection);
            intent.putExtra("autoConnection", autoConnection);
            startActivity(intent);
        }
    }

    protected void ipInfoResult() {
    }

    protected void getIpInfo(Server server) {
        List<Server> serverList = new ArrayList<Server>();
        serverList.add(server);
        getIpInfo(serverList);
    }

    protected void getIpInfo(final List<Server> serverList) {
        JSONArray jsonArray = new JSONArray();

        for (Server server : serverList) {
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.put("query", server.getIp());
                jsonObject.put("lang", Locale.getDefault().getLanguage());

                jsonArray.put(jsonObject);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        AndroidNetworking.post(getString(R.string.url_check_ip_batch))
                .addJSONArrayBody(jsonArray)
                .setTag("getIpInfo")
                .setPriority(Priority.MEDIUM)
                .build()
                .getAsJSONArray(new JSONArrayRequestListener() {
                    @Override
                    public void onResponse(JSONArray response) {
                        if (dbHelper.setIpInfo(response, serverList))
                            ipInfoResult();
                    }

                    @Override
                    public void onError(ANError error) {

                    }
                });
    }
}
