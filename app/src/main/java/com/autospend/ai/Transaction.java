package com.autospend.ai;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "transactions")
public class Transaction {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public double amount;
    public String merchant;
    public String category;
    public String paymentMethod;
    public String date;
    public String rawText;
    public String sourceApp;
    public long createdAt;

    public Transaction() {
        this.createdAt = System.currentTimeMillis();
    }

    public String getFormattedAmount() {
        return "Rp " + String.format("%,.0f", amount).replace(",", ".");
    }

    public String getCategoryIcon() {
        if (category == null) return "💡";
        switch (category) {
            case "food":          return "🍔";
            case "transport":     return "🚗";
            case "shopping":      return "🛒";
            case "health":        return "💊";
            case "entertainment": return "🎮";
            case "bills":         return "📋";
            default:              return "💡";
        }
    }

    public String getCategoryLabel() {
        if (category == null) return "Lainnya";
        switch (category) {
            case "food":          return "Makanan & Minuman";
            case "transport":     return "Transportasi";
            case "shopping":      return "Belanja";
            case "health":        return "Kesehatan";
            case "entertainment": return "Hiburan";
            case "bills":         return "Tagihan";
            default:              return "Lainnya";
        }
    }

    public int getCategoryColor() {
        if (category == null) return 0xFF8891AA;
        switch (category) {
            case "food":          return 0xFFF97316;
            case "transport":     return 0xFF22D3EE;
            case "shopping":      return 0xFFA78BFA;
            case "health":        return 0xFF4ADE80;
            case "entertainment": return 0xFFF472B6;
            case "bills":         return 0xFFFBBF24;
            default:              return 0xFF8891AA;
        }
    }
}
