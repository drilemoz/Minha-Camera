package com.minhacamera

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ClipDescription
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText

object CameraFormUi {
    const val DEFAULT_PATH = "Default"
    private data class Preset(val family: String, val stream: Int, val path: String)
    private val pathSuggestions = listOf(
        Preset("Stream", 1, "/stream1"),
        Preset("Stream", 2, "/stream2"),
        Preset("ONVIF", 1, "/onvif1"),
        Preset("ONVIF", 2, "/onvif2"),
        Preset("Live", 1, "/live/ch00_0"),
        Preset("Live", 2, "/live/ch00_1"),
        Preset("Streaming Channels", 1, "/Streaming/Channels/101"),
        Preset("Streaming Channels", 2, "/Streaming/Channels/102"),
        Preset("H264 Preview", 1, "/h264Preview_01_main"),
        Preset("H264 Preview", 2, "/h264Preview_01_sub"),
        Preset("Realmonitor", 1, "/cam/realmonitor?channel=1&subtype=0"),
        Preset("Realmonitor", 2, "/cam/realmonitor?channel=1&subtype=1")
    )

    data class ParsedRtsp(
        val host: String,
        val port: Int,
        val user: String,
        val pass: String,
        val path: String
    )

    fun showCameraForm(dialog: AlertDialog) {
        dialog.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        dialog.show()
    }

    /** A URL completa já define a porta e o caminho. Mantém os campos de login disponíveis. */
    fun adaptToAddress(form: View, input: TextInputEditText) {
        fun update(value: CharSequence?) {
            val visibility = if (CameraStreamProtocol.isSupported(value?.toString()?.trim().orEmpty()))
                View.GONE else View.VISIBLE
            form.findViewById<View>(R.id.cameraPortBox).visibility = visibility
            form.findViewById<View>(R.id.rtspPathCard).visibility = visibility
        }
        input.doAfterTextChanged { update(it) }
        update(input.text)
    }

