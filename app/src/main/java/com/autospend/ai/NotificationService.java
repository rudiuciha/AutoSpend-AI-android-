package com.autospend.ai;

import android.app.Notification;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationService extends NotificationListenerService {

    private static final String TAG = "AutoSpendNotif";
    private AiParser aiParser;
    private AppDatabase db;
    private ExecutorService executor;

    // Package names of financial apps to monitor
    private static final Set<String> FINANCIAL_APPS = new HashSet<>(Arrays.asList(
        "com.gojek.gopay",
        "id.co.ovo.android",
        "id.dana",
        "com.shopee.id",
        "com.linkaja",
        "id.co.bca.mybca",
        "com.bni.android",
        "id.co.mandiri.internet",
        "id.co.bri.brimo",
        "id.co.cimb.niagaclicks",
        "com.permata.mobile",
        "id.co.btn.btnmobile",
        "id.co.maybank2u.android",
        "com.gojek.app",
        "com.grabtaxi.passenger",
        "com.tokopedia.tkpd",
        "com.shopee.id",
        "com.bukalapak.android",
        "id.co.qris",
        "id.co.nobu.mobile"
    ));

    // Keywords that indicate a transaction notification
    private static final String[] TRANSACTION_KEYWORDS = {
        "rp ", "rp.", "idr", "nominal", "pembayaran", "transaksi",
        "berhasil", "sukses", "debit", "transfer", "belanja", "bayar",
        "payment", "paid", "charged", "debited"
    };

    @Override
    public void onCreate() {
        super.onCreate();
        aiParser = new AiParser(this);
        db = AppDatabase.getInstance(this);
        executor = Executors.newSingleThreadExecutor();
        Log.d(TAG, "NotificationService started");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName();

        // Check if it's from a financial app
        if (!isFinancialApp(packageName)) return;

        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras;

        String title = extras.getString(Notification.EXTRA_TITLE, "");
        String text  = extras.getString(Notification.EXTRA_TEXT, "");
        String bigText = extras.getString(Notification.EXTRA_BIG_TEXT, "");

        // Use the longest text available
        String fullText = bigText.length() > text.length() ? bigText : text;
        if (!title.isEmpty()) fullText = title + ". " + fullText;

        Log.d(TAG, "Notification from " + packageName + ": " + fullText);

        // Check if it looks like a transaction
        if (!isTransactionNotification(fullText)) return;

        final String textToParse = fullText;
        final String appName = getAppName(packageName);

        // Parse with AI in background
        aiParser.parse(textToParse, appName, new AiParser.ParseCallback() {
            @Override
            public void onSuccess(Transaction transaction) {
                if (transaction.amount <= 0) {
                    Log.d(TAG, "Skipping - amount is 0");
                    return;
                }
                executor.execute(() -> {
                    db.transactionDao().insert(transaction);
                    Log.d(TAG, "Saved transaction: " + transaction.merchant + " " + transaction.getFormattedAmount());

                    // Broadcast to update UI
                    Intent intent = new Intent("com.autospend.TRANSACTION_ADDED");
                    sendBroadcast(intent);
                });
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Parse error: " + message);
            }
        });
    }

    private boolean isFinancialApp(String packageName) {
        if (FINANCIAL_APPS.contains(packageName)) return true;
        // Also catch SMS apps (bank SMS notifications)
        return packageName.contains("sms") || packageName.contains("message") ||
               packageName.contains("mms") || packageName.equals("com.google.android.apps.messaging");
    }

    private boolean isTransactionNotification(String text) {
        if (text == null || text.length() < 10) return false;
        String lower = text.toLowerCase();
        for (String keyword : TRANSACTION_KEYWORDS) {
            if (lower.contains(keyword)) return true;
        }
        return false;
    }

    private String getAppName(String packageName) {
        if (packageName.contains("gopay") || packageName.contains("gojek")) return "GoPay";
        if (packageName.contains("ovo")) return "OVO";
        if (packageName.contains("dana")) return "DANA";
        if (packageName.contains("shopee")) return "ShopeePay";
        if (packageName.contains("linkaja")) return "LinkAja";
        if (packageName.contains("bca")) return "BCA";
        if (packageName.contains("bni")) return "BNI";
        if (packageName.contains("mandiri")) return "Mandiri";
        if (packageName.contains("bri")) return "BRI";
        if (packageName.contains("sms") || packageName.contains("message")) return "SMS";
        return "Lainnya";
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executor != null) executor.shutdown();
    }
}
