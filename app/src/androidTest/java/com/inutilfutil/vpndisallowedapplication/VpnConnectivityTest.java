package com.inutilfutil.vpndisallowedapplication;

import android.app.UiAutomation;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.VpnService;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiSelector;

import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.ConditionVariable;
import android.util.Log;
import android.widget.Button;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.InetAddress;

@RunWith(AndroidJUnit4.class)
public class VpnConnectivityTest {
    private static final String TAG = VpnConnectivityTest.class.getSimpleName();
    private static final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private static final ConnectivityManager connectivityManager = context.getSystemService(ConnectivityManager.class);
    private static final WifiManager wifiManager = context.getSystemService(WifiManager.class);

    enum CallbackType { RegisterNetworkCallback, RequestNetwork }

    // To simplify the test, disable mobile data and only leave Wifi
    @Before
    public void disableMobileData() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand("svc data disable");
    }
    @After
    public void enableMobileData()  {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand("svc data enable");
    }

    @Test
    public void testRegisterNetworkCallback() throws Exception {
        testConnectivity(CallbackType.RegisterNetworkCallback, false);
    }
    @Test
    public void testRegisterNetworkCallbackWithVpn() throws Exception {
        /** This one fails in Android 12, because network isn't actually ready when NetworkCallback.onAvailable() is called */
        testConnectivity(CallbackType.RegisterNetworkCallback, true);
    }
    @Test
    public void testRequestNetwork() throws Exception {
        testConnectivity(CallbackType.RequestNetwork, false);
    }
    @Test
    public void testRequestNetworkWithVpn() throws Exception {
        testConnectivity(CallbackType.RequestNetwork, true);
    }

    public void testConnectivity(CallbackType callbackType, boolean useVpn) throws Exception {
        if (useVpn) {
            startVpn();
        }
        try {
            InetAddress knowGoodIp = InetAddress.getByName("8.8.8.8");

            NetworkRequest networkRequest = new NetworkRequest.Builder()
                    // Network is available for general use
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build();


            ConditionVariable connected = new ConditionVariable();
            ConditionVariable disconnected = new ConditionVariable();

            IOException connectivityFailures = new IOException("Connectivity failures");

            ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    Log.i(TAG, "NetworkCallback.onAvailable: " + network);
                    try {
                        InetAddress byName = InetAddress.getByName(Math.random() + ".appspot.com");
                        Log.w(TAG, "Resolved domain: " + byName);
                    } catch (Throwable t) {
                        connectivityFailures.addSuppressed(t);
                        Log.w(TAG, "Failed to resolve domain", t);
                    }

                    try {
                        boolean reachable = knowGoodIp.isReachable(500);
                        Log.w(TAG, "testing " + knowGoodIp + ": isReachable=" + reachable);
                        if (!reachable) {
                            connectivityFailures.addSuppressed(new IOException(knowGoodIp + " not reachable"));
                        }
                    } catch (Throwable t) {
                        connectivityFailures.addSuppressed(t);
                        Log.w(TAG, " dns=" + knowGoodIp + ", not reachable", t);
                    }

                    connected.open();
                }

                @Override
                public void onLost(@NonNull Network network) {
                    Log.i(TAG, "NetworkCallback.onLost: " + network);
                    disconnected.open();
                }
            };

            Assert.assertTrue("Failed to ensure Wifi is on", wifiManager.setWifiEnabled(true));
            connected.close();
            if (callbackType == CallbackType.RequestNetwork) {
                connectivityManager.requestNetwork(networkRequest, networkCallback);
            } else {
                connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
            }

            try {
                Assert.assertTrue("No Wifi connectivity", connected.block(30_000));

                Log.i(TAG, "Disabling Wifi");
                disconnected.close();
                Assert.assertTrue("Failed to call setWifiEnabled(false)", wifiManager.setWifiEnabled(false));
                Assert.assertTrue("Didn't receive lost Wifi network", disconnected.block(30_000));

                Thread.sleep(30_000);  // Android dislikes turning Wifi on and off too fast, and may not reconnect without the delay

                Log.i(TAG, "Re-enabling Wifi");
                connected.close();
                Assert.assertTrue("Failed to call setWifiEnabled(true)", wifiManager.setWifiEnabled(true));
                Assert.assertTrue("Didn't receive avaiable Wifi network", connected.block(30_000));

                Thread.sleep(30_000);  // Android dislikes turning Wifi on and off too fast, and may not reconnect without the delay
            } finally {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            }

            if (connectivityFailures.getSuppressed().length != 0) {
                throw connectivityFailures;
            }
        } finally {
            if (useVpn) {
                stopVpn();
            }
        }
    }


    public void startVpn() throws Exception {
        Intent prepareIntent = VpnService.prepare(context);
        if (prepareIntent == null) {
            Log.i(TAG, "VPN Already prepared");
        } else {
            // We cannot use VpnService.prepare, it must be called from an intermediary activity
            Intent prepareActivityIntent = new Intent(context, PrepareVpnActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(prepareActivityIntent);
            UiDevice uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
            UiObject ok = uiDevice.findObject(new UiSelector().packageName(prepareIntent.getComponent().getPackageName()).className(Button.class).textMatches("OK|Ok"));
            ok.waitForExists(5_000);
            ok.clickAndWaitForNewWindow();
            Log.i(TAG, "VPN Prepared");
        }

        Intent stubVpnServiceIntent = new Intent(context, StubVpnService.class);
        context.startService(stubVpnServiceIntent);

        long start = System.currentTimeMillis();
        while (StubVpnService.fd == null) {
            Assert.assertTrue("VPN took more than 5s to start", System.currentTimeMillis() - start < 5_000);
            Thread.sleep(50);
        }
    }

    public void stopVpn() {
        StubVpnService.killVpn();
    }

}