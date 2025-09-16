package com.example.birthdaywidget.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.birthdaywidget.R
import com.example.birthdaywidget.data.BirthdayWidgetSnapshot
import com.example.birthdaywidget.data.BirthdayWidgetStateStore
import com.example.birthdaywidget.network.ContactPhotoCache
import com.example.birthdaywidget.network.PeopleBirthdayRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class BirthdaySyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account == null) {
            ContactPhotoCache(context).clear()
            val snapshot = BirthdayWidgetSnapshot(hasAccount = false)
            BirthdayWidgetStateStore.saveSnapshot(context, snapshot)
            return@withContext Result.success()
        }

        val repository = PeopleBirthdayRepository(context, account)
        return@withContext try {
            val birthdays = repository.loadUpcomingBirthdays(DAYS_RANGE)
            val snapshot = BirthdayWidgetSnapshot(
                hasAccount = true,
                entries = birthdays,
                errorMessage = null,
                lastSyncedEpochMillis = System.currentTimeMillis()
            )
            BirthdayWidgetStateStore.saveSnapshot(context, snapshot)
            Result.success()
        } catch (recoverable: UserRecoverableAuthIOException) {
            val message = context.getString(R.string.error_authorization_needed)
            val snapshot = BirthdayWidgetSnapshot(
                hasAccount = true,
                entries = emptyList(),
                errorMessage = message,
                lastSyncedEpochMillis = System.currentTimeMillis()
            )
            BirthdayWidgetStateStore.saveSnapshot(context, snapshot)
            Result.success()
        } catch (io: IOException) {
            val detail = io.localizedMessage?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.error_unknown)
            val message = context.getString(R.string.error_sync_failed, detail)
            val snapshot = BirthdayWidgetSnapshot(
                hasAccount = true,
                entries = emptyList(),
                errorMessage = message,
                lastSyncedEpochMillis = System.currentTimeMillis()
            )
            BirthdayWidgetStateStore.saveSnapshot(context, snapshot)
            Result.retry()
        } catch (exception: Exception) {
            val message = exception.localizedMessage?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.error_unknown)
            val snapshot = BirthdayWidgetSnapshot(
                hasAccount = true,
                entries = emptyList(),
                errorMessage = message,
                lastSyncedEpochMillis = System.currentTimeMillis()
            )
            BirthdayWidgetStateStore.saveSnapshot(context, snapshot)
            Result.failure()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "birthday-widget-sync"
        private const val DAYS_RANGE = 14L

        fun enqueueImmediate(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<BirthdaySyncWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
