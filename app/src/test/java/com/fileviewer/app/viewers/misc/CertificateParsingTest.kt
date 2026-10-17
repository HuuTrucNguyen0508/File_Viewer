package com.fileviewer.app.viewers.misc

import com.fileviewer.app.viewers.misc.cert.CertificateParser
import com.fileviewer.app.viewers.misc.cert.DistinguishedName
import com.fileviewer.app.viewers.misc.cert.KeyItem
import com.fileviewer.app.viewers.misc.cert.Validity
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CertificateParsingTest {
    private fun resource(name: String): ByteArray = javaClass.getResourceAsStream("/certs/$name")!!.use { it.readBytes() }

    private fun certs(name: String) = CertificateParser.parse(resource(name)).items.map { (it as KeyItem.Certificate).info }

    private val now = Instant.parse("2026-10-17T00:00:00Z")

    @Test fun leafFieldsMatchWhatOpensslPrints() {
        val leaf = certs("leaf.der").single()
        assertEquals("leaf.example.test", leaf.subject.commonName)
        assertEquals("Fixture, Inc.", leaf.subject.parts.first { it.first == "O" }.second)
        assertEquals("Fixture Root CA", leaf.issuer.commonName)
        assertEquals(Instant.parse("2024-01-01T00:00:00Z"), leaf.notBefore)
        assertEquals(Instant.parse("2124-01-01T00:00:00Z"), leaf.notAfter)
        assertEquals("16:D6:35:71:87:CB:E4:12:58:15:74:F7:0E:6E:B9:A4:4A:F2:C8:50", leaf.serial)
        assertEquals("E6:69:83:54:BD:82:10:97:CD:FC:CB:5C:21:3E:04:B8:2C:8D:C0:D4:F4:10:35:77:20:3E:E1:E5:57:6A:16:9F", leaf.sha256)
        assertEquals("FF:CF:13:63:85:A7:0C:13:9F:24:CB:0C:6E:5F:26:A0:3B:12:A0:DA", leaf.sha1)
        assertEquals("EC", leaf.keyAlgorithm)
        assertEquals(256, leaf.keyBits)
        assertTrue(leaf.signatureAlgorithm, leaf.signatureAlgorithm.contains("RSA", ignoreCase = true))
        assertEquals(listOf("DNS leaf.example.test", "DNS *.example.test", "IP 192.0.2.7", "Email ops@example.test"), leaf.subjectAltNames)
        assertEquals(listOf("Digital signature"), leaf.keyUsage)
        assertEquals(listOf("Server authentication", "Client authentication"), leaf.extendedKeyUsage)
        assertFalse(leaf.isCa)
        assertFalse(leaf.selfSigned)
    }

    @Test fun pemChainListsEveryCertificateInOrder() {
        val chain = certs("chain.pem")
        assertEquals(listOf("leaf.example.test", "Fixture Root CA"), chain.map { it.subject.commonName })
        val root = chain[1]
        assertTrue(root.isCa)
        assertTrue(root.selfSigned)
        assertEquals("RSA", root.keyAlgorithm)
        assertEquals(2048, root.keyBits)
        assertEquals(listOf("Certificate signing", "CRL signing"), root.keyUsage)
    }

    @Test fun pemAndDerOfTheSameCertificateAgree() {
        val pem = certs("chain.pem").first()
        val der = certs("leaf.der").single()
        assertEquals(der.sha256, pem.sha256)
    }

    @Test fun validityIsJudgedAgainstTheGivenTime() {
        val expired = certs("expired.pem").single()
        assertEquals(Validity.Expired, expired.validityAt(now))
        assertEquals(Validity.Valid, expired.validityAt(Instant.parse("2019-06-01T00:00:00Z")))
        assertEquals(Validity.NotYetValid, expired.validityAt(Instant.parse("2018-12-31T23:59:59Z")))
        assertTrue(expired.selfSigned)
        assertEquals(Validity.Valid, certs("leaf.der").single().validityAt(now))
    }

    @Test fun publicKeyPemShowsAlgorithmSizeAndDigest() {
        val key = CertificateParser.parse(resource("public-key.pem")).items.single() as KeyItem.PublicKeyItem
        assertEquals("EC", key.algorithm)
        assertEquals(256, key.bits)
        assertEquals(95, key.sha256.length)
    }

    @Test fun privateKeysAreNamedButNeverRead() {
        val secret = "c2VjcmV0LWtleS1tYXRlcmlhbC10aGF0LW11c3Qtbm90LWFwcGVhcg=="
        val pem = "# my keys\n-----BEGIN RSA PRIVATE KEY-----\n$secret\n-----END RSA PRIVATE KEY-----\n" +
            "-----BEGIN ENCRYPTED PRIVATE KEY-----\n$secret\n-----END ENCRYPTED PRIVATE KEY-----\n" +
            "-----BEGIN PRIVATE KEY-----\n$secret\n-----END PRIVATE KEY-----\n"
        val items = CertificateParser.parse(pem.toByteArray()).items.map { it as KeyItem.PrivateKeyItem }
        assertEquals(listOf("RSA private key", "Private key", "Private key"), items.map { it.kind })
        assertEquals(listOf(false, true, false), items.map { it.encrypted })
        for (item in items) {
            assertFalse(item.kind.contains(secret))
            val fields = item.javaClass.declaredFields.map { it.apply { isAccessible = true }.get(item).toString() }
            assertTrue(fields.none { secret in it || "c2VjcmV0" in it })
        }
    }

    @Test fun legacyEncryptedPemHeaderIsRecognised() {
        val pem = "-----BEGIN RSA PRIVATE KEY-----\nProc-Type: 4,ENCRYPTED\nDEK-Info: AES-128-CBC,00\n\nQUJD\n-----END RSA PRIVATE KEY-----\n"
        assertTrue((CertificateParser.parse(pem.toByteArray()).items.single() as KeyItem.PrivateKeyItem).encrypted)
    }

    @Test fun certificateBundledWithAKeyShowsBoth() {
        val pem = String(resource("chain.pem")) +
            "-----BEGIN PRIVATE KEY-----\nQUJD\n-----END PRIVATE KEY-----\n"
        val items = CertificateParser.parse(pem.toByteArray()).items
        assertEquals(listOf("Certificate", "Certificate", "PrivateKeyItem"), items.map { it.javaClass.simpleName })
    }

    @Test fun brokenInputsBecomeExplainedItems() {
        val bad = CertificateParser.parse("-----BEGIN CERTIFICATE-----\n!!!notbase64!!!\n-----END CERTIFICATE-----\n".toByteArray()).items.single()
        assertTrue(bad is KeyItem.Unreadable)
        val garbage = CertificateParser.parse(ByteArray(64) { it.toByte() }).items.single() as KeyItem.Unreadable
        assertEquals("DER data", garbage.label)
        val truncated = CertificateParser.parse(resource("leaf.der").copyOf(100)).items.single()
        assertTrue(truncated is KeyItem.Unreadable)
        assertTrue(CertificateParser.parse(ByteArray(0)).items.single() is KeyItem.Unreadable)
    }

    @Test fun unknownBlocksAreReportedNotParsed() {
        val item = CertificateParser.parse("-----BEGIN CERTIFICATE REQUEST-----\nQUJD\n-----END CERTIFICATE REQUEST-----\n".toByteArray()).items.single() as KeyItem.Unreadable
        assertEquals("Certificate signing request", item.label)
    }

    @Test fun distinguishedNamesHandleEscapesAndMultiValueParts() {
        val dn = DistinguishedName.parse("CN=a\\,b,O=Acme\\, Inc.,C=NL")
        assertEquals(listOf("CN" to "a,b", "O" to "Acme, Inc.", "C" to "NL"), dn.parts)
        assertEquals("a,b", dn.summary)
        assertNull(DistinguishedName.parse("O=Org").commonName)
        assertEquals("Org", DistinguishedName.parse("O=Org").summary)
        assertEquals("Common name", DistinguishedName.label("CN"))
        assertEquals("2.5.4.99", DistinguishedName.label("2.5.4.99"))
    }
}
