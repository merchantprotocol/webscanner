package com.google.zxing.client.android;

/**
 * Created by michael on 22/06/14.
 */
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class HttpServerUI extends Activity {
    private Button serverbtn;
    private TextView stattxt;
    private boolean serverstarted =false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.httpserv_fragment);
        serverbtn = (Button) findViewById(R.id.serverbtn);
        Button activitybtn = (Button) findViewById(R.id.activitybtn);
        activitybtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(HttpServerUI.this, CaptureActivity.class);
                startActivity(intent);
            }
        });
        stattxt = (TextView) findViewById(R.id.stattxt);
        serverstarted = isServiceRunning(HttpServer.class);
        setButton(serverstarted);
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public void startService(){
        Intent sintent = new Intent(HttpServerUI.this, HttpServer.class);
        startService(sintent);
        // set new button click
        setButton(true);
    }

    public void stopService(){
        Intent sintent = new Intent(HttpServerUI.this, HttpServer.class);
        stopService(sintent);
        // set new button click
        setButton(false);
    }

    private void setButton(boolean started){
        if (started){
            serverbtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    stopService();
                }
            });
            serverbtn.setText("Stop Server");
            stattxt.setText("Server Running");
        } else {
            serverbtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    startService();
                }
            });
            serverbtn.setText("Start Server");
            stattxt.setText("Server Stopped");
        }
    }

}