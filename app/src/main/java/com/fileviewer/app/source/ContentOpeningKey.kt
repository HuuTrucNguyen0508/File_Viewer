package com.fileviewer.app.source

/** Created with a source, which AppState retains through rotation and viewer changes. */
class ContentOpeningKey(uri: String) {
    val key: String = "uri:$uri:${java.util.UUID.randomUUID()}"
}
