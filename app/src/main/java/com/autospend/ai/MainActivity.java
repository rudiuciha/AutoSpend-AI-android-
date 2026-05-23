package com.autospend.ai;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private AppDatabase db;
    private AiParser aiParser;
    private ExecutorService executor;
    private Handler mainHandler;

    // Views - Bottom Nav
    private View navDashboard, navParse, navHistory;
    private View pageDashboard, pageParse, pageHistory;

    // Dashboard
    private TextView tvTotalAmount, tvTxnCount, tvMaxAmount, tvTopCat;
    private LinearLayout catContainer;
    private RecyclerView rvRecentTxn;

    // Parse
    private EditText etNotifText;
    private Button btnParse;
    private View cardResult;
    private TextView tvMerchant, tvAmount, tvCategory, tvMethod, tvDate, tvRaw;
    private Button btnSave;

    // History
    private EditText etSearch;
    private RecyclerView rvHistory;

    private Transaction currentParsed = null;
    private String currentFilter = "week";

    private BroadcastReceiver txnReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshDashboard();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = AppDatabase.getInstance(this);
        aiParser = new AiParser(this);
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        initViews();
        setupNavigation();
        setupDashboard();
        setupParsePage();
        setupHistoryPage();

        checkNotificationPermission();
        showPage("dashboard");

        // Register receiver for auto-captured transactions
        ContextCompat.registerReceiver(this, txnReceiver,
            new IntentFilter("com.autospend.TRANSACTION_ADDED"),
            ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void initViews() {
        // Pages
        pageDashboard = findViewById(R.id.page_dashboard);
        pageParse     = findViewById(R.id.page_parse);
        pageHistory   = findViewById(R.id.page_history);

        // Nav
        navDashboard = findViewById(R.id.nav_dashboard);
        navParse     = findViewById(R.id.nav_parse);
        navHistory   = findViewById(R.id.nav_history);

        // Dashboard
        tvTotalAmount = findViewById(R.id.tv_total_amount);
        tvTxnCount    = findViewById(R.id.tv_txn_count);
        tvMaxAmount   = findViewById(R.id.tv_max_amount);
        tvTopCat      = findViewById(R.id.tv_top_cat);
        catContainer  = findViewById(R.id.cat_container);
        rvRecentTxn   = findViewById(R.id.rv_recent_txn);

        // Parse
        etNotifText = findViewById(R.id.et_notif_text);
        btnParse    = findViewById(R.id.btn_parse);
        cardResult  = findViewById(R.id.card_result);
        tvMerchant  = findViewById(R.id.tv_result_merchant);
        tvAmount    = findViewById(R.id.tv_result_amount);
        tvCategory  = findViewById(R.id.tv_result_category);
        tvMethod    = findViewById(R.id.tv_result_method);
        tvDate      = findViewById(R.id.tv_result_date);
        tvRaw       = findViewById(R.id.tv_result_raw);
        btnSave     = findViewById(R.id.btn_save);

        // History
        etSearch  = findViewById(R.id.et_search);
        rvHistory = findViewById(R.id.rv_history);
    }

    // ── Navigation ─────────────────────────────────────────
    private void setupNavigation() {
        navDashboard.setOnClickListener(v -> showPage("dashboard"));
        navParse.setOnClickListener(v -> showPage("parse"));
        navHistory.setOnClickListener(v -> showPage("history"));

        // Filter tabs
        findViewById(R.id.tab_week).setOnClickListener(v -> { currentFilter = "week"; refreshDashboard(); });
        findViewById(R.id.tab_month).setOnClickListener(v -> { currentFilter = "month"; refreshDashboard(); });
        findViewById(R.id.tab_all).setOnClickListener(v -> { currentFilter = "all"; refreshDashboard(); });

        // Settings
        findViewById(R.id.btn_settings).setOnClickListener(v -> showSettingsDialog());
    }

    private void showPage(String page) {
        pageDashboard.setVisibility("dashboard".equals(page) ? View.VISIBLE : View.GONE);
        pageParse.setVisibility("parse".equals(page) ? View.VISIBLE : View.GONE);
        pageHistory.setVisibility("history".equals(page) ? View.VISIBLE : View.GONE);

        navDashboard.setAlpha("dashboard".equals(page) ? 1f : 0.4f);
        navParse.setAlpha("parse".equals(page) ? 1f : 0.4f);
        navHistory.setAlpha("history".equals(page) ? 1f : 0.4f);

        if ("dashboard".equals(page)) refreshDashboard();
        if ("history".equals(page)) refreshHistory("");
    }

    // ── Dashboard ──────────────────────────────────────────
    private void setupDashboard() {
        rvRecentTxn.setLayoutManager(new LinearLayoutManager(this));
        refreshDashboard();
    }

    private void refreshDashboard() {
        executor.execute(() -> {
            long from = getFromTime();
            List<Transaction> txns = from > 0 ? db.transactionDao().getFrom(from) : db.transactionDao().getAll();
            double total = txns.stream().mapToDouble(t -> t.amount).sum();
            double max   = txns.stream().mapToDouble(t -> t.amount).max().orElse(0);

            // Category breakdown
            Map<String, Double> byCat = new LinkedHashMap<>();
            for (Transaction t : txns) {
                String cat = t.category != null ? t.category : "other";
                byCat.put(cat, byCat.getOrDefault(cat, 0.0) + t.amount);
            }
            List<Map.Entry<String, Double>> catEntries = new ArrayList<>(byCat.entrySet());
            catEntries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

            String topCat = catEntries.isEmpty() ? "-" :
                new Transaction() {{ category = catEntries.get(0).getKey(); }}.getCategoryIcon() + " " +
                new Transaction() {{ category = catEntries.get(0).getKey(); }}.getCategoryLabel();

            List<Transaction> recent = txns.subList(0, Math.min(5, txns.size()));
            final double finalTotal = total;
            final double finalMax = max;
            final int finalCount = txns.size();

            mainHandler.post(() -> {
                tvTotalAmount.setText(formatRp(finalTotal));
                tvTxnCount.setText(String.valueOf(finalCount));
                tvMaxAmount.setText(formatRp(finalMax));
                tvTopCat.setText(topCat);

                // Category list
                catContainer.removeAllViews();
                for (Map.Entry<String, Double> e : catEntries) {
                    Transaction dummy = new Transaction();
                    dummy.category = e.getKey();
                    int pct = finalTotal > 0 ? (int)(e.getValue() / finalTotal * 100) : 0;
                    long count = txns.stream().filter(t -> e.getKey().equals(t.category)).count();
                    addCatRow(dummy, e.getValue(), pct, (int) count);
                }

                // Recent transactions
                rvRecentTxn.setAdapter(new TransactionAdapter(recent, this::showTxnDetail));
            });
        });
    }

    private void addCatRow(Transaction dummy, double amount, int pct, int count) {
        View row = getLayoutInflater().inflate(R.layout.item_category, catContainer, false);
        ((TextView) row.findViewById(R.id.tv_cat_icon)).setText(dummy.getCategoryIcon());
        ((TextView) row.findViewById(R.id.tv_cat_name)).setText(dummy.getCategoryLabel());
        ((TextView) row.findViewById(R.id.tv_cat_count)).setText(count + " transaksi");
        ((TextView) row.findViewById(R.id.tv_cat_amount)).setText(formatRp(amount));
        ((TextView) row.findViewById(R.id.tv_cat_pct)).setText(pct + "%");
        View bar = row.findViewById(R.id.cat_bar);
        bar.post(() -> {
            int maxWidth = ((View) bar.getParent()).getWidth();
            bar.getLayoutParams().width = maxWidth * pct / 100;
            bar.setBackgroundColor(dummy.getCategoryColor());
            bar.requestLayout();
        });
        catContainer.addView(row);
    }

    private long getFromTime() {
        Calendar cal = Calendar.getInstance();
        if ("week".equals(currentFilter)) {
            cal.add(Calendar.DAY_OF_YEAR, -7);
            return cal.getTimeInMillis();
        } else if ("month".equals(currentFilter)) {
            cal.set(Calendar.DAY_OF_MONTH, 1);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            return cal.getTimeInMillis();
        }
        return 0;
    }

    // ── Parse Page ─────────────────────────────────────────
    private void setupParsePage() {
        // Sample buttons
        String[] samples = {
            "Pembayaran GoPay ke McDonald Sudirman berhasil. Nominal Rp 45.000 pada 22/05/2026.",
            "OVO berhasil digunakan. Pembayaran Grab Bike Rp 18.500. Tanggal: 21/05/2026.",
            "Transaksi DANA sukses. Belanja di Indomaret Rp 127.000 pada 20/05/2026.",
            "BCA Mobile: Debit Rp 35.000 ke Starbucks pada 19/05/2026."
        };
        int[] sampleBtnIds = { R.id.sample1, R.id.sample2, R.id.sample3, R.id.sample4 };
        for (int i = 0; i < sampleBtnIds.length; i++) {
            final String s = samples[i];
            findViewById(sampleBtnIds[i]).setOnClickListener(v -> etNotifText.setText(s));
        }

        btnParse.setOnClickListener(v -> doParse());
        btnSave.setOnClickListener(v -> saveTransaction());
        cardResult.setVisibility(View.GONE);
    }

    private void doParse() {
        String text = etNotifText.getText().toString().trim();
        if (text.isEmpty()) { toast("Masukkan teks notifikasi dulu!"); return; }

        btnParse.setEnabled(false);
        btnParse.setText("🤖 Parsing...");
        cardResult.setVisibility(View.GONE);

        aiParser.parse(text, "Manual", new AiParser.ParseCallback() {
            @Override
            public void onSuccess(Transaction t) {
                currentParsed = t;
                mainHandler.post(() -> {
                    tvMerchant.setText(t.merchant);
                    tvAmount.setText(formatRp(t.amount));
                    tvCategory.setText(t.getCategoryIcon() + " " + t.getCategoryLabel());
                    tvMethod.setText(t.paymentMethod);
                    tvDate.setText(t.date);
                    tvRaw.setText(t.rawText.length() > 80 ? t.rawText.substring(0, 80) + "..." : t.rawText);
                    cardResult.setVisibility(View.VISIBLE);
                    btnSave.setText("💾 Simpan Transaksi");
                    btnSave.setEnabled(true);
                    btnParse.setEnabled(true);
                    btnParse.setText("🤖 Parse dengan AI");
                });
            }
            @Override
            public void onError(String msg) {
                mainHandler.post(() -> {
                    toast("Gagal parse: " + msg);
                    btnParse.setEnabled(true);
                    btnParse.setText("🤖 Parse dengan AI");
                });
            }
        });
    }

    private void saveTransaction() {
        if (currentParsed == null) return;
        executor.execute(() -> {
            db.transactionDao().insert(currentParsed);
            mainHandler.post(() -> {
                btnSave.setText("✅ Tersimpan!");
                btnSave.setEnabled(false);
                toast("Transaksi disimpan! 🎉");
                refreshDashboard();
            });
        });
    }

    // ── History Page ───────────────────────────────────────
    private void setupHistoryPage() {
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) { refreshHistory(s.toString()); }
            public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private void refreshHistory(String query) {
        executor.execute(() -> {
            List<Transaction> list = query.isEmpty() ?
                db.transactionDao().getAll() :
                db.transactionDao().search(query);
            mainHandler.post(() -> rvHistory.setAdapter(new TransactionAdapter(list, this::showTxnDetail)));
        });
    }

    // ── Transaction Detail ─────────────────────────────────
    private void showTxnDetail(Transaction t) {
        new AlertDialog.Builder(this)
            .setTitle(t.getCategoryIcon() + " " + t.merchant)
            .setMessage(
                "Jumlah: " + t.getFormattedAmount() + "\n" +
                "Kategori: " + t.getCategoryLabel() + "\n" +
                "Metode: " + t.paymentMethod + "\n" +
                "Tanggal: " + t.date + "\n" +
                "Sumber: " + t.sourceApp + "\n\n" +
                "Teks asli:\n" + t.rawText
            )
            .setPositiveButton("Tutup", null)
            .setNegativeButton("Hapus", (d, w) -> {
                executor.execute(() -> {
                    db.transactionDao().delete(t.id);
                    mainHandler.post(() -> { refreshDashboard(); refreshHistory(""); });
                });
            })
            .show();
    }

    // ── Permission & Settings ──────────────────────────────
    private void checkNotificationPermission() {
        if (!isNotificationListenerEnabled()) {
            new AlertDialog.Builder(this)
                .setTitle("Izin Diperlukan")
                .setMessage("AutoSpend AI perlu izin untuk membaca notifikasi supaya bisa mencatat transaksi secara otomatis.")
                .setPositiveButton("Beri Izin", (d, w) ->
                    startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
                .setNegativeButton("Nanti", null)
                .setCancelable(false)
                .show();
        }
    }

    private boolean isNotificationListenerEnabled() {
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(getPackageName());
    }

    private void showSettingsDialog() {
        SharedPreferences prefs = getSharedPreferences("autospend_prefs", MODE_PRIVATE);
        String currentKey = prefs.getString("api_key", "");

        View v = getLayoutInflater().inflate(R.layout.dialog_settings, null);
        EditText etKey = v.findViewById(R.id.et_api_key);
        etKey.setText(currentKey);

        new AlertDialog.Builder(this)
            .setTitle("⚙️ Pengaturan")
            .setView(v)
            .setPositiveButton("Simpan", (d, w) -> {
                String key = etKey.getText().toString().trim();
                prefs.edit().putString("api_key", key).apply();
                toast(key.isEmpty() ? "Mode regex (tanpa AI)" : "API key disimpan ✅");
            })
            .setNegativeButton("Batal", null)
            .show();
    }

    // ── Helpers ────────────────────────────────────────────
    private String formatRp(double amount) {
        return "Rp " + String.format(Locale.US, "%,.0f", amount).replace(",", ".");
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(txnReceiver);
        executor.shutdown();
    }
}
