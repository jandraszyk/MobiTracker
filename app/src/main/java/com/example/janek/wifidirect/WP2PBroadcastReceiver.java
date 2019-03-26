package com.example.janek.wifidirect;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Jan Andraszyk on 03.11.2017.
 */

public class WP2PBroadcastReceiver extends BroadcastReceiver {

    private WifiP2pManager mManager;
    private Channel mChannel;
    private List<WifiP2pDevice> mPeers = new ArrayList<>();
    private List<WifiP2pConfig> mConfigs;
    private MainActivity mainActivity;
    private WifiP2pManager.PeerListListener myPeerListListener;

    Intent remoteDevice;
    Intent parentDevice;

    public WP2PBroadcastReceiver(WifiP2pManager manager, Channel channel,
                                 MainActivity activity) {
        super();
        this.mManager = manager;
        this.mChannel = channel;
        this.mainActivity = activity;
    }
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
            //Check if WIFI P2P mode is enabled or not
            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
            if(state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                Toast.makeText(mainActivity.getApplicationContext(),"WiFi Direct is enabled",Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(mainActivity.getApplicationContext(),"WiFi Direct is disabled",Toast.LENGTH_LONG).show();
            }
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
            mPeers = new ArrayList<>();
            mConfigs = new ArrayList<>();
            if(mManager != null) {
                myPeerListListener = new WifiP2pManager.PeerListListener() {
                    @Override
                    public void onPeersAvailable(WifiP2pDeviceList peers) {
                        mPeers.clear();
                        mPeers.addAll(peers.getDeviceList());
                        mainActivity.displayPeers(peers);

                        mPeers.addAll(peers.getDeviceList());
                        for(int i = 0; i < peers.getDeviceList().size(); i++) {
                            WifiP2pConfig config = new WifiP2pConfig();
                            config.deviceAddress = mPeers.get(i).deviceAddress;
                            mConfigs.add(config);
                        }
                    }
                };
                mManager.requestPeers(mChannel,myPeerListListener);
            }
        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            if(mManager == null) {
                return;
            }
            NetworkInfo networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
            if(networkInfo.isConnected()) {
                mManager.requestConnectionInfo(mChannel,infoListener);
            }
        }
    }

    public void connect(int position) {
        WifiP2pConfig config = mConfigs.get(position);
        mManager.connect(mChannel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Toast.makeText(mainActivity.getApplicationContext(),"Connected!",Toast.LENGTH_SHORT).show();

            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(mainActivity.getApplicationContext(),"Connection failed",Toast.LENGTH_SHORT).show();
            }
        });
    }

    WifiP2pManager.ConnectionInfoListener infoListener = new WifiP2pManager.ConnectionInfoListener() {
        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo info) {
            if(info.groupFormed) {
                if(info.isGroupOwner) {
                    remoteDevice = new Intent(mainActivity.getApplicationContext(),MonitorActivity.class);
                    remoteDevice.putExtra(MonitorActivity.GROUP_PORT, 8888);
                    remoteDevice.putExtra(MonitorActivity.AUDIO_PORT, 8080);
                    remoteDevice.putExtra(MonitorActivity.VIDEO_PORT, 8000);
                    mainActivity.startActivityForResult(remoteDevice, 0);
                } else {
                    parentDevice = new Intent(mainActivity.getApplicationContext(),ParentActivity.class);
                    parentDevice.putExtra(ParentActivity.HOST_ADDRESS, info.groupOwnerAddress.getHostAddress());
                    parentDevice.putExtra(ParentActivity.GROUP_PORT, 8888);
                    parentDevice.putExtra(ParentActivity.AUDIO_PORT,8080);
                    parentDevice.putExtra(ParentActivity.VIDEO_PORT, 8000);
                    mainActivity.startActivityForResult(parentDevice, 0);
                }
            }
        }
    };

    public  void disconnect() {
        mManager.removeGroup(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Toast.makeText(mainActivity.getApplicationContext(),"Disconnected",Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onFailure(int reason) {

            }
        });
    }





}
