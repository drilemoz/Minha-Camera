package com.minhacamera

internal fun checkOnvifPtz() {
    val base = "http://192.0.2.10:5000"
    data class Call(val url: String, val action: String, val body: String)
    val received = mutableListOf<Call>()
    fun reply(action: String, xml: String = "") = OnvifPtz.Reply(200, "<${action}Response>$xml</${action}Response>")
    val fake = OnvifPtz.Transport { url, _, action, body ->
        received += Call(url, action, body)
        when (action) {
            "GetCapabilities" -> reply(action,
                "<Capabilities><Media><XAddr>http://203.0.113.17:32157/onvif/media</XAddr></Media>" +
                    "<PTZ><XAddr>http://203.0.113.17:32157/onvif/PTZ</XAddr></PTZ></Capabilities>")
            "GetProfiles" -> reply(action,
                "<Profiles token=\"test-main\"><PTZConfiguration/></Profiles>" +
                    "<Profiles token=\"test-sub\"><PTZConfiguration/></Profiles>" +
                    "<Profiles token=\"test-fixed\"><VideoEncoderConfiguration/></Profiles>")
            "GetStreamUri" -> {
                val path = if ("test-main" in body) "/onvif1" else "/onvif2"
                reply(action, "<MediaUri><Uri>rtsp://192.0.2.10:554$path</Uri></MediaUri>")
            }
            "GetDeviceInformation" -> reply(action,
                "<Manufacturer>Technology</Manufacturer><Model>IPC</Model><FirmwareVersion>46.00.53</FirmwareVersion>")
            "ContinuousMove" -> reply(action)
            "Stop" -> OnvifPtz.Reply(200, "") // firmware behavior: no SOAP confirmation
            else -> error("Unexpected action $action")
        }
    }
    check(OnvifPtz.isLanIPv4("192.168.0.18"))
    check(OnvifPtz.isLanIPv4("10.0.0.19"))
    check(!OnvifPtz.isLanIPv4("192.168.01.3"))
    check(!OnvifPtz.isLanIPv4("198.51.100.10"))
    val session = checkNotNull(OnvifPtz.discover(base, "/onvif2", false, fake).session)
    check(received.all { it.url.startsWith("$base/") })
    check(received.none { it.action == "ContinuousMove" || it.action == "Stop" })
    check(session.pulse(OnvifPtz.Direction.LEFT) == OnvifPtz.PulseResult(true, false))
    val (move, stop) = received.takeLast(2)
    check(move.action == "ContinuousMove" && stop.action == "Stop")
    check(move.url == "$base/onvif/deviceio_service" && stop.url == move.url)
    check("<ProfileToken>test-sub</ProfileToken>" in move.body)
    check("x=\"-0.2\" y=\"0\"" in move.body && "<Timeout>PT0H0M0.200S</Timeout>" in move.body)
    check("<PanTilt>true</PanTilt>" in stop.body)
    check(OnvifPtz.discover(base, "/another-source", false, fake).failure == OnvifPtz.Failure.PROFILES)

    val standard = OnvifPtz.Transport { url, ns, action, body ->
        if (action == "GetDeviceInformation") reply(action, "<Model>generic</Model>")
        else if (action == "ContinuousMove") {
            check(url == "$base/onvif/PTZ")
            check("x=\"0\" y=\"0.2\"" in body)
            OnvifPtz.Reply(500, "<Fault/>")
        } else if (action == "Stop") {
            check(url == "$base/onvif/PTZ")
            reply(action)
        } else fake.send(url, ns, action, body)
    }
    val normal = checkNotNull(OnvifPtz.discover(base, "/onvif1", false, standard).session)
    check(normal.pulse(OnvifPtz.Direction.UP) == OnvifPtz.PulseResult(false, true))
    val refused = OnvifPtz.Transport { _, _, _, _ -> OnvifPtz.Reply(401, "") }
    check(OnvifPtz.discover(base, "/onvif1", true, refused).failure == OnvifPtz.Failure.AUTH)
    println("OK: PTZ local, perfil por URI, pulso curto, rota compatível e parada sem confirmação")
}
