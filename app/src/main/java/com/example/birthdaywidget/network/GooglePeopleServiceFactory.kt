package com.example.birthdaywidget.network

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.people.v1.PeopleService

class GooglePeopleServiceFactory {
    fun create(context: Context, credential: GoogleAccountCredential): PeopleService {
        val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
        return PeopleService.Builder(httpTransport, GsonFactory.getDefaultInstance(), credential)
            .setApplicationName(context.packageName)
            .build()
    }
}
