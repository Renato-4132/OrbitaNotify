package it.notif2mail

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * Invia le notifiche in attesa di una regola, una mail ciascuna oppure
 * un unico riepilogo. Se la rete manca riprova da sola (fino a 6 volte).
 */
class FlushWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        val ruleId = inputData.getString(KEY_RULE) ?: return Result.failure()
        val cfg = Config(applicationContext)
        val db = Db.get(applicationContext)

        val items = db.pending(ruleId)
        if (items.isEmpty()) return Result.success()

        val rule = cfg.rules.firstOrNull { it.id == ruleId }
        if (rule == null || !rule.enabled) {
            // Regola eliminata o disattivata nel frattempo: niente da inviare
            db.deletePending(items.map { it.id })
            return Result.success()
        }

        val lastAttempt = runAttemptCount >= MAX_ATTEMPTS - 1
        val groups: List<List<Db.Pending>> =
            if (rule.digestMinutes > 0) listOf(items) else items.map { listOf(it) }

        for ((index, group) in groups.withIndex()) {
            val msg = buildMessage(cfg, rule, group)
            val results = Dispatcher.send(cfg, msg, rule.recipients)

            if (results.any { it.ok }) {
                record(db, rule, msg, results, group)
                continue
            }

            val retryable = results.isNotEmpty() && results.all { it.retryable }
            if (retryable && !lastAttempt) return Result.retry()

            // Fallimento definitivo: lo registro per questo gruppo e per i successivi
            for (g in groups.drop(index)) {
                record(db, rule, buildMessage(cfg, rule, g), results, g)
            }
            return Result.failure()
        }

        // Notifiche arrivate mentre stavo inviando
        if (db.pending(ruleId).isNotEmpty()) {
            Scheduler.schedule(applicationContext, rule, "_next")
        }
        return Result.success()
    }

    private fun buildMessage(cfg: Config, rule: Rule, group: List<Db.Pending>): Dispatcher.Message {
        val base = if (rule.digestMinutes > 0) Formatter.digest(cfg, group, rule.appLabel)
        else Formatter.single(cfg, group[0], rule.appLabel)
        return base.copy(conto = rule.conto.trim(), metodo = rule.metodo.trim(), auto = rule.auto, tags = rule.tags())
    }

    private fun record(
        db: Db,
        rule: Rule,
        msg: Dispatcher.Message,
        results: List<Dispatcher.ChannelResult>,
        group: List<Db.Pending>
    ) {
        val ok = results.isNotEmpty() && results.filter { !it.soft }.all { it.ok }
        val detail = if (results.isEmpty()) {
            "Nessun canale pronto: controlla server, utente, password e destinatari"
        } else {
            results.joinToString("; ") { "${it.channel}: ${if (it.ok) "OK" else it.error}" }
        }
        db.addHistory(System.currentTimeMillis(), rule.appLabel, msg.subject, ok, detail)
        db.deletePending(group.map { it.id })
    }

    companion object {
        const val KEY_RULE = "ruleId"
        const val MAX_ATTEMPTS = 6
    }
}
