package com.example.birthdaywidget.network

import android.content.Context
import com.example.birthdaywidget.data.UpcomingBirthday
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.people.v1.PeopleServiceScopes
import com.google.api.services.people.v1.model.Date
import com.google.api.services.people.v1.model.Person
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class PeopleBirthdayRepository(
    private val context: Context,
    private val account: GoogleSignInAccount,
    private val serviceFactory: GooglePeopleServiceFactory = GooglePeopleServiceFactory(),
    private val photoCache: ContactPhotoCache = ContactPhotoCache(context)
) {

    suspend fun loadUpcomingBirthdays(windowDays: Long): List<UpcomingBirthday> = withContext(Dispatchers.IO) {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(PeopleServiceScopes.CONTACTS_READONLY)
        )
        credential.selectedAccount = account.account
        val service = serviceFactory.create(context, credential)
        val today = LocalDate.now()
        val upperBound = today.plusDays(windowDays)
        val results = mutableListOf<UpcomingBirthday>()
        var nextPageToken: String? = null
        val accessToken = runCatching { credential.token }.getOrNull()
        val usedPhotoKeys = mutableSetOf<String>()
        do {
            val request = service.people().connections().list("people/me")
                .setPageSize(400)
                .setPersonFields("names,birthdays,photos")
            if (!nextPageToken.isNullOrEmpty()) {
                request.pageToken = nextPageToken
            }
            val response = request.execute()
            response.connections?.forEach { person ->
                val upcomingDate = nextOccurrence(person, today) ?: return@forEach
                if (!upcomingDate.isAfter(upperBound)) {
                    val resourceName = person.resourceName ?: return@forEach
                    val displayName = person.names?.firstOrNull { it.metadata?.primary == true }?.displayName
                        ?: person.names?.firstOrNull()?.displayName
                        ?: return@forEach
                    val photoPath = person.photos?.firstOrNull { it.metadata?.primary == true }?.url
                        ?: person.photos?.firstOrNull()?.url
                    val hashedKey = photoCache.keyFor(resourceName)
                    val localPhoto = if (photoPath != null) {
                        photoCache.cachePhoto(resourceName, photoPath, credential, accessToken)
                    } else {
                        null
                    }
                    if (localPhoto != null) {
                        usedPhotoKeys.add(hashedKey)
                    }
                    results.add(
                        UpcomingBirthday(
                            resourceName = resourceName,
                            displayName = displayName,
                            dateIso8601 = upcomingDate.format(DateTimeFormatter.ISO_DATE),
                            photoPath = localPhoto
                        )
                    )
                }
            }
            nextPageToken = response.nextPageToken
        } while (!nextPageToken.isNullOrEmpty())
        if (usedPhotoKeys.isNotEmpty()) {
            photoCache.trimTo(usedPhotoKeys)
        } else {
            photoCache.clear()
        }
        results.sortBy { it.dateIso8601 }
        results
    }

    private fun nextOccurrence(person: Person, today: LocalDate): LocalDate? {
        val birthdays = person.birthdays ?: return null
        val next = birthdays.mapNotNull { birthday ->
            birthday.date?.let { date ->
                computeNextDate(date, today)
            }
        }.minOrNull()
        return next
    }

    private fun computeNextDate(date: Date, today: LocalDate): LocalDate? {
        val month = date.month ?: return null
        val day = date.day ?: return null
        var year = today.year
        var candidate = createDate(year, month, day) ?: return null
        if (candidate.isBefore(today)) {
            candidate = createDate(year + 1, month, day) ?: return null
        }
        return candidate
    }

    private fun createDate(year: Int, month: Int, day: Int): LocalDate? {
        var attemptYear = year
        repeat(4) {
            val candidate = runCatching { LocalDate.of(attemptYear, month, day) }.getOrNull()
            if (candidate != null) {
                return candidate
            }
            attemptYear += 1
        }
        return null
    }

}
