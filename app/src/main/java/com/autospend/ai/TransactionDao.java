package com.autospend.ai;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface TransactionDao {

    @Insert
    long insert(Transaction transaction);

    @Query("SELECT * FROM transactions ORDER BY createdAt DESC")
    List<Transaction> getAll();

    @Query("SELECT * FROM transactions WHERE createdAt >= :from ORDER BY createdAt DESC")
    List<Transaction> getFrom(long from);

    @Query("SELECT SUM(amount) FROM transactions WHERE createdAt >= :from")
    double sumFrom(long from);

    @Query("SELECT COUNT(*) FROM transactions WHERE createdAt >= :from")
    int countFrom(long from);

    @Query("SELECT * FROM transactions ORDER BY createdAt DESC LIMIT 5")
    List<Transaction> getRecent();

    @Query("SELECT * FROM transactions WHERE merchant LIKE '%' || :q || '%' OR category LIKE '%' || :q || '%' OR paymentMethod LIKE '%' || :q || '%' ORDER BY createdAt DESC")
    List<Transaction> search(String q);

    @Query("DELETE FROM transactions WHERE id = :id")
    void delete(int id);

    @Query("DELETE FROM transactions")
    void deleteAll();
}
