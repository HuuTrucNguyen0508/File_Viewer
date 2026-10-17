package com.fileviewer.app.viewers.misc.cert

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fileviewer.app.detect.Family
import com.fileviewer.app.source.FileSource
import com.fileviewer.app.source.LimitedInputStream
import com.fileviewer.app.ui.components.Loading
import com.fileviewer.app.ui.components.Message
import com.fileviewer.app.ui.theme.Fv
import com.fileviewer.app.viewer.ViewerId
import com.fileviewer.app.viewers.misc.Badge
import com.fileviewer.app.viewers.misc.MiscFact
import com.fileviewer.app.viewers.misc.MiscLimits
import com.fileviewer.app.viewers.misc.MiscSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.time.Instant

/** Certificates, certificate chains and keys. Private keys are named, never shown. */
@Composable
fun CertViewer(source: FileSource, onSwitch: (ViewerId) -> Unit) {
    val parsed by produceState<Result<CertificateFile>?>(null, source) {
        value = runInterruptible(Dispatchers.IO) {
            runCatching {
                val bytes = LimitedInputStream(source.openStream(), MiscLimits.MAX_CERT_FILE_BYTES, source.name).use { it.readBytes() }
                CertificateParser.parse(bytes)
            }
        }
    }
    val result = parsed ?: return Loading()
    result.onFailure {
        Message(
            "Couldn't read this file",
            detail = it.message,
            actions = listOf("Show text" to { onSwitch(ViewerId.Text) }, "Show hex" to { onSwitch(ViewerId.Hex) }),
        )
    }
    result.onSuccess { file ->
        val now = Instant.now()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (file.items.size > 1) {
                Text(
                    "${file.items.size}${if (file.truncated) "+" else ""} items in this file",
                    style = MaterialTheme.typography.bodyMedium, color = Fv.palette.muted, modifier = Modifier.padding(top = 12.dp),
                )
            }
            file.items.forEachIndexed { i, item ->
                if (i > 0) HorizontalDivider(Modifier.padding(top = 20.dp), color = Fv.palette.rule)
                when (item) {
                    is KeyItem.Certificate -> CertificateCard(item.info, now, position = if (file.items.size > 1) i + 1 else null)
                    is KeyItem.PublicKeyItem -> PublicKeyCard(item)
                    is KeyItem.PrivateKeyItem -> PrivateKeyCard(item)
                    is KeyItem.Unreadable -> UnreadableCard(item)
                }
            }
        }
    }
}

@Composable
private fun Heading(title: String, badges: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Fv.palette.ink, modifier = Modifier.weight(1f))
        badges()
    }
}

@Composable
private fun CertificateCard(c: CertInfo, now: Instant, position: Int?) {
    val validity = c.validityAt(now)
    Heading(c.subject.summary) {
        when (validity) {
            Validity.Expired -> Badge("Expired", Fv.palette.danger)
            Validity.NotYetValid -> Badge("Not valid yet", Fv.palette.family(Family.Media))
            Validity.Valid -> Badge("Valid", Fv.palette.family(Family.Image))
        }
    }
    Row(Modifier.padding(top = 4.dp)) {
        val role = buildList {
            position?.let { add("Certificate $it") }
            if (c.isCa) add("Certificate authority")
            if (c.selfSigned) add("self-signed")
        }
        if (role.isNotEmpty()) Text(role.joinToString(", "), style = MaterialTheme.typography.bodyMedium, color = Fv.palette.muted)
    }
    SelectionContainer {
        Column {
            MiscSection("Subject")
            c.subject.parts.forEach { (k, v) -> MiscFact(DistinguishedName.label(k), v) }
            MiscSection("Issuer")
            c.issuer.parts.forEach { (k, v) -> MiscFact(DistinguishedName.label(k), v) }
            MiscSection("Validity")
            MiscFact("Not before", c.notBefore.day())
            MiscFact(
                "Not after", c.notAfter.day(),
                valueColor = if (validity == Validity.Expired) Fv.palette.danger else Fv.palette.ink,
            )
            MiscSection("Technical")
            MiscFact("Serial", c.serial, mono = true)
            MiscFact("Signature", c.signatureAlgorithm)
            MiscFact("Public key", listOfNotNull(c.keyAlgorithm, c.keyBits?.let { "$it-bit" }).joinToString(" "))
            if (c.keyUsage.isNotEmpty()) MiscFact("Key usage", c.keyUsage.joinToString("\n"))
            if (c.extendedKeyUsage.isNotEmpty()) MiscFact("Also allows", c.extendedKeyUsage.joinToString("\n"))
            if (c.subjectAltNames.isNotEmpty()) {
                MiscSection("Alternative names")
                c.subjectAltNames.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = Fv.palette.ink, modifier = Modifier.padding(vertical = 2.dp)) }
            }
            MiscSection("Fingerprints")
            MiscFact("SHA-256", c.sha256, mono = true)
            MiscFact("SHA-1", c.sha1, mono = true)
        }
    }
}

@Composable
private fun PublicKeyCard(k: KeyItem.PublicKeyItem) {
    Heading("Public key")
    SelectionContainer {
        Column {
            MiscSection("Key")
            MiscFact("Algorithm", k.algorithm)
            k.bits?.let { MiscFact("Size", "$it bits") }
            MiscFact("SHA-256", k.sha256, mono = true)
        }
    }
}

@Composable
private fun PrivateKeyCard(k: KeyItem.PrivateKeyItem) {
    Heading(k.kind.replaceFirstChar(Char::titlecase)) { Icon(Icons.Outlined.Lock, contentDescription = null, tint = Fv.palette.muted) }
    Text(
        (if (k.encrypted) "Password-protected. " else "") + "This is a private key, so it isn't displayed.",
        style = MaterialTheme.typography.bodyMedium, color = Fv.palette.muted, modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun UnreadableCard(u: KeyItem.Unreadable) {
    Heading(u.label)
    Text(u.reason, style = MaterialTheme.typography.bodyMedium, color = Fv.palette.muted, modifier = Modifier.padding(top = 4.dp))
}
