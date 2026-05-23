package com.autospend.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

import okhttp3.*;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class AiParser {

    private static final String TAG = "AiParser";
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-sonnet-4-20250514";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final Gson gson;
    private final Context context;

    public interface ParseCallback {
        void onSuccess(Transaction transaction);
        void onError(String message);
    }

    public AiParser(Context context) {
        this.context = context;
        this.gson = new Gson();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public void parse(String text, String sourceApp, ParseCallback callback) {
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            // Fallback to regex parser
            Transaction t = regexParse(text, sourceApp);
            callback.onSuccess(t);
            return;
        }

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        JsonObject body = new JsonObject();
        body.addProperty("model", MODEL);
        body.addProperty("max_tokens", 500);

        JsonObject system = new JsonObject();
        body.addProperty("system",
            "Kamu adalah parser transaksi keuangan Indonesia. Ekstrak informasi dari teks notifikasi " +
            "dan kembalikan HANYA JSON valid tanpa markdown, tanpa penjelasan:\n" +
            "{\"amount\":45000,\"merchant\":\"Nama Merchant\",\"category\":\"food|transport|shopping|health|entertainment|bills|other\"," +
            "\"payment_method\":\"GoPay|OVO|DANA|BCA|Mandiri|BNI|BRI|Cash|Other\",\"date\":\"YYYY-MM-DD\"}\n" +
            "Hari ini: " + today + ". Jika tanggal tidak ada, gunakan hari ini. Jika nominal tidak ada, amount=0."
        );

        JsonArray messages = new JsonArray();
        JsonObject msg = new JsonObject();
        msg.addProperty("role", "user");
        msg.addProperty("content", "Parse transaksi ini:\n" + text);
        messages.add(msg);
        body.add("messages", messages);

        Request request = new Request.Builder()
                .url(API_URL)
                .post(RequestBody.create(gson.toJson(body), JSON))
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("Content-Type", "application/json")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "API call failed: " + e.getMessage());
                // Fallback to regex
                Transaction t = regexParse(text, sourceApp);
                callback.onSuccess(t);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String responseBody = response.body().string();
                    JsonObject resp = gson.fromJson(responseBody, JsonObject.class);
                    String raw = resp.getAsJsonArray("content")
                            .get(0).getAsJsonObject()
                            .get("text").getAsString();

                    raw = raw.replaceAll("```json|```", "").trim();
                    JsonObject parsed = gson.fromJson(raw, JsonObject.class);

                    Transaction t = new Transaction();
                    t.amount = parsed.has("amount") ? parsed.get("amount").getAsDouble() : 0;
                    t.merchant = parsed.has("merchant") ? parsed.get("merchant").getAsString() : "Unknown";
                    t.category = parsed.has("category") ? parsed.get("category").getAsString() : "other";
                    t.paymentMethod = parsed.has("payment_method") ? parsed.get("payment_method").getAsString() : "Other";
                    t.date = parsed.has("date") ? parsed.get("date").getAsString() : today;
                    t.rawText = text;
                    t.sourceApp = sourceApp;

                    callback.onSuccess(t);
                } catch (Exception e) {
                    Log.e(TAG, "Parse error: " + e.getMessage());
                    Transaction t = regexParse(text, sourceApp);
                    callback.onSuccess(t);
                }
            }
        });
    }

    // Fallback regex parser (no API needed)
    private Transaction regexParse(String text, String sourceApp) {
        Transaction t = new Transaction();
        t.rawText = text;
        t.sourceApp = sourceApp;
        t.date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        // Extract amount (Rp 45.000 or Rp45000 or IDR 45,000)
        java.util.regex.Pattern amtPattern = java.util.regex.Pattern.compile(
            "(?:Rp\\.?|IDR)\\s*([\\d.,]+)", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher amtMatcher = amtPattern.matcher(text);
        if (amtMatcher.find()) {
            String amtStr = amtMatcher.group(1).replaceAll("[.,](?=\\d{3}(?:[.,]|$))", "").replace(",", ".");
            try { t.amount = Double.parseDouble(amtStr.replace(".", "")); } catch (Exception e) { t.amount = 0; }
        }

        // Extract payment method
        String lower = text.toLowerCase();
        if (lower.contains("gopay"))          t.paymentMethod = "GoPay";
        else if (lower.contains("ovo"))       t.paymentMethod = "OVO";
        else if (lower.contains("dana"))      t.paymentMethod = "DANA";
        else if (lower.contains("shopeepay")) t.paymentMethod = "ShopeePay";
        else if (lower.contains("linkaja"))   t.paymentMethod = "LinkAja";
        else if (lower.contains("bca"))       t.paymentMethod = "BCA";
        else if (lower.contains("mandiri"))   t.paymentMethod = "Mandiri";
        else if (lower.contains("bni"))       t.paymentMethod = "BNI";
        else if (lower.contains("bri"))       t.paymentMethod = "BRI";
        else                                  t.paymentMethod = "Other";

        // Extract category by keywords
        if (lower.matches(".*(mcdonald|kfc|pizza|burger|resto|warung|makan|cafe|coffee|starbucks|bakery|sushi|boba|indomie|grab food|gofood|shopee food).*"))
            t.category = "food";
        else if (lower.matches(".*(grab|gojek|ojek|taxi|transjakarta|busway|kereta|parkir|toll|pertamina|shell|bensin|bbm).*"))
            t.category = "transport";
        else if (lower.matches(".*(indomaret|alfamart|tokopedia|shopee|lazada|blibli|beli|belanja|mall|supermarket).*"))
            t.category = "shopping";
        else if (lower.matches(".*(apotek|dokter|rumah sakit|rs |klinik|halodoc|alodokter|obat|kesehatan).*"))
            t.category = "health";
        else if (lower.matches(".*(netflix|spotify|youtube|game|bioskop|cinema|hiburan|steam).*"))
            t.category = "entertainment";
        else if (lower.matches(".*(listrik|pln|pdam|air |internet|tagihan|token|pulsa|cicilan|angsuran).*"))
            t.category = "bills";
        else
            t.category = "other";

        // Extract merchant - look for "ke [Merchant]" or "di [Merchant]" pattern
        java.util.regex.Pattern mPattern = java.util.regex.Pattern.compile(
            "(?:ke|di|at|to)\\s+([A-Z][\\w\\s]{2,30}?)(?:\\s+(?:berhasil|sukses|rp|idr|pada|tanggal|,))", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher mMatcher = mPattern.matcher(text);
        if (mMatcher.find()) {
            t.merchant = mMatcher.group(1).trim();
        } else {
            t.merchant = "Unknown";
        }

        // Extract date
        java.util.regex.Pattern datePattern = java.util.regex.Pattern.compile(
            "(\\d{1,2})/(\\d{1,2})/(\\d{4})");
        java.util.regex.Matcher dateMatcher = datePattern.matcher(text);
        if (dateMatcher.find()) {
            t.date = dateMatcher.group(3) + "-" +
                     String.format("%02d", Integer.parseInt(dateMatcher.group(2))) + "-" +
                     String.format("%02d", Integer.parseInt(dateMatcher.group(1)));
        }

        return t;
    }

    private String getApiKey() {
        SharedPreferences prefs = context.getSharedPreferences("autospend_prefs", Context.MODE_PRIVATE);
        return prefs.getString("api_key", "");
    }
}