    fun setupPathSelector(
        form: View,
        initialValue: String = DEFAULT_PATH,
        editing: Boolean = false
    ): () -> String {
        val context = form.context
        val summary = form.findViewById<TextView>(R.id.rtspPathSummary)
        val detail = form.findViewById<TextView>(R.id.rtspPathDetail)
        val customBox = form.findViewById<View>(R.id.rtspCustomPathBox)
        val customField = form.findViewById<TextInputEditText>(R.id.rtspCustomPath)
        val choose = form.findViewById<MaterialButton>(R.id.btnRtspPath)
        var selected = normalizePathChoice(initialValue)
        var manual = false

        fun render() {
            customBox.visibility = if (manual) View.VISIBLE else View.GONE
            val preset = pathSuggestions.firstOrNull { it.path == selected }
            summary.text = when {
                manual -> context.getText(R.string.rtsp_form_custom_title)
                selected.isEmpty() -> context.getText(
                    if (editing) R.string.rtsp_form_no_path else R.string.rtsp_form_pick_auto
                )
                preset != null -> context.getString(R.string.rtsp_form_preset_label, preset.family, preset.stream)
                else -> selected.substringBefore('?') + if ('?' in selected) "?…" else ""
            }
            detail.text = when {
                manual -> context.getText(R.string.rtsp_form_custom_detail)
                selected.isEmpty() -> context.getText(
                    if (editing) R.string.rtsp_form_auto_edit_detail else R.string.rtsp_form_auto_add_detail
                )
                preset != null -> preset.path
                else -> context.getText(R.string.rtsp_form_exact_detail)
            }
        }

        fun select(path: String) {
            selected = path
            manual = false
            render()
        }

        choose.setOnClickListener {
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(choose.windowToken, 0)
            customField.clearFocus()
            val items = arrayOf(
                context.getString(if (editing) R.string.rtsp_form_no_path else R.string.rtsp_form_pick_auto),
                context.getString(R.string.rtsp_form_suggestions),
                context.getString(R.string.rtsp_form_custom_title)
            )
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.rtsp_form_choose)
                .setItems(items) { _, which ->
                    when (which) {
                        0 -> select("")
                        1 -> {
                            val labels = pathSuggestions.map {
                                context.getString(R.string.rtsp_form_preset_row, it.family, it.stream, it.path)
                            }.toTypedArray()
                            MaterialAlertDialogBuilder(context)
                                .setTitle(R.string.rtsp_form_suggestions)
                                .setItems(labels) { _, index -> select(pathSuggestions[index].path) }
                                .setNegativeButton(R.string.cancelar, null)
                                .show()
                        }
                        2 -> {
                            if (!manual) customField.setText(selected)
                            manual = true
                            render()
                        }
                    }
                }
                .setNegativeButton(R.string.cancelar, null)
                .show()
        }
        render()
        return { if (manual) normalizePathChoice(customField.text?.toString().orEmpty()) else selected }
    }

    fun setupPasswordTools(
        context: Context,
        passwordView: TextInputEditText,
        btnCopy: MaterialButton,
        btnPaste: MaterialButton
    ) {
        btnCopy.setOnClickListener {
            val text = passwordView.text?.toString().orEmpty()
            if (text.isBlank()) {
                Toast.makeText(context, R.string.senha_vazia, Toast.LENGTH_SHORT).show()
            } else {
                copyText(context, text, context.getString(R.string.senha), true)
                Toast.makeText(context, R.string.senha_copiada, Toast.LENGTH_SHORT).show()
            }
        }
        btnPaste.setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val pasted = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
            if (pasted.isBlank()) {
                Toast.makeText(context, R.string.area_transferencia_vazia, Toast.LENGTH_SHORT).show()
            } else {
                passwordView.setText(pasted)
                passwordView.setSelection(passwordView.text?.length ?: 0)
                Toast.makeText(context, R.string.senha_colada, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun normalizePathChoice(raw: String): String {
        val value = raw.trim()
        if (value.isBlank() || value.equals(DEFAULT_PATH, ignoreCase = true)) return ""
        return if (value.startsWith("/")) value else "/$value"
    }

    fun buildRtspUrl(host: String, port: Int, user: String, pass: String, pathChoice: String): String {
        val auth = if (user.isBlank() && pass.isBlank()) "" else "${android.net.Uri.encode(user)}:${android.net.Uri.encode(pass)}@"
        val path = normalizePathChoice(pathChoice)
        return "rtsp://$auth$host:$port$path"
    }

    /** Aplica credenciais da tela a uma URL RTSP sem autenticação embutida. */
    fun attachRtspCredentials(url: String, user: String, password: String): String {
        if (!CameraStreamProtocol.isRtsp(url) || (user.isBlank() && password.isBlank())) return url
        return try {
            val parsed = android.net.Uri.parse(url)
            if (!parsed.encodedUserInfo.isNullOrBlank()) return url
            val authority = parsed.encodedAuthority ?: return url
            val auth = "${android.net.Uri.encode(user)}:${android.net.Uri.encode(password)}@"
            parsed.buildUpon().encodedAuthority(auth + authority).build().toString()
        } catch (_: Exception) { url }
    }

    fun parseRtsp(url: String): ParsedRtsp {
        return try {
            val uri = android.net.Uri.parse(url)
            val encodedUserInfo = uri.encodedUserInfo.orEmpty()
            val encodedUser = encodedUserInfo.substringBefore(':', "")
            val encodedPass = encodedUserInfo.substringAfter(':', "")
            val path = (uri.encodedPath ?: "") + (uri.encodedQuery?.let { "?$it" } ?: "")
            ParsedRtsp(
                host = uri.host.orEmpty(),
                port = if (uri.port != -1) uri.port else 554,
                user = android.net.Uri.decode(encodedUser),
                pass = android.net.Uri.decode(encodedPass),
                path = path.ifBlank { DEFAULT_PATH }
            )
        } catch (_: Exception) {
            ParsedRtsp("", 554, "admin", "", DEFAULT_PATH)
        }
    }

    fun copyText(context: Context, text: String, label: String, sensitive: Boolean = false) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        if (sensitive && Build.VERSION.SDK_INT >= 24) {
            clip.description.extras = android.os.PersistableBundle().apply {
                putBoolean(if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE
                    else "android.content.extra.IS_SENSITIVE", true)
            }
        }
        clipboard.setPrimaryClip(clip)
    }
}
