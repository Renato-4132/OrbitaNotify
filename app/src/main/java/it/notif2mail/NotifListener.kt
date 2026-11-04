package it.notif2mail

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.Calendar

class NotifListener : NotificationListenerService() {

    private val cfg by lazy { Config(this) }

    // Evita di rimandare la stessa notifica quando l'app la aggiorna senza cambiare testo
    private val lastSeen = LinkedHashMap<String, Int>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            handle(sbn)
        } catch (e: Exception) {
            // Un errore su una notifica non deve mai fermare il servizio
        }
    }

    private fun handle(sbn: StatusBarNotification) {
        if (!cfg.enabled || !cfg.anyChannelEnabled()) return

        val pkg = sbn.packageName
        if (pkg == packageName) return

        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (cfg.ignoreOngoing && n.flags and Notification.FLAG_ONGOING_EVENT != 0) return

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val rule = RuleMatcher.firstMatch(cfg.rules, pkg, title, text, Calendar.getInstance()) ?: return

        // Anti-duplicati
        val hash = (title + "\u0000" + text).hashCode()
        if (lastSeen[sbn.key] == hash) return
        lastSeen[sbn.key] = hash
        if (lastSeen.size > 200) lastSeen.remove(lastSeen.keys.first())

        Db.get(this).addPending(
            rule.id, sbn.postTime, appLabel(pkg), pkg,
            title.take(2000), text.take(6000)
        )
        Scheduler.schedule(this, rule)
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }
}
