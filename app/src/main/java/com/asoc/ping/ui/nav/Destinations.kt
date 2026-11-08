package com.asoc.ping.ui.nav

object Destinations {
    const val HOME = "home"
    const val INCIDENT_DETAIL = "incident/{incidentId}"
    const val NEW_SIGNAL = "signal/new"
    const val REPLY = "reply/{sourceKey}"
    const val SETTINGS = "settings"
    const val PROJECTS = "projects"
    const val PROJECT_DETAIL = "project/{projectId}"

    fun incidentDetail(id: String) = "incident/${android.net.Uri.encode(id)}"
    fun reply(sourceKey: String) = "reply/${android.net.Uri.encode(sourceKey)}"
    fun project(id: String) = "project/${android.net.Uri.encode(id)}"
}
