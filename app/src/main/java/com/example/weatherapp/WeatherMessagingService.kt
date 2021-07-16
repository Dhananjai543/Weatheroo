package com.example.weatherapp

import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class WeatherMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        showNotification(remoteMessage.notification!!.title, remoteMessage.notification!!.body)
    }

    fun showNotification(title: String?, message: String?){
        val builder : NotificationCompat.Builder = NotificationCompat.Builder(this,"MyNotifications")
        builder.run {
            setContentTitle(title)
            setSmallIcon(R.drawable.ic_launcher_background)
            setAutoCancel(true)
            setContentText(message)
        }

        val manager: NotificationManagerCompat = NotificationManagerCompat.from(this)
        manager.notify(999,builder.build())
    }
}