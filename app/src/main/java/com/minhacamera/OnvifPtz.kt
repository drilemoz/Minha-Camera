package com.minhacamera

import java.io.StringReader
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

/** Explicit LAN pan/tilt. Detection sends read-only commands; only a tap may move the camera. */
internal object OnvifPtz {
    private const val DEVICE = "http://www.onvif.org/ver10/device/wsdl"
    private const val MEDIA = "http://www.onvif.org/ver10/media/wsdl"
    private const val PTZ = "http://www.onvif.org/ver20/ptz/wsdl"
    private const val SCHEMA = "http://www.onvif.org/ver10/schema"
    private const val DEVICE_PATH = "/onvif/device_service"
    private const val IO_PATH = "/onvif/deviceio_service"
    private const val PULSE_MS = 200L

    data class Reply(val code: Int, val xml: String)
    internal fun interface Transport {
        fun send(url: String, namespace: String, action: String, body: String): Reply
    }

    enum class Direction(val x: String, val y: String) {
        LEFT("-0.2", "0"), RIGHT("0.2", "0"), UP("0", "0.2"), DOWN("0", "-0.2")
    }

    data class PulseResult(val moveAcknowledged: Boolean, val stopAcknowledged: Boolean)

    class Session internal constructor(private val url: String, private val token: String, private val transport: Transport) {
        fun pulse(direction: Direction): PulseResult {
            var moved = false
            var stopped = false
            try {
                moved = invoke(url, PTZ, "ContinuousMove", moveBody(token, direction), transport) != null
                if (moved) Thread.sleep(PULSE_MS)
            } catch (_: InterruptedException) {
                // A move sent before cancellation may already have reached the camera.
            } finally {
                stopped = stop()
            }
            return PulseResult(moved, stopped)
        }

        fun stop(): Boolean = invoke(url, PTZ, "Stop",
            "<ProfileToken>${xml(token)}</ProfileToken><PanTilt>true</PanTilt>", transport) != null
    }

    data class Connection(val session: Session?, val failure: Failure? = null)
    enum class Failure { INPUT, AUTH, PROFILES, PTZ }

