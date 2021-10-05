package com.inutilfutil.vpndisallowedapplication;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.util.Log;

public class PrepareVpnActivity extends AppCompatActivity {
    private static final String TAG = PrepareVpnActivity.class.getSimpleName();
    private static final int VPN_PREPARE = 56;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent vpnPrepareIntent = VpnService.prepare(this);
        if (vpnPrepareIntent == null) {
            finishAndRemoveTask();
            return;
        }
        Log.i(TAG, "Requesting VPN access to the system");
        startActivityForResult(vpnPrepareIntent, VPN_PREPARE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == VPN_PREPARE) {
            finishAndRemoveTask();
        }
    }
}