package com.vpn.opennewvk.activity;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.net.VpnService;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AlertDialog;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.vpn.BuildConfig;
import com.vpn.R;
import com.vpn.opennewvk.App;
import com.vpn.opennewvk.model.Server;
import com.vpn.opennewvk.util.PropertiesService;
import com.vpn.opennewvk.util.TotalTraffic;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.TimeUnit;

import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.ConfigParser;
import de.blinkt.openvpn.core.OpenVPNService;
import de.blinkt.openvpn.core.ProfileManager;
import de.blinkt.openvpn.core.VPNLaunchHelper;
import de.blinkt.openvpn.core.VpnStatus;

public class ServerActivity extends BaseActivity {

    private static final int START_VPN_PROFILE = 70;
    private BroadcastReceiver br;
    private BroadcastReceiver trafficReceiver;
    public final static String BROADCAST_ACTION = "de.blinkt.openvpn.VPN_STATUS";

    private static OpenVPNService mVPNService;
    private VpnProfile vpnProfile;

    private Server currentServer = null;
    public static ToggleButton serverConnect;
    private TextView toggleButtonText;
    public static TextView messageOkText;
    public static TextView messageWaitText;
    private ProgressBar connectingProgress;
    private LinearLayout parentLayout;

    private boolean autoConnection;
    private boolean fastConnection;
    private Server autoServer;

    private boolean statusConnection = false;
    private boolean firstData = true;

    private WaitConnectionAsync waitConnection;
    private boolean inBackground;