    fun isLanIPv4(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4 || parts.any { it.length !in 1..3 || it.any { c -> c !in '0'..'9' } ||
            (it.length > 1 && it.startsWith('0')) }) return false
        val n = parts.map { it.toInt() }
        if (n.any { it !in 0..255 }) return false
        return n[0] == 10 || (n[0] == 172 && n[1] in 16..31) ||
            (n[0] == 192 && n[1] == 168) || (n[0] == 169 && n[1] == 254)
    }

    fun connect(host: String, port: Int, user: String, password: String, streamPath: String,
        deviceIoMode: Boolean): Connection {
        if (!isLanIPv4(host) || port !in 1..65535 || user.isBlank()) return Connection(null, Failure.INPUT)
        val base = "http://$host:$port"
        val offset = OnvifClient.cameraTimeOffset(base + DEVICE_PATH, timeoutMs = 1500)
        val wire = Transport { url, ns, action, body ->
            val r = OnvifClient.soap(url, user, password, offset, body, "$ns/$action", timeoutMs = 1700)
            Reply(r.code, r.body)
        }
        return discover(base, streamPath, deviceIoMode, wire)
    }

    internal fun discover(base: String, streamPath: String, deviceIoMode: Boolean, transport: Transport): Connection {
        val device = base + DEVICE_PATH
        val capsReply = request(device, DEVICE, "GetCapabilities", "<Category>All</Category>", transport)
        if (capsReply.code in listOf(401, 403) || capsReply.xml.contains("NotAuthorized", true))
            return Connection(null, Failure.AUTH)
        val caps = response(capsReply, "GetCapabilities")
        val mediaPath = capabilityPath(caps, "Media")
        val ptzPath = capabilityPath(caps, "PTZ")
        val profiles = listOfNotNull(mediaPath, "/onvif/media", "/onvif/media_service", DEVICE_PATH).distinct()
            .firstNotNullOfOrNull { path ->
                if (Thread.currentThread().isInterrupted) return Connection(null, Failure.PROFILES)
                val url = base + path
                val current = request(url, MEDIA, "GetProfiles", "", transport)
                if (current.code in listOf(401, 403) || current.xml.contains("NotAuthorized", true))
                    return Connection(null, Failure.AUTH)
                val all = response(current, "GetProfiles")?.children("Profiles").orEmpty()
                    .asSequence().filter { it.children("PTZConfiguration").isNotEmpty() }
                    .map { it.getAttribute("token") }.filter { it.isNotBlank() && it.length <= 128 }
                    .distinct().take(8).toList()
                all.takeIf { it.isNotEmpty() }?.let { url to it }
            } ?: return Connection(null, Failure.PROFILES)
        val token = profiles.second.singleOrNull() ?: profiles.second.firstOrNull {
            if (Thread.currentThread().isInterrupted) return Connection(null, Failure.PROFILES)
            val media = invoke(profiles.first, MEDIA, "GetStreamUri",
                "<StreamSetup><tt:Stream>RTP-Unicast</tt:Stream><tt:Transport><tt:Protocol>RTSP</tt:Protocol>" +
                    "</tt:Transport></StreamSetup><ProfileToken>${xml(it)}</ProfileToken>", transport)
            val uri = media?.descendant("Uri")?.textContent.orEmpty()
            runCatching { URI(uri).let { u ->
                val path = u.rawPath.orEmpty() + (u.rawQuery?.let { query -> "?$query" } ?: "")
                path.isNotEmpty() && path == streamPath
            } }.getOrDefault(false)
        } ?: return Connection(null, Failure.PROFILES)
        if (Thread.currentThread().isInterrupted) return Connection(null, Failure.PTZ)
        val info = if (!deviceIoMode && base.endsWith(":5000"))
            invoke(device, DEVICE, "GetDeviceInformation", "", transport) else null
        val knownFirmware = info?.descendant("Manufacturer")?.textContent?.trim().equals("Technology", true) &&
            info?.descendant("Model")?.textContent?.trim() == "IPC" &&
            info?.descendant("FirmwareVersion")?.textContent?.trim() == "46.00.53"
        val route = when {
            deviceIoMode || knownFirmware -> IO_PATH
            ptzPath != null -> ptzPath
            invoke(base + "/onvif/PTZ", PTZ, "GetConfigurations", "", transport) != null -> "/onvif/PTZ"
            else -> return Connection(null, Failure.PTZ)
        }
        return Connection(Session(base + route, token, transport))
    }

    private fun moveBody(token: String, direction: Direction): String =
        "<ProfileToken>${xml(token)}</ProfileToken><Velocity>" +
            "<tt:PanTilt x=\"${direction.x}\" y=\"${direction.y}\"/></Velocity><Timeout>PT0H0M0.200S</Timeout>"

    private fun request(url: String, ns: String, action: String, inner: String, transport: Transport): Reply =
        transport.send(url, ns, action, "<$action xmlns=\"$ns\" xmlns:tt=\"$SCHEMA\">$inner</$action>")

    private fun invoke(url: String, ns: String, action: String, inner: String, transport: Transport): Element? =
        response(request(url, ns, action, inner, transport), action)

    private fun response(reply: Reply, action: String): Element? {
        if (reply.code !in 200..299 || reply.xml.isBlank() || reply.xml.length > 262144 ||
            reply.xml.contains("<!DOCTYPE", true) || reply.xml.contains("<!ENTITY", true)) return null
        return runCatching {
            val doc = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                isExpandEntityReferences = false
            }.newDocumentBuilder().parse(InputSource(StringReader(reply.xml)))
            if (doc.getElementsByTagNameNS("*", "Fault").length > 0) null
            else doc.getElementsByTagNameNS("*", "${action}Response").item(0) as? Element
        }.getOrNull()
    }

    private fun capabilityPath(caps: Element?, capability: String): String? {
        val addr = caps?.descendant(capability)?.descendant("XAddr")?.textContent?.trim().orEmpty()
        // Keep only the service path. Some cameras advertise a different public host
        // or port. Authentication and movement always stay on the selected LAN address.
        val path = runCatching { URI(addr).rawPath }.getOrNull() ?: return null
        return path.takeIf { it.matches(Regex("/[a-zA-Z0-9_./-]{1,120}")) && ".." !in it && !it.startsWith("//") }
    }

    private fun Element.descendant(name: String): Element? =
        getElementsByTagNameNS("*", name).item(0) as? Element

    private fun Element.children(name: String): List<Element> =
        (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }.filter { it.localName == name }

    private fun xml(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
