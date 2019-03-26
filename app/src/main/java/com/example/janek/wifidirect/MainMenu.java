package com.example.janek.wifidirect;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.Toast;

public class MainMenu extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_menu);
        Button bt_connect =(Button) findViewById(R.id.bt_connect);
        Button bt_help = (Button) findViewById(R.id.bt_about);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        bt_connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent connectDevices = new Intent(MainMenu.this, MainActivity.class);
                startActivity(connectDevices);
            }
        });

        bt_help.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainMenu.this, "This will display info about app", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
