package com.socks.meituanmultidex.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.socks.meituanmultidex.MeituanApplication;
import com.socks.meituanmultidex.R;

public class MainActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MeituanApplication.attachInstrumentation();
    }

    public void click(View view) {
        Intent intent = new Intent(this, SecondaryActivity.class);
        startActivity(intent);
    }

}
