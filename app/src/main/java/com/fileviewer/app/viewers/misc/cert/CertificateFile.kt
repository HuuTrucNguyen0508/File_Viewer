package com.fileviewer.app.viewers.misc.cert

import com.fileviewer.app.source.Limits
import java.io.ByteArrayInputStream
import java.security.KeyFactory
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.X509EncodedKeySpec

/** Something found in a certificate or key file. */
sealed interface KeyItem {
    class Certificate(val info: CertInfo) : KeyItem

    class PublicKeyItem(val algorithm: String, val bits: Int?, val sha256: String) : KeyItem

    /** Only what kind of key it is. The key material is never read into this model. */
    class PrivateKeyItem(val kind: String, val encrypted: Boolean) : KeyItem

    class Unreadable(val label: String, val reason: String) : KeyItem
}

class CertificateFile(val items: List<KeyItem>, val truncated: Boolean)

object CertificateParser {
    private val KEY_ALGORITHMS = listOf("RSA", "EC", "DSA", "Ed25519", "Ed448", "X25519")

    /** Parses PEM text (certificates, public keys, PKCS#7, private-key markers) or one DER certificate/key. */
    fun parse(bytes: ByteArray): CertificateFile {
        val limit = Limits.MAX_CERT_ITEMS
        val text = String(bytes, Charsets.ISO_8859_1)
        val items = if (PemBlocks.isPem(text)) {
            PemBlocks.parse(text, limit + 1).flatMap(::pemItem).ifEmpty {
                listOf(KeyItem.Unreadable("PEM data", "No complete certificate or key blocks were found."))
            }
        } else {
            der(bytes)
        }
        return CertificateFile(items.take(limit), truncated = items.size > limit)
    }

    private fun pemItem(block: PemBlock): List<KeyItem> = when (block) {
        is PemBlock.Secret -> listOf(KeyItem.PrivateKeyItem(secretKind(block.label), block.encrypted))
        is PemBlock.Invalid -> listOf(KeyItem.Unreadable(block.label, block.reason))
        is PemBlock.Data -> when {
            block.label.endsWith("CERTIFICATE") || block.label == "PKCS7" || block.label == "CMS" -> certificates(block.der, block.label)
            block.label == "PUBLIC KEY" -> listOf(publicKey(block.der) ?: KeyItem.Unreadable(block.label, "Not a supported public key."))
            block.label == "CERTIFICATE REQUEST" || block.label == "NEW CERTIFICATE REQUEST" ->
                listOf(KeyItem.Unreadable("Certificate signing request", "Requests aren't parsed. Switch to Text to read it."))
            else -> listOf(KeyItem.Unreadable(block.label.lowercase().replaceFirstChar(Char::titlecase), "This kind of block isn't parsed."))
        }
    }

    private fun der(bytes: ByteArray): List<KeyItem> {
        val certs = certificates(bytes, "Certificate")
        if (certs.none { it is KeyItem.Unreadable }) return certs
        return listOf(publicKey(bytes) ?: KeyItem.Unreadable("DER data", "This isn't an X.509 certificate, PKCS#7 bundle or public key."))
    }

    private fun certificates(der: ByteArray, label: String): List<KeyItem> = try {
        CertificateFactory.getInstance("X.509").generateCertificates(ByteArrayInputStream(der))
            .filterIsInstance<X509Certificate>()
            .map { KeyItem.Certificate(CertInfo.from(it)) }
            .ifEmpty { listOf(KeyItem.Unreadable(label, "No certificates in this block.")) }
    } catch (e: Exception) {
        listOf(KeyItem.Unreadable(label, "Couldn't parse it as a certificate: ${e.message ?: e.javaClass.simpleName}"))
    }

    private fun publicKey(der: ByteArray): KeyItem.PublicKeyItem? {
        val spec = X509EncodedKeySpec(der)
        for (algorithm in KEY_ALGORITHMS) {
            val key: PublicKey = runCatching { KeyFactory.getInstance(algorithm).generatePublic(spec) }.getOrNull() ?: continue
            return KeyItem.PublicKeyItem(key.algorithm, CertInfo.keyBits(key), der.digest("SHA-256"))
        }
        return null
    }

    /** "RSA PRIVATE KEY" becomes "RSA private key"; PKCS#8 "PRIVATE KEY" has no algorithm in its label. */
    private fun secretKind(label: String): String {
        val prefix = label.removeSuffix("PRIVATE KEY").removePrefix("ENCRYPTED ").trim()
        return if (prefix.isEmpty()) "Private key" else "$prefix private key"
    }
}
