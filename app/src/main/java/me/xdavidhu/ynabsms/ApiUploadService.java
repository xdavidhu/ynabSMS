package me.xdavidhu.ynabsms;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.SecureRandom;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ApiUploadService extends IntentService {
    private static final String ACTION_UPLOAD = "me.xdavidhu.ynabsms.action.UPLOAD";

    private static final String EXTRA_AMOUNT = "me.xdavidhu.ynabsms.extra.AMOUNT";
    private static final String EXTRA_ISINCOME = "me.xdavidhu.ynabsms.extra.ISINCOME";
    private static final String EXTRA_CURRENCY = "me.xdavidhu.ynabsms.extra.CURRENCY";
    private static final String EXTRA_BALANCE = "me.xdavidhu.ynabsms.extra.BALANCE";
    private static final String EXTRA_DESCRIPTION = "me.xdavidhu.ynabsms.extra.DESCRIPTION";
    private static final String EXTRA_DATE = "me.xdavidhu.ynabsms.extra.DATE";

    OkHttpClient client = new OkHttpClient();

    public ApiUploadService() {
        super("ApiUploadService");
    }

    public static void startActionUpload(Context context, double amount, boolean is_income, String currency, int balance, String description, String date) {
        Intent intent = new Intent(context, ApiUploadService.class);
        intent.setAction(ACTION_UPLOAD);

        intent.putExtra(EXTRA_AMOUNT, amount);
        intent.putExtra(EXTRA_ISINCOME, is_income);
        intent.putExtra(EXTRA_CURRENCY, currency);
        intent.putExtra(EXTRA_BALANCE, balance);
        intent.putExtra(EXTRA_DESCRIPTION, description);
        intent.putExtra(EXTRA_DATE, date);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_UPLOAD.equals(action)) {

                final double amount = intent.getDoubleExtra(EXTRA_AMOUNT,0.0);
                final boolean is_income = intent.getBooleanExtra(EXTRA_ISINCOME,false);
                final String currency = intent.getStringExtra(EXTRA_CURRENCY);
                final int balance = intent.getIntExtra(EXTRA_BALANCE,0);
                final String description = intent.getStringExtra(EXTRA_DESCRIPTION);
                final String date = intent.getStringExtra(EXTRA_DATE);
                uploadToApi(amount, is_income, currency, balance, description, date);
            }
        }
    }

    private void uploadToApi(final double amount, final boolean is_income, final String currency, final int balance, final String description, final String date) {

        final SharedPreferences sharedPref = getSharedPreferences("preferences", Context.MODE_PRIVATE);

        // Before calling uploadTransaction(), check if the difference between the 'amount' and the
        // 'sms_balance - current_balance' is small (10% bigger/smaller). If the difference is small,
        // upload the 'sms_balance - current_balance' (small diff is probably only from currency
        // conversions). If the difference is big, upload the 'amount' from the SMS, converted to HUF.
        //
        // This is added to fix an issue where OTP is sending some SMSs with a wrong balance (usually
        // at the 1st of every month).

        // If the currency is not HUF, convert it to HUF.
        int huf_amount;
        if (currency.equals("HUF")) {
            huf_amount = (int)amount;
        } else {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url("https://api.exchangerate.host/latest?base=" + currency.toUpperCase())
                    .build();

            try (Response response = client.newCall(request).execute()) {

                JSONObject apiResponse = null;
                try {
                    apiResponse = new JSONObject(response.body().string());
                    JSONObject rates = apiResponse.getJSONObject("rates");
                    double huf_rate = rates.getDouble("HUF");
                    Log.d("ynabsms", "HUF rate is: " + Double.toString(huf_rate));
                    huf_amount = (int)(amount * huf_rate);

                } catch (JSONException e) {
                    sendFailNotification("Failed to parse API response from currency exchange endpoint. No such currency?");
                    e.printStackTrace();
                    return;
                }

            } catch (IOException e) {
                sendFailNotification("API request to currency exchange endpoint failed.");
                return;
            }
        }

        Log.d("ynabsms", "Amount is: " + Double.toString(amount));
        Log.d("ynabsms", "HUF amount is: " + Integer.toString(huf_amount));

        // Get the current balance from YNAB API.
        int current_balance;
        String account_id;

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url("https://api.youneedabudget.com/v1/budgets/last-used")
                .addHeader("accept", "application/json")
                .addHeader("Authorization", "Bearer " + sharedPref.getString("apikey", ""))
                .build();

        try (Response response = client.newCall(request).execute()) {

            JSONObject apiResponse = null;
            try {
                apiResponse = new JSONObject(response.body().string());
                JSONObject data = apiResponse.getJSONObject("data");
                JSONObject budget = data.getJSONObject("budget");
                JSONArray accounts = budget.getJSONArray("accounts");
                JSONObject account = accounts.getJSONObject(0);

                current_balance = account.getInt("balance")/1000;
                account_id = account.getString("id");

            } catch (JSONException e) {
                sendFailNotification("Failed to parse API response from original balance endpoint.");
                e.printStackTrace();
                return;
            }

        } catch (IOException e) {
            sendFailNotification("API request to original balance endpoint failed.");
            return;
        }

        if (!is_income) {
            huf_amount = huf_amount * -1;
            Log.d("ynabsms", "Not income, multiplying huf amount by -1: " + Integer.toString(huf_amount));
        }

        // Check if the difference is small between the two amounts.
        Log.d("ynabsms", "balance - current_balance: " + Double.toString((double)((balance - current_balance))));
        double diff = (double)((balance - current_balance)) / (double)huf_amount;
        Log.d("ynabsms", "Diff is: " + Double.toString(diff));
        if (diff < 0.9 || diff > 1.1) {
            // Difference is big, uploading amount from SMS...
            Log.d("ynabsms", "Uploading huf_amount...");
            uploadTransaction(account_id, huf_amount, description, date);
        } else {
            // Difference is small, uploading more precise 'sms_balance - current_balance'
            Log.d("ynabsms", "Uploading balance - current_balance...");
            uploadTransaction(account_id, (balance - current_balance), description, date);
        }

    }

    private void uploadTransaction(final String accountId, final int value, final String description, final String date){

        final SharedPreferences sharedPref = getSharedPreferences("preferences", Context.MODE_PRIVATE);

        JSONObject body_json = new JSONObject();
        JSONObject transaction_json = new JSONObject();
        try {
            transaction_json.put("account_id", accountId);
            transaction_json.put("date", date);
            transaction_json.put("amount", value*1000);
            transaction_json.put("memo", description);
            transaction_json.put("approved", true);
            body_json.put("transaction", transaction_json);
        } catch (JSONException e) {
            sendFailNotification("Failed to create new transaction object.");
            e.printStackTrace();
        }

        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(body_json.toString(), JSON);

        Request request = new Request.Builder()
                .url("https://api.youneedabudget.com/v1/budgets/last-used/transactions")
                .addHeader("accept", "application/json")
                .addHeader("Authorization", "Bearer " + sharedPref.getString("apikey", ""))
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {

            if (response.code() == 201) {
                NotificationCompat.Builder builder = new NotificationCompat.Builder(ApiUploadService.this.getApplicationContext(), MainActivity.NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.drawable.notification_success_icon)
                        .setContentTitle("Transaction uploaded!")
                        .setContentText("New transaction successfully uploaded to YNAB.")
                        .setPriority(NotificationCompat.PRIORITY_HIGH);

                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(ApiUploadService.this.getApplicationContext());
                notificationManager.notify(createNotificationID(), builder.build());
            } else {
                Log.d("ynabsms", response.body().string());
                sendFailNotification("Status code '" + Integer.toString(response.code()) + "' received from YNAB API.");
            }

        } catch (IOException e) {
            sendFailNotification("API request to transactions endpoint failed.");
        }

    }

    public int createNotificationID(){
        SecureRandom random = new SecureRandom();
        int num = random.nextInt(100000);
        String formatted = String.format("%05d", num);
        int id = Integer.parseInt(formatted);
        return id;
    }

    public void sendFailNotification(String reason){

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ApiUploadService.this.getApplicationContext(), MainActivity.NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.notification_fail_icon)
                .setContentTitle("Failed to upload transaction!")
                .setContentText(reason)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(ApiUploadService.this.getApplicationContext());
        notificationManager.notify(createNotificationID(), builder.build());

    }
}