    private Tracker mTracker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server);

        App application = (App) getApplication();
        mTracker = application.getDefaultTracker();
        Log.i(App.TAG, "Экран переключателя VPN ");
        mTracker.setScreenName("ViewServer");
        mTracker.send(new HitBuilders.ScreenViewBuilder().build());

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        parentLayout = (LinearLayout) findViewById(R.id.serverParentLayout);
        connectingProgress = (ProgressBar) findViewById(R.id.serverConnectingProgress);
        serverConnect = (ToggleButton) findViewById(R.id.serverConnect);
        messageOkText = (TextView) findViewById(R.id.message_ok_text);
        messageWaitText = (TextView) findViewById(R.id.message_wait);
        serverConnect.setText(null);
        serverConnect.setTextOn(null);
        serverConnect.setTextOff(null);
        messageOkText.setVisibility(View.GONE);
        toggleButtonText = (TextView) findViewById(R.id.toggleButtonText);

        br = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                receiveStatus(context, intent);
            }
        };

        registerReceiver(br, new IntentFilter(BROADCAST_ACTION));

        trafficReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                receiveTraffic(context, intent);
            }
        };

        registerReceiver(trafficReceiver, new IntentFilter(TotalTraffic.TRAFFIC_ACTION));

        initView(getIntent());

        if (checkStatus()) {
            serverConnect.setChecked(false);
            messageOkText.setVisibility(View.GONE);
        } else {
            serverConnect.setChecked(true);
        }
    }

    private void initView(Intent intent) {

        autoConnection = intent.getBooleanExtra("autoConnection", false);
        fastConnection = intent.getBooleanExtra("fastConnection", false);
        currentServer = (Server) intent.getParcelableExtra(Server.class.getCanonicalName());

        if (currentServer == null) {
            if (connectedServer != null) {
                currentServer = connectedServer;
            } else {
                onBackPressed();
                return;
            }
        }

        String code = currentServer.getCountryShort().toLowerCase();
        if (code.equals("do"))
            code = "dom";

        String localeCountryName = localeCountries.get(currentServer.getCountryShort()) != null ?
                localeCountries.get(currentServer.getCountryShort()) : currentServer.getCountryLong();

        double speedValue = (double) Integer.parseInt(currentServer.getSpeed()) / 1048576;
        speedValue = new BigDecimal(speedValue).setScale(3, RoundingMode.UP).doubleValue();
        String speed = String.valueOf(speedValue) + " " + getString(R.string.mbps);

        toggleButtonText.setText(getString(R.string.server_btn_access));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        initView(intent);
    }

    private void receiveTraffic(Context context, Intent intent) {
        if (checkStatus()) {
            String in = "";
            String out = "";
            if (firstData) {
                firstData = false;
            } else {
                in = String.format(getResources().getString(R.string.traffic_in),
                        intent.getStringExtra(TotalTraffic.DOWNLOAD_SESSION));
                out = String.format(getResources().getString(R.string.traffic_out),
                        intent.getStringExtra(TotalTraffic.UPLOAD_SESSION));
            }
        }
    }

    private void receiveStatus(Context context, Intent intent) {
        if (checkStatus()) {
            changeServerStatus(VpnStatus.ConnectionStatus.valueOf(intent.getStringExtra("status")));
        }

        if (intent.getStringExtra("detailstatus").equals("NOPROCESS")) {
            try {
                TimeUnit.SECONDS.sleep(1);
                if (!VpnStatus.isVPNActive())
                    prepareStopVPN();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();

        if (waitConnection != null)
            waitConnection.cancel(false);

        if (isTaskRoot()) {
            startActivity(new Intent(this, HomeActivity.class));
            finish();
        }
    }

    private boolean checkStatus() {
        if (connectedServer != null && connectedServer.getHostName().equals(currentServer.getHostName())) {
            return VpnStatus.isVPNActive();
        }

        return false;
    }

    private void changeServerStatus(VpnStatus.ConnectionStatus status) {
        toggleButtonText.setText(getString(R.string.server_btn_access));
        switch (status) {
            case LEVEL_CONNECTED:
                statusConnection = true;
                connectingProgress.setVisibility(View.GONE);
                if (!inBackground) {
                    if (PropertiesService.getDownloaded() >= 104857600 && PropertiesService.getShowRating()
                            && BuildConfig.FLAVOR != "underground") {
                        PropertiesService.setShowRating(false);
                    }
                }
                messageWaitText.setVisibility(View.GONE);
                messageOkText.setVisibility(View.VISIBLE);
                break;
            default:
                toggleButtonText.setText(getString(R.string.server_btn_access));
                statusConnection = false;
                connectingProgress.setVisibility(View.VISIBLE);
        }
    }

    private void prepareVpn() {
        connectingProgress.setVisibility(View.VISIBLE);
        if (loadVpnProfile()) {
            waitConnection = new WaitConnectionAsync();
            waitConnection.execute();
            toggleButtonText.setText(getString(R.string.server_btn_access));
            startVpn();
        } else {
            connectingProgress.setVisibility(View.GONE);
            Toast.makeText(this, getString(R.string.server_error_loading_profile), Toast.LENGTH_SHORT).show();
        }
    }

    public void serverOnClick(View view) {
        switch (view.getId()) {
            case R.id.serverConnect:
                if (checkStatus()) {
                    mTracker.send(new HitBuilders.EventBuilder()
                            .setCategory("Action")
                            .setAction("StoptVPN")
                            .build());
                    serverConnect.setChecked(false);
                    messageOkText.setVisibility(View.GONE);
                    messageWaitText.setVisibility(View.GONE);
                    stopVpn();
                } else {
                    mTracker.send(new HitBuilders.EventBuilder()
                            .setCategory("Action")
                            .setAction("StartVPN")
                            .build());
                    serverConnect.setChecked(true);
                    messageWaitText.setVisibility(View.VISIBLE);
                    messageOkText.setVisibility(View.GONE);
                    prepareVpn();
                }
                break;
        }
    }

    private boolean loadVpnProfile() {
        byte[] data = Base64.decode(currentServer.getConfigData(), Base64.DEFAULT);
        ConfigParser cp = new ConfigParser();
        InputStreamReader isr = new InputStreamReader(new ByteArrayInputStream(data));
        try {
            cp.parseConfig(isr);
            vpnProfile = cp.convertProfile();
            vpnProfile.mName = currentServer.getCountryLong();

            vpnProfile.mOverrideDNS = true;
            vpnProfile.mDNS1 = "198.101.242.72";
            vpnProfile.mDNS2 = "23.253.163.53";

            ProfileManager.getInstance(this).addProfile(vpnProfile);
        } catch (IOException | ConfigParser.ConfigParseError e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private void prepareStopVPN() {
        statusConnection = false;
        if (waitConnection != null)
            waitConnection.cancel(false);
        connectingProgress.setVisibility(View.GONE);
        toggleButtonText.setText(getString(R.string.server_btn_access));
        connectedServer = null;
    }

    public void stopVpn() {
        //prepareStopVPN();
        ProfileManager.setConntectedVpnProfileDisconnected(this);
        if (mVPNService != null && mVPNService.getManagement() != null)
            mVPNService.getManagement().stopVPN(false);

    }

    private void startVpn() {
        connectedServer = currentServer;
        hideCurrentConnection = true;

        Intent intent = VpnService.prepare(this);

        if (intent != null) {
            VpnStatus.updateStateString("USER_VPN_PERMISSION", "", R.string.state_user_vpn_permission,
                    VpnStatus.ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT);
            // Start the query
            try {
                startActivityForResult(intent, START_VPN_PROFILE);
            } catch (ActivityNotFoundException ane) {
                // Shame on you Sony! At least one user reported that
                // an official Sony Xperia Arc S image triggers this exception
                VpnStatus.logError(R.string.no_vpn_support_image);
            }
        } else {
            onActivityResult(START_VPN_PROFILE, Activity.RESULT_OK, null);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        inBackground = false;

        if (currentServer.getCity() == null)
            getIpInfo(currentServer);

        if (connectedServer != null && currentServer.getIp().equals(connectedServer.getIp())) {
            hideCurrentConnection = true;
            invalidateOptionsMenu();
        }


        Intent intent = new Intent(this, OpenVPNService.class);
        intent.setAction(OpenVPNService.START_SERVICE);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        if (checkStatus()) {
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (!checkStatus()) {
                connectedServer = null;
                toggleButtonText.setText(getString(R.string.server_btn_access));
            }
        } else {
            toggleButtonText.setText(getString(R.string.server_btn_access));
            if (autoConnection) {
                prepareVpn();
                messageWaitText.setVisibility(View.VISIBLE);
                messageOkText.setVisibility(View.GONE);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        inBackground = true;
        unbindService(mConnection);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(br);
        unregisterReceiver(trafficReceiver);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case START_VPN_PROFILE:
                    VPNLaunchHelper.startOpenVpn(vpnProfile, getBaseContext());
                    break;
            }
        }
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            OpenVPNService.LocalBinder binder = (OpenVPNService.LocalBinder) service;
            mVPNService = binder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mVPNService = null;
        }

    };

    private class WaitConnectionAsync extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            try {
                TimeUnit.SECONDS.sleep(PropertiesService.getAutomaticSwitchingSeconds());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            if (!statusConnection) {
                if (currentServer != null)
                    dbHelper.setInactive(currentServer.getIp());

                if (fastConnection) {
                    stopVpn();
                    newConnecting(getRandomServer(), true, true);
                } else if (PropertiesService.getAutomaticSwitching()) {
                    if (!inBackground)
                        showAlert();
                }
            }
        }
    }

    private void showAlert() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(getString(R.string.try_another_server_text))
                .setPositiveButton(getString(R.string.try_another_server_ok),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                                stopVpn();
                                autoServer = dbHelper.getSimilarServer(currentServer.getCountryLong(), currentServer.getIp());
                                if (autoServer != null) {
                                    newConnecting(autoServer, false, true);
                                } else {
                                    onBackPressed();
                                }
                            }
                        })
                .setNegativeButton(getString(R.string.try_another_server_no),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                if (!statusConnection) {
                                    waitConnection = new WaitConnectionAsync();
                                    waitConnection.execute();
                                }
                                dialog.cancel();
                            }
                        });
        AlertDialog alert = builder.create();
        alert.show();
    }
}
