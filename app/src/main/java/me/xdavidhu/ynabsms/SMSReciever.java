package me.xdavidhu.ynabsms;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;
import android.widget.Toast;

import java.security.SecureRandom;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class SMSReciever extends BroadcastReceiver {

    private static final String TAG = SMSReciever.class.getSimpleName();
    public static final String pdu_type = "pdus";

    public void parseSMS(final Context context, String body) {

        int balance = 0;
        String description = "";
        String date = "";
        boolean success = true;

        Pattern uc_pattern = Pattern.compile("^\\d\\d\\d$");
        Pattern uc_balance_pattern = Pattern.compile("(?<=\\+).*(?= HUF)");
        Pattern aa_pattern = Pattern.compile("\\.\\.\\.$");
        Pattern aa_balance_pattern = Pattern.compile("(?<=Egy:\\+).*(?=,)");


        // Usual Card Transaction
        // Starts like this:
        // "200801 17:46"

        if (uc_pattern.matcher(body.substring(0, 3)).matches()) {

            String[] body_list = body.split("; ");

            if (body_list.length != 3) {

                // Parse balance

                Matcher matcher = uc_balance_pattern.matcher(body_list[3]);
                if (matcher.find()) {
                    balance = Integer.valueOf(matcher.group().replace(".", ""));
                } else {
                    sendFailNotification(context,"Balance not found in transaction.");
                    success = false;
                }

                // Parse description

                description = body_list[1];

                // Parse date

                String date_number = body.substring(0, 6);
                DateFormat df_in = new SimpleDateFormat("yyMMdd");
                try {
                    Date result = df_in.parse(date_number);
                    DateFormat df_out = new SimpleDateFormat("yyyy-MM-dd");
                    date = df_out.format(result);
                } catch (ParseException e) {
                    e.printStackTrace();
                    sendFailNotification(context,"Transaction date is invalid.");
                    success = false;
                }

                // Ignore if it is a finalization message. (Happens after "Zàrolàs", we are uploading that)

                if (body_list[0].contains("Kàrtyàs foglalàs véglegesitése")) {
                    sendFailNotification(context,"Transaction is a finalization message. Ignoring...");
                    success = false;
                }

            } else {
                sendFailNotification(context,"Transaction is too short, it is probably failed.");
                success = false;
            }
        }

        // Account Activity Transaction
        // Starts like this:
        // "...1234 Szàmla"

        else if (aa_pattern.matcher(body.substring(0, 3)).matches()) {

            String[] body_list = body.split("; ");

            // Parse balance

            Matcher matcher = aa_balance_pattern.matcher(body);
            if (matcher.find()) {
                balance = Integer.valueOf(matcher.group().replace(".", ""));
            } else {
                sendFailNotification(context,"Balance not found in transaction.");
                success = false;
            }

            description = body;

            // Parse date
            // 3 different date formats are used
            // 1: "...1234 Szàmla (200802)"
            // 2: "...1234 Szàmla (2020-08-2)"
            // 3: "...1234 Szàmla (2020-08-12)" - when day number is > 9

            String date_number;
            DateFormat df_in;

            if (body.substring(16, 22).contains("-")) {
                if (body.substring(25, 26).equals(")")) {
                    date_number = body.substring(16, 25);
                } else {
                    date_number = body.substring(16, 26);
                }
                df_in = new SimpleDateFormat("yyyy-MM-dd");
            } else {
                date_number = body.substring(16, 22);
                df_in = new SimpleDateFormat("yyMMdd");
            }

            try {
                Date result = df_in.parse(date_number);
                DateFormat df_out = new SimpleDateFormat("yyyy-MM-dd");
                date = df_out.format(result);

            } catch (ParseException e) {
                sendFailNotification(context,"Transaction date is invalid.");
                success = false;
            }

        }

        else {
            sendFailNotification(context,"SMS is not a transaction. Ignoring...");
            success = false;
        }


        if (success) {

            Log.d("ynabsms", "Balance: " + Integer.toString(balance));
            Log.d("ynabsms", "Description: " + description);
            Log.d("ynabsms", "Date: " + date);
            ApiUploadService.startActionUpload(context, balance, description, date);

        }

    }

    @Override
    public void onReceive(Context context, Intent intent) {

        final SharedPreferences sharedPref = context.getSharedPreferences("preferences", Context.MODE_PRIVATE);

        if (sharedPref.getBoolean("toggle", false)) {
            // Get the SMS message.
            Bundle bundle = intent.getExtras();
            SmsMessage[] msgs;
            String format = bundle.getString("format");

            Object[] pdus = (Object[]) bundle.get(pdu_type);
            if (pdus != null) {

                Map<String, String> messages = new HashMap<>();
                for (int i = 0; i < pdus.length; i++) {
                    SmsMessage msg;
                    // Check Android version and use appropriate createFromPdu.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        msg = SmsMessage.createFromPdu((byte[]) pdus[i], format);
                    } else {
                        // If Android version L or older:
                        msg = SmsMessage.createFromPdu((byte[]) pdus[i]);
                    }

                    if (messages.containsKey(msg.getOriginatingAddress())){
                        // Long SMS
                        String previousparts = messages.get(msg.getOriginatingAddress());
                        messages.put(msg.getOriginatingAddress(), previousparts + msg.getMessageBody());
                    } else {
                        messages.put(msg.getOriginatingAddress(), msg.getMessageBody());
                    }
                }

                for (Map.Entry<String, String> message : messages.entrySet()) {

                    Pattern sender_pattern = Pattern.compile("^\\+36\\d\\d9400700$");
                    if (sender_pattern.matcher(message.getKey()).matches()) {
                        parseSMS(context, message.getValue());
                    }

                }

            }
        }
    }

    public int createNotificationID(){
        SecureRandom random = new SecureRandom();
        int num = random.nextInt(100000);
        String formatted = String.format("%05d", num);
        int id = Integer.parseInt(formatted);
        return id;
    }

    public void sendFailNotification(final Context context, String reason){

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, MainActivity.NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.notification_fail_icon)
                .setContentTitle("Failed to upload transaction!")
                .setContentText(reason)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.notify(createNotificationID(), builder.build());

    }
}
