package com.example.weijie.control;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Parcelable;
import android.widget.Toast;

/**
 * Created by weijie on 16-3-1.
 */
public class ControlBroadcastReceiver extends BroadcastReceiver {
    private static final int CHECK_SECOND = 30;
    private static final int DELAY_SECOND = 60;
    static boolean sHadConnected = false;
    static long sOpenWifiTime;
    static long sFailConnectToWifiTime;
    static long sLastSuccessConnectToWifiTime;

    @Override
    public void onReceive(final Context context, Intent intent) {

        //这个监听wifi的打开与关闭，与wifi的连接无关
        if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(intent.getAction())) {
            doInWifiOpenOrClose(context, intent);
        }

        if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(intent.getAction())) {
            doInWifiStateChanged(context, intent);
        }

        if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
            doInWifiOrGPRSChange(context);
        }

    }

    private void doInWifiOrGPRSChange(final Context context) {
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifi = manager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (wifi.isConnected()) {
            connectSuccess();
        } else {
            connectFailAfterSuccess(context);
        }
    }

    private void connectFailAfterSuccessDelayCheck(final Context context) {
        sFailConnectToWifiTime = System.currentTimeMillis();
        if (sFailConnectToWifiTime - sLastSuccessConnectToWifiTime >= 1000 * CHECK_SECOND) {
            Handler x = new Handler();
            x.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isWifi(context)) {
                        closeWifi(context);
                    }
                }
            }, CHECK_SECOND * 1000);
        }
    }

    private void doInWifiStateChanged(Context context, Intent intent) {
        Parcelable parcelableExtra = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
        if (null != parcelableExtra) {
            NetworkInfo networkInfo = (NetworkInfo) parcelableExtra;
            NetworkInfo.State state = networkInfo.getState();
            boolean isConnected = state == NetworkInfo.State.CONNECTED;//当然，这边可以更精确的确定状态

            checkBroadcasrInit(context);

            if (isConnected) {
                connectSuccess();
            } else {
                if (sHadConnected) {
                    //上一次连接成功走这里
                    //补充：上次链接成功说明链接状态必定发生变化，这个广播会接收多次，但是链接变化只有1次，更加可靠
                    connectFailAfterSuccess(context);
                } else {
                    //开wifi到现在没有连接成功走这里
                    connectUntilFail(context);
                }
            }
        }
    }

    private void doInWifiOpenOrClose(Context context, Intent intent) {
        int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0);
        switch (wifiState) {
            case WifiManager.WIFI_STATE_ENABLED:
                init();
                delayCheckOpenCanConnectWifi(context);
                break;
            case WifiManager.WIFI_STATE_DISABLED:
                Toast.makeText(context, "WIFI关闭", Toast.LENGTH_LONG).show();
                cleanOnDisabled();
                break;
        }
    }

    private void connectUntilFail(Context context) {
        if (sOpenWifiTime != 0) {
            sFailConnectToWifiTime = System.currentTimeMillis();
            if (sFailConnectToWifiTime - sOpenWifiTime >= 1000 * CHECK_SECOND) {
                closeWifi(context);
            }
        }
    }

    private void connectFailAfterSuccess(Context context) {
        sFailConnectToWifiTime = System.currentTimeMillis();
        if (sFailConnectToWifiTime - sLastSuccessConnectToWifiTime >= 1000 * CHECK_SECOND) {
            closeWifi(context);
        }
    }

    private void connectSuccess() {
        sHadConnected = true;
        sLastSuccessConnectToWifiTime = System.currentTimeMillis();
    }

    private void cleanOnDisabled() {
        sHadConnected = false;
        sOpenWifiTime = 0;
        sFailConnectToWifiTime = 0;
    }

    private void delayCheckOpenCanConnectWifi(final Context context) {
        Handler x = new Handler();
        x.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isWifi(context)) {
                    closeWifi(context);
                }
            }
        }, DELAY_SECOND * 1000);
    }

    private void checkBroadcasrInit(Context context) {
        //防止广播注册时wifi已经启动没有走初始化流程
        if (isWifi(context) && sOpenWifiTime == 0) {
            //已连接的时候注册
            sHadConnected = true;
            sFailConnectToWifiTime = 0;
            sOpenWifiTime = System.currentTimeMillis() - (1000 * CHECK_SECOND);
            sLastSuccessConnectToWifiTime = System.currentTimeMillis();

        } else if (!isWifi(context) && sOpenWifiTime == 0) {
            //在搜索的时候注册
            sHadConnected = false;
            sFailConnectToWifiTime = 0;
            sOpenWifiTime = System.currentTimeMillis();
        }
    }

    private void init() {
        sHadConnected = false;
        sOpenWifiTime = System.currentTimeMillis();
        sFailConnectToWifiTime = 0;
    }

    private void closeWifi(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        wifiManager.setWifiEnabled(false);
    }

    /**
     * 是否为wifi连接
     *
     * @param mContext
     * @return
     */
    public static boolean isWifi(Context mContext) {
        ConnectivityManager connectivityManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetInfo = connectivityManager.getActiveNetworkInfo();
        if (activeNetInfo != null && activeNetInfo.getType() == ConnectivityManager.TYPE_WIFI) {
            return true;
        }
        return false;
    }
}