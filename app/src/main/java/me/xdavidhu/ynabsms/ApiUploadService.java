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

    private static final String EXTRA_BALANCE = "me.xdavidhu.ynabsms.extra.BALANCE";
    private static final String EXTRA_DESCRIPTION = "me.xdavidhu.ynabsms.extra.DESCRIPTION";
    private static final String EXTRA_DATE = "me.xdavidhu.ynabsms.extra.DATE";

    OkHttpClient client = new OkHttpClient();

    public ApiUploadService() {
        super("ApiUploadService");
    }

    public static void startActionUpload(Context context, int balance, String description, String date) {
        Intent intent = new Intent(context, ApiUploadService.class);
        intent.setAction(ACTION_UPLOAD);
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
                final int balance = intent.getIntExtra(EXTRA_BALANCE,0);
                final String description = intent.getStringExtra(EXTRA_DESCRIPTION);
                final String date = intent.getStringExtra(EXTRA_DATE);
                handleActionFoo(balance, description, date);
            }
        }
    }

    private void handleActionFoo(final int balance, final String description, final String date) {

        final SharedPreferences sharedPref = getSharedPreferences("preferences", Context.MODE_PRIVATE);

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url("https://api.youneedabudget.com/v1/budgets/last-used")
                .addHeader("accept", "application/json")
                .addHeader("Authorization", "Bearer " + sharedPref.getString("apikey", ""))
                .build();

        client.newCall(request)
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(final Call call, IOException e) {

                        sendFailNotification("API request to original balance endpoint failed.");

                    }

                    @Override
                    public void onResponse(Call call, final Response response) throws IOException {

                        JSONObject apiResponse = null;
                        try {
                            apiResponse = new JSONObject(response.body().string());
                            JSONObject data = apiResponse.getJSONObject("data");
                            JSONObject budget = data.getJSONObject("budget");
                            JSONArray accounts = budget.getJSONArray("accounts");
                            JSONObject account = accounts.getJSONObject(0);
                            int originalBalance = account.getInt("balance")/1000;
                            String accountId = account.getString("id");
                            int value = (originalBalance - balance)*-1;

                            uploadTransaction(accountId, value, description, date);

                        } catch (JSONException e) {
                            sendFailNotification("Failed to parse API response from original balance endpoint.");
                            e.printStackTrace();
                        }

                    }
                });

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

        client.newCall(request)
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(final Call call, IOException e) {

                        sendFailNotification("API request to transactions endpoint failed.");

                    }

                    @Override
                    public void onResponse(Call call, final Response response) throws IOException {

                        if (response.code() == 200) {
                            Log.d("ynabsms", response.body().string());
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
                    }
                });
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
