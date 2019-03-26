package com.example.janek.wifidirect;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

public class MainActivity extends Activity implements WifiP2pManager.ChannelListener {

    private final IntentFilter intentFilter = new IntentFilter();
    private WifiP2pManager mManager;
    private WifiP2pManager.Channel mChannel;
    private WP2PBroadcastReceiver mReceiver = null;


    int position;

    ListView peersList;
    Button bt_search, bt_disconnect;
    ArrayAdapter<String> wifiP2pArrayAdapter;

    @Override
        protected void onCreate(Bundle savedInstanceState) {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            peersList = (ListView) findViewById(R.id.list_peers);
            bt_search = (Button) findViewById(R.id.bt_search);
            bt_disconnect = (Button) findViewById(R.id.bt_play);

            mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
            mChannel = mManager.initialize(this,getMainLooper(),null);

            intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);

            wifiP2pArrayAdapter = new ArrayAdapter<>(this,android.R.layout.simple_list_item_1);
            peersList.setAdapter(wifiP2pArrayAdapter);

            peersList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapter, View view, int pos, long id) {
                    position = pos;
                    mReceiver.connect(position);
                }
            });

        bt_search.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                search(v);
            }
        });

        bt_disconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mReceiver.disconnect();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        mReceiver = new WP2PBroadcastReceiver(mManager, mChannel, this);
        registerReceiver(mReceiver,intentFilter);
    }

    public void onPause() {
        super.onPause();
        unregisterReceiver(mReceiver);
    }

    public void search(View view) {
        mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Toast.makeText(getApplicationContext(),"Discovery Initiated",
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(getApplicationContext(),"Discovery Failed: " + reason,
                        Toast.LENGTH_SHORT).show();

            }
        });
    }

    public void displayPeers(WifiP2pDeviceList list) {
        wifiP2pArrayAdapter.clear();

        for(WifiP2pDevice peer : list.getDeviceList()) {
            wifiP2pArrayAdapter.add(peer.deviceName);
        }
    }

    @Override
    public void onChannelDisconnected() {
        if(mManager != null) {
            finish();
            Toast.makeText(this, "Connection lost. Reconnecting", Toast.LENGTH_SHORT).show();
            mManager.initialize(this,getMainLooper(),this);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(resultCode == RESULT_CANCELED) {
            mReceiver.disconnect();
        }
    }

    @Override
    protected void onDestroy() {
        mReceiver.disconnect();
        super.onDestroy();
    }
}
