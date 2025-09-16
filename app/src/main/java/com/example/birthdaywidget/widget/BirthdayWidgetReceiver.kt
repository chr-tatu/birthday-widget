package com.example.birthdaywidget.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.example.birthdaywidget.work.BirthdaySyncWorker

class BirthdayWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = BirthdayWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        BirthdaySyncWorker.enqueueImmediate(context)
    }

    override fun onUpdate(context: Context, glanceIds: List<GlanceId>) {
        super.onUpdate(context, glanceIds)
        BirthdaySyncWorker.enqueueImmediate(context)
    }
}
