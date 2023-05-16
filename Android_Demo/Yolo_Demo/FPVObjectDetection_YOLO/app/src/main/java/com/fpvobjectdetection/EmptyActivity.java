package com.fpvobjectdetection;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;

public class EmptyActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        super.onBackPressed();
        Intent intent = new Intent(EmptyActivity.this, MainActivity.class);
        EmptyActivity.this.startActivity(intent);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
    }

    @Override public void onBackPressed() {
        super.onBackPressed();
    }
}