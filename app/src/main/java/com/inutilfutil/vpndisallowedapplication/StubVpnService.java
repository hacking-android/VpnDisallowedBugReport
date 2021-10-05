package com.inutilfutil.vpndisallowedapplication;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.IOException;

public class StubVpnService extends VpnService {
    private static final String TAG = StubVpnService.class.getSimpleName();
    public static ParcelFileDescriptor fd = null;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        onRevoke();
        try {
            fd = new Builder()
                    .addAddress("1.2.3.4", 32)
                    .addDnsServer("5.6.7.8")
                    .addRoute("0.0.0.0", 0)
                    .addDisallowedApplication(getPackageName())
                    .establish();
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        killVpn();
    }

    @Override
    public void onRevoke() {
        killVpn();
    }


    public static void killVpn() {
        if (fd != null) {
            try {
                fd.close();
            } catch (IOException e) {
                Log.w(TAG, "Failed to close fd");
            }
            fd = null;
        }
    }
}
