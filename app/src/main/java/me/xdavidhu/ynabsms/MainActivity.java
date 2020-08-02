package me.xdavidhu.ynabsms;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends Activity {

    Switch toggleSwitch;
    EditText apikey;
    Button apikeyUpdate;

    public static final int MY_PERMISSIONS_REQUEST_RECIEVE_SMS = 1;
    public static final String NOTIFICATION_CHANNEL_ID = "me.xdavidhu.ynabsms.notifications";

    public void requestPermission() {
        ActivityCompat.requestPermissions(MainActivity.this,
                new String[]{Manifest.permission.RECEIVE_SMS},
                MY_PERMISSIONS_REQUEST_RECIEVE_SMS);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {

            requestPermission();

        } else {
            // Permission has already been granted
        }

        toggleSwitch = findViewById(R.id.toggle);
        apikey = findViewById(R.id.apikey);
        apikeyUpdate = findViewById(R.id.apikey_update);

        Context context = MainActivity.this;
        final SharedPreferences sharedPref = context.getSharedPreferences("preferences", Context.MODE_PRIVATE);

        final SharedPreferences.Editor editor = sharedPref.edit();

        if (!sharedPref.contains("toggle")) {
            editor.putBoolean("toggle", false);
            editor.apply();
        }
        if (!sharedPref.contains("apikey")) {
            editor.putString("apikey", "");
            editor.apply();
        }

        toggleSwitch.setChecked(sharedPref.getBoolean("toggle", false));
        apikey.setText(sharedPref.getString("apikey", ""));

        apikeyUpdate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                editor.putString("apikey", apikey.getText().toString());
                Toast.makeText(MainActivity.this, "API Key Updated!", Toast.LENGTH_SHORT).show();
                editor.apply();
            }
        });

        toggleSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b) {
                    editor.putBoolean("toggle", true);
                    Toast.makeText(MainActivity.this, "ynabSMS enabled!", Toast.LENGTH_SHORT).show();
                    editor.apply();
                } else {
                    editor.putBoolean("toggle", false);
                    Toast.makeText(MainActivity.this, "ynabSMS disabled!", Toast.LENGTH_SHORT).show();
                    editor.apply();
                }
            }
        });

        createNotificationChannel();

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {

        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_RECIEVE_SMS: {

                // If request is cancelled, the result arrays are empty.

                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // Permission granted.

                } else {

                    requestPermission();

                }
                return;
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            CharSequence name = "ynabSMS";
            String description = "Notifications for ynabSMS";

            int importance = NotificationManager.IMPORTANCE_DEFAULT;

            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }



}