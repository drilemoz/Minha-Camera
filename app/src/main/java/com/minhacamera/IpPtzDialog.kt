package com.minhacamera

import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.atomic.AtomicBoolean

/** The dialog never starts movement without a button press. No repeats or queued commands. */
internal object IpPtzDialog {
    fun show(activity: AppCompatActivity, host: String, user: String, password: String, path: String,
        busy: AtomicBoolean, isCurrent: () -> Boolean, report: (String, Boolean) -> Unit)
        : androidx.appcompat.app.AlertDialog {
        fun dp(n: Int) = (n * activity.resources.displayMetrics.density + .5f).toInt()
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(6), dp(22), dp(10))
        }
        val scroll = ScrollView(activity).apply { addView(column) }
        fun label(resId: Int) = TextView(activity).apply { setText(resId); setPadding(0, dp(4), 0, dp(4)) }
        fun button(text: CharSequence) = MaterialButton(activity, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            this.text = text
            isAllCaps = false
            minimumWidth = 0; minWidth = 0
        }
        val port = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setHint(R.string.player_ptz_port)
            setText("5000")
            setSelectAllOnFocus(true)
        }
        val compatibility = MaterialCheckBox(activity).apply { setText(R.string.player_ptz_compat) }
        val connect = button(activity.getText(R.string.player_ptz_connect))
        val status = label(R.string.player_ptz_initial).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        column.addView(label(R.string.player_ptz_port))
        column.addView(port, LinearLayout.LayoutParams(-1, -2))
        column.addView(compatibility)
        column.addView(connect, LinearLayout.LayoutParams(-1, -2))
        column.addView(status)
        val actionButtons = mutableListOf<MaterialButton>()
        var session: OnvifPtz.Session? = null
        var closed = false
        var localBusy = false
        var discovery: Thread? = null

        fun render() {
            val ready = session != null
            port.isEnabled = !ready && !localBusy
            compatibility.isEnabled = !ready && !localBusy
            connect.visibility = if (ready) View.GONE else View.VISIBLE
            connect.isEnabled = !localBusy
            actionButtons.forEach { it.isEnabled = ready && !localBusy }
        }
        fun work(onBusy: Int, scanning: Boolean = false, task: () -> (() -> Unit)) {
            if (closed || !isCurrent() || localBusy) return
            if (!busy.compareAndSet(false, true)) {
                status.setText(R.string.player_ptz_busy)
                return
            }
            localBusy = true
            status.setText(onBusy)
            render()
            val worker = Thread {
                val result = try { task() } catch (_: Exception) {
                    { status.setText(R.string.player_ptz_no_confirmation) }
                } finally { busy.set(false) }
                activity.runOnUiThread {
                    if (closed || !isCurrent()) return@runOnUiThread
                    localBusy = false
                    if (scanning) discovery = null
                    result()
                    render()
                }
            }
            if (scanning) discovery = worker
            worker.start()
        }
        fun row(vararg cells: Pair<String, OnvifPtz.Direction?>) {
            val line = LinearLayout(activity).apply { gravity = Gravity.CENTER }
            for ((glyph, direction) in cells) {
                val key = button(glyph)
                key.contentDescription = when (direction) {
                    OnvifPtz.Direction.UP -> activity.getString(R.string.player_ptz_up)
                    OnvifPtz.Direction.DOWN -> activity.getString(R.string.player_ptz_down)
                    OnvifPtz.Direction.LEFT -> activity.getString(R.string.player_ptz_left)
                    OnvifPtz.Direction.RIGHT -> activity.getString(R.string.player_ptz_right)
                    null -> activity.getString(R.string.player_ptz_stop)
                }
                key.setOnClickListener {
                    val target = session ?: return@setOnClickListener
                    work(R.string.player_ptz_sending) {
                        val outcome = if (direction == null) null else target.pulse(direction)
                        val stopped = outcome?.stopAcknowledged ?: target.stop()
                        val outcomeText = when {
                            direction == null && stopped -> R.string.player_ptz_stopped
                            outcome?.moveAcknowledged == true && stopped -> R.string.player_ptz_sent
                            outcome?.moveAcknowledged == true -> R.string.player_ptz_stop_unconfirmed
                            else -> R.string.player_ptz_no_confirmation
                        }
                        val action: () -> Unit = {
                            status.setText(outcomeText)
                            report("PTZ: " + activity.getString(outcomeText), !stopped ||
                                (direction != null && outcome?.moveAcknowledged != true))
                        }
                        action
                    }
                }
                actionButtons += key
                line.addView(key, LinearLayout.LayoutParams(if (direction == null) dp(88) else dp(60), dp(52)).apply {
                    marginStart = dp(3); marginEnd = dp(3)
                })
            }
            column.addView(line, LinearLayout.LayoutParams(-1, -2))
        }

        row("↑" to OnvifPtz.Direction.UP)
        row("←" to OnvifPtz.Direction.LEFT, activity.getString(R.string.player_ptz_stop) to null,
            "→" to OnvifPtz.Direction.RIGHT)
        row("↓" to OnvifPtz.Direction.DOWN)
        connect.setOnClickListener {
            val number = port.text.toString().trim().toIntOrNull()
            if (number == null || number !in 1..65535) {
                port.error = activity.getString(R.string.player_ptz_bad_port)
                return@setOnClickListener
            }
            val deviceIo = compatibility.isChecked
            work(R.string.player_ptz_checking, scanning = true) {
                val result = OnvifPtz.connect(host, number, user, password, path, deviceIo)
                val msg = when (result.failure) {
                    OnvifPtz.Failure.INPUT -> R.string.player_ptz_invalid_camera
                    OnvifPtz.Failure.AUTH -> R.string.player_ptz_auth_failed
                    OnvifPtz.Failure.PROFILES -> R.string.player_ptz_no_profile
                    OnvifPtz.Failure.PTZ -> R.string.player_ptz_no_service
                    null -> R.string.player_ptz_ready
                }
                val action: () -> Unit = {
                    session = result.session
                    status.setText(msg)
                    report("PTZ: " + activity.getString(msg), result.session == null)
                }
                action
            }
        }
        render()
        return MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.player_ptz_title)
            .setView(scroll)
            .setNegativeButton(R.string.player_ptz_close, null)
            .create().also { dialog ->
                dialog.setOnDismissListener {
                    closed = true
                    // Read-only discovery may be cancelled. A movement already sent still gets its one Stop.
                    discovery?.interrupt()
                    session = null
                }
                dialog.show()
            }
    }
}
