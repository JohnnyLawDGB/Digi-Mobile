package com.digimobile.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

/**
 * SECURITY DISCLAIMER: This activity shows a first-run warning dialog about the
 * experimental, hobby nature of Digi-Mobile. It is a UX reminder and not a
 * replacement for reading the project docs or managing personal risk.
 */
public class MainActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "digimobile_prefs";
    private static final String KEY_DISCLAIMER_ACK = "disclaimer_ack_v1";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // TODO: replace with a real layout when the app UI is built.
        // setContentView(R.layout.activity_main);

        if (!hasAcknowledgedDisclaimer()) {
            showSecurityDisclaimer();
        }
    }

    private boolean hasAcknowledgedDisclaimer() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_DISCLAIMER_ACK, false);
    }

    private void showSecurityDisclaimer() {
        new AlertDialog.Builder(this)
                .setTitle("Digi-Mobile disclaimer")
                .setMessage("Digi-Mobile runs a pruned DigiByte node on a consumer Android device. " +
                        "This build is experimental and not a bank vault. Do not store large balances here.\n\n" +
                        "Proceed only if you understand the risks.")
                .setPositiveButton("I Understand", (dialog, which) -> {
                    markDisclaimerAccepted();
                    dialog.dismiss();
                })
                .setNegativeButton("Exit", (dialog, which) -> {
                    dialog.dismiss();
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    private void markDisclaimerAccepted() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_DISCLAIMER_ACK, true).apply();
        // TODO: consider adding a settings option to view the disclaimer again or reset this flag.
    }
}
