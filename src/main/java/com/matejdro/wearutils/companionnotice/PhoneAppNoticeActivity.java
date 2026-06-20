package com.matejdro.wearutils.companionnotice;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.view.View;

import androidx.wear.activity.ConfirmationActivity;

import com.google.android.wearable.intent.RemoteIntent;
import com.matejdro.wearutils.R;

public class PhoneAppNoticeActivity extends Activity {
    private View noPhoneErrorView;
    private View loadingView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone_app_notice);

        noPhoneErrorView = findViewById(R.id.no_phone_error_view);
        loadingView = findViewById(R.id.progress);
    }

    public void openGithub(View view) {
        Intent githubIntent = new Intent(Intent.ACTION_VIEW);
        githubIntent.addCategory(Intent.CATEGORY_BROWSABLE);
        githubIntent.setData(Uri.parse(getString(R.string.no_phone_app_github_url)));

        ResultReceiver resultReceiver = new ResultReceiver(new Handler()) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
                Intent confirmationIntent = new Intent(PhoneAppNoticeActivity.this, ConfirmationActivity.class);

                if (resultCode == RemoteIntent.RESULT_OK) {
                    confirmationIntent.putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE,
                            ConfirmationActivity.OPEN_ON_PHONE_ANIMATION);
                    confirmationIntent.putExtra(ConfirmationActivity.EXTRA_MESSAGE,
                            getString(R.string.github_page_opened));
                } else {
                    confirmationIntent.putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE,
                            ConfirmationActivity.FAILURE_ANIMATION);
                    confirmationIntent.putExtra(ConfirmationActivity.EXTRA_MESSAGE,
                            getString(R.string.github_page_opening_failed)
                    );

                }

                try {
                    startActivity(confirmationIntent);
                } catch (ActivityNotFoundException e) {
                    // Don't show confirmation, just exit
                }
                finish();
            }
        };

        RemoteIntent.startRemoteActivity(this, githubIntent, resultReceiver);

        noPhoneErrorView.setVisibility(View.GONE);
        loadingView.setVisibility(View.VISIBLE);
    }
}
