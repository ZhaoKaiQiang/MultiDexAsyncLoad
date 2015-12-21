package com.socks.meituanmultidex.activity;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;

public class BaseActivity extends AppCompatActivity {

    public static final String TAG_TARGET = "target";

    @Override
    public void startActivity(Intent intent) {
        intent.putExtra(TAG_TARGET, intent.getComponent().getClassName());
        super.startActivity(intent);
    }
}
