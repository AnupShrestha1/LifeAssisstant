package com.anujstha.lifeassistant.data.export

import android.content.ContentResolver
import android.net.Uri
import com.anujstha.lifeassistant.data.repository.ExpenseCategoryRepository
import com.anujstha.lifeassistant.data.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Writes all transactions as CSV to a document the user picked with the system file picker. */
class TransactionExporter(
    private val transactions: TransactionRepository,
    private val categories: ExpenseCategoryRepository,
    private val contentResolver: ContentResolver,
) {
    /** Returns the number of transactions written. */
    suspend fun export(uri: Uri): Int = withContext(Dispatchers.IO) {
        val all = transactions.observeFiltered().first()
        val cats = categories.observeAll().first()
        val stream = contentResolver.openOutputStream(uri, "wt") ?: error("Cannot open the chosen file")
        stream.bufferedWriter(Charsets.UTF_8).use { TransactionCsv.write(it, all, cats) }
        all.size
    }
}
