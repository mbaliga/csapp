package com.mbaliga.csapp.ui.nav

object Destinations {
    const val DASHBOARD = "dashboard"
    const val INCIDENT_DETAIL = "incident/{incidentId}"
    const val MANUAL_INCIDENT = "incident/new"
    const val REPLY = "reply/{sourceKey}"
    const val SETTINGS = "settings"
    const val EXPORT = "export"

    fun incidentDetail(incidentId: String) = "incident/$incidentId"
    fun reply(sourceKey: String) = "reply/${android.net.Uri.encode(sourceKey)}"
}
