package com.fileviewer.app.viewers.misc.cert

import java.math.BigInteger
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.security.interfaces.DSAPublicKey
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import javax.security.auth.x500.X500Principal

enum class Validity { Valid, Expired, NotYetValid }

/** The fields the certificate viewer shows for one X.509 certificate. */
class CertInfo(
    val subject: DistinguishedName,
    val issuer: DistinguishedName,
    val notBefore: Instant,
    val notAfter: Instant,
    val serial: String,
    val signatureAlgorithm: String,
    val keyAlgorithm: String,
    val keyBits: Int?,
    val subjectAltNames: List<String>,
    val keyUsage: List<String>,
    val extendedKeyUsage: List<String>,
    val isCa: Boolean,
    val selfSigned: Boolean,
    val sha1: String,
    val sha256: String,
) {
    fun validityAt(now: Instant): Validity = when {
        now.isBefore(notBefore) -> Validity.NotYetValid
        now.isAfter(notAfter) -> Validity.Expired
        else -> Validity.Valid
    }

    companion object {
        private val KEY_USAGE = listOf(
            "Digital signature", "Non-repudiation", "Key encipherment", "Data encipherment", "Key agreement",
            "Certificate signing", "CRL signing", "Encipher only", "Decipher only",
        )
        private val EXTENDED_KEY_USAGE = mapOf(
            "1.3.6.1.5.5.7.3.1" to "Server authentication", "1.3.6.1.5.5.7.3.2" to "Client authentication",
            "1.3.6.1.5.5.7.3.3" to "Code signing", "1.3.6.1.5.5.7.3.4" to "Email protection",
            "1.3.6.1.5.5.7.3.8" to "Time stamping", "1.3.6.1.5.5.7.3.9" to "OCSP signing",
        )
        private val NAME_KEYWORDS = mapOf("2.5.4.5" to "SERIALNUMBER", "1.2.840.113549.1.9.1" to "EMAIL")
        private val FIXED_KEY_BITS = mapOf("Ed25519" to 256, "Ed448" to 456, "X25519" to 255, "X448" to 448)

        fun from(cert: X509Certificate): CertInfo {
            val encoded = cert.encoded
            val subject = cert.subjectX500Principal.getName(X500Principal.RFC2253, NAME_KEYWORDS)
            val issuer = cert.issuerX500Principal.getName(X500Principal.RFC2253, NAME_KEYWORDS)
            return CertInfo(
                subject = DistinguishedName.parse(subject),
                issuer = DistinguishedName.parse(issuer),
                notBefore = cert.notBefore.toInstant(),
                notAfter = cert.notAfter.toInstant(),
                serial = cert.serialNumber.toColonHex(),
                signatureAlgorithm = cert.sigAlgName,
                keyAlgorithm = cert.publicKey.algorithm,
                keyBits = keyBits(cert.publicKey),
                subjectAltNames = altNames(cert),
                keyUsage = cert.keyUsage?.let { bits -> KEY_USAGE.filterIndexed { i, _ -> bits.getOrNull(i) == true } } ?: emptyList(),
                extendedKeyUsage = runCatching { cert.extendedKeyUsage }.getOrNull().orEmpty().map { EXTENDED_KEY_USAGE[it] ?: it },
                isCa = cert.basicConstraints >= 0,
                selfSigned = subject == issuer,
                sha1 = encoded.digest("SHA-1"),
                sha256 = encoded.digest("SHA-256"),
            )
        }

        fun keyBits(key: PublicKey): Int? = when (key) {
            is RSAPublicKey -> key.modulus.bitLength()
            is ECPublicKey -> key.params.order.bitLength()
            is DSAPublicKey -> key.params.p.bitLength()
            else -> FIXED_KEY_BITS[key.algorithm]
        }

        private fun BigInteger.toColonHex(): String = abs().toByteArray().dropWhile { it.toInt() == 0 }.toByteArray().colonHex().ifEmpty { "00" }

        /** DNS names, addresses, emails and URIs, each prefixed with what it is. */
        private fun altNames(cert: X509Certificate): List<String> {
            val names = runCatching { cert.subjectAlternativeNames }.getOrNull() ?: return emptyList()
            return names.mapNotNull { entry ->
                val value = entry.getOrNull(1)?.toString() ?: return@mapNotNull null
                when (entry[0] as? Int) {
                    1 -> "Email $value"
                    2 -> "DNS $value"
                    4 -> "Directory $value"
                    6 -> "URI $value"
                    7 -> "IP $value"
                    else -> value
                }
            }
        }
    }
}
