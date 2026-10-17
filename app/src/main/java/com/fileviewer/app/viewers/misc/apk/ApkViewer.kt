package com.fileviewer.app.viewers.misc.apk

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fileviewer.app.detect.Family
import com.fileviewer.app.source.FileSource
import com.fileviewer.app.ui.components.Loading
import com.fileviewer.app.ui.components.Message
import com.fileviewer.app.ui.formatSize
import com.fileviewer.app.ui.theme.Fv
import com.fileviewer.app.ui.theme.MonoBody
import com.fileviewer.app.viewer.ViewerId
import com.fileviewer.app.viewers.misc.Badge
import com.fileviewer.app.viewers.misc.MiscFact
import com.fileviewer.app.viewers.misc.MiscSection
import com.fileviewer.app.viewers.misc.WithRealFile
import com.fileviewer.app.viewers.misc.cert.CertInfo
import com.fileviewer.app.viewers.misc.cert.Validity
import com.fileviewer.app.viewers.misc.cert.day
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

/** What the package declares: name, icon, version, SDK range, permissions and signers. Nothing is installed. */
@Composable
fun ApkViewer(source: FileSource, onSwitch: (ViewerId) -> Unit) {
    val context = LocalContext.current
    WithRealFile(source) { file ->
        val facts by produceState<Result<ApkFacts?>?>(null, file) {
            value = withContext(Dispatchers.IO) { runCatching { ApkReader.read(context, file) } }
        }
        val result = facts ?: return@WithRealFile Loading()
        val apk = result.getOrNull()
        if (apk == null) {
            Message(
                "Android couldn't read this package",
                detail = result.exceptionOrNull()?.message
                    ?: "It may be an app bundle (APKS, XAPK) or a damaged file. Browse its contents or raw bytes instead.",
                actions = listOf("Show contents" to { onSwitch(ViewerId.Archive) }, "Show hex" to { onSwitch(ViewerId.Hex) }),
            )
        } else {
            ApkDetails(source, apk)
        }
    }
}

@Composable
private fun ApkDetails(source: FileSource, apk: ApkFacts) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            apk.icon?.let {
                Image(it.asImageBitmap(), contentDescription = "App icon", modifier = Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)))
            }
            Column(Modifier.padding(start = if (apk.icon != null) 16.dp else 0.dp)) {
                Text(apk.label, style = MaterialTheme.typography.headlineSmall, color = Fv.palette.ink)
                Text(apk.packageName, style = MonoBody, color = Fv.palette.muted)
                Text(
                    "Version ${apk.versionName ?: "unknown"} (build ${apk.versionCode})",
                    style = MaterialTheme.typography.bodyMedium, color = Fv.palette.muted, modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        SelectionContainer {
            Column {
                MiscSection("Requirements")
                MiscFact("Runs on", "${AndroidVersions.describe(apk.minSdk)} and later")
                MiscFact("Built for", AndroidVersions.describe(apk.targetSdk))
                MiscFact("File size", formatSize(source.size))
                if (apk.debuggable) MiscFact("Build", "Debuggable", valueColor = Fv.palette.danger)
            }
        }
        Signing(apk.signers)
        Permissions(apk.permissions)
    }
}

@Composable
private fun Signing(signers: List<ApkSigner>) {
    MiscSection(if (signers.size > 1) "Signed by ${signers.size} certificates" else "Signed by")
    if (signers.isEmpty()) {
        Text("This package isn't signed, so Android won't install it.", style = MaterialTheme.typography.bodyMedium, color = Fv.palette.muted)
        return
    }
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (signer in signers) {
                Column {
                    signer.certificate?.let { SignerName(it) }
                    MiscFact("SHA-256", signer.sha256, mono = true)
                }
            }
        }
    }
}

@Composable
private fun SignerName(cert: CertInfo) {
    val validity = cert.validityAt(Instant.now())
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(cert.subject.summary, style = MaterialTheme.typography.bodyMedium, color = Fv.palette.ink, modifier = Modifier.weight(1f))
        when (validity) {
            Validity.Expired -> Badge("Expired ${cert.notAfter.day()}", Fv.palette.danger)
            Validity.NotYetValid -> Badge("Not valid until ${cert.notBefore.day()}", Fv.palette.family(Family.Media))
            Validity.Valid -> Badge("Valid until ${cert.notAfter.day()}", Fv.palette.family(Family.Image))
        }
    }
}

@Composable
private fun Permissions(permissions: List<ApkPermission>) {
    if (permissions.isEmpty()) {
        MiscSection("Permissions")
        Text("This package asks for no permissions.", style = MaterialTheme.typography.bodyMedium, color = Fv.palette.muted)
        return
    }
    val sensitive = permissions.count { it.sensitive }
    MiscSection("Permissions  ${permissions.size}")
    if (sensitive > 0) {
        Text(
            "$sensitive ${if (sensitive == 1) "is" else "are"} sensitive: Android asks you before granting ${if (sensitive == 1) "it" else "them"}.",
            style = MaterialTheme.typography.bodyMedium, color = Fv.palette.muted, modifier = Modifier.padding(bottom = 6.dp),
        )
    }
    SelectionContainer {
        Column {
            for (p in permissions) {
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.shortName, style = MonoBody, color = Fv.palette.ink, modifier = Modifier.weight(1f))
                    if (p.sensitive) Badge("Sensitive", Fv.palette.danger)
                }
            }
        }
    }
}
