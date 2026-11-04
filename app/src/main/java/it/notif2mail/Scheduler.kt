package it.notif2mail

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object Scheduler {

    fun uniqueName(ruleId: String, suffix: String = "") = "flush_$ruleId$suffix"

    /**
     * Pianifica l'invio delle notifiche in attesa di una regola.
     * Senza riepilogo parte subito (appena c'è rete); con riepilogo parte
     * dopo N minuti dalla prima notifica raccolta.
     */
    fun schedule(ctx: Context, rule: Rule, suffix: String = "") {
        val wm = WorkManager.getInstance(ctx.applicationContext)
        val builder = OneTimeWorkRequestBuilder<FlushWorker>()
            .setInputData(workDataOf(FlushWorker.KEY_RULE to rule.id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)

        if (rule.digestMinutes > 0) {
            builder.setInitialDelay(rule.digestMinutes.toLong(), TimeUnit.MINUTES)
            wm.enqueueUniqueWork(uniqueName(rule.id, suffix), ExistingWorkPolicy.KEEP, builder.build())
        } else {
            wm.enqueueUniqueWork(
                uniqueName(rule.id, suffix),
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                builder.build()
            )
        }
    }
}
