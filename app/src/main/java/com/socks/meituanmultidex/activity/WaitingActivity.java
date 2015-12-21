package com.socks.meituanmultidex.activity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.socks.meituanmultidex.MeituanApplication;
import com.socks.meituanmultidex.R;

import java.util.Timer;
import java.util.TimerTask;

public class WaitingActivity extends BaseActivity {

    private Timer timer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wait);
        waitForDexAvailable();
    }

    private void waitForDexAvailable() {

        final Intent intent = getIntent();
        final String className = intent.getStringExtra(TAG_TARGET);

        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                while (!MeituanApplication.isDexAvailable()) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    Log.d("TAG", "waiting");
                }
                intent.setClassName(getPackageName(), className);
                startActivity(intent);
                finish();
            }
        }, 0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) {
            timer.cancel();
        }
    }

}
