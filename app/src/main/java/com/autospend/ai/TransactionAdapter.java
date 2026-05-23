package com.autospend.ai;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.VH> {

    public interface OnClick { void onClick(Transaction t); }

    private final List<Transaction> list;
    private final OnClick listener;

    public TransactionAdapter(List<Transaction> list, OnClick listener) {
        this.list = list;
        this.listener = listener;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Transaction t = list.get(pos);
        h.tvIcon.setText(t.getCategoryIcon());
        h.tvMerchant.setText(t.merchant != null ? t.merchant : "Unknown");
        h.tvMethod.setText(t.paymentMethod != null ? t.paymentMethod : "-");
        h.tvDate.setText(t.date != null ? t.date : "");
        h.tvAmount.setText(t.getFormattedAmount());
        h.tvCat.setText(t.getCategoryLabel());

        // Color the icon background
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(24);
        int color = t.getCategoryColor();
        bg.setColor(Color.argb(30, Color.red(color), Color.green(color), Color.blue(color)));
        h.tvIcon.setBackground(bg);

        h.itemView.setOnClickListener(v -> listener.onClick(t));
    }

    @Override public int getItemCount() { return list.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvIcon, tvMerchant, tvMethod, tvDate, tvAmount, tvCat;
        VH(View v) {
            super(v);
            tvIcon     = v.findViewById(R.id.tv_txn_icon);
            tvMerchant = v.findViewById(R.id.tv_txn_merchant);
            tvMethod   = v.findViewById(R.id.tv_txn_method);
            tvDate     = v.findViewById(R.id.tv_txn_date);
            tvAmount   = v.findViewById(R.id.tv_txn_amount);
            tvCat      = v.findViewById(R.id.tv_txn_cat);
        }
    }
}
