package com.fileviewer.app.viewers.misc.apk

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import com.fileviewer.app.viewers.misc.MiscLimits
import com.fileviewer.app.viewers.misc.cert.CertInfo
import com.fileviewer.app.viewers.misc.cert.digest
import java.io.File
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class ApkPermission(val name: String, val sensitive: Boolean) {
    /** `android.permission.CAMERA` reads as `CAMERA`; other namespaces keep their full name. */
    val shortName: String get() = name.removePrefix("android.permission.")
}

/** A signing certificate: its SHA-256, plus the parsed certificate when it can be read. */
class ApkSigner(val sha256: String, val certificate: CertInfo?)

class ApkFacts(
    val label: String,
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val debuggable: Boolean,
    val permissions: List<ApkPermission>,
    val signers: List<ApkSigner>,
    val icon: Bitmap?,
)

object ApkReader {
    /** Null when the platform can't parse [file] as an APK (an APKS or XAPK bundle, or a damaged file). */
    fun read(context: Context, file: File): ApkFacts? {
        val pm = context.packageManager
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNING_CERTIFICATES
        val info = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: return null
        val app = info.applicationInfo ?: return null
        // Without these the platform can't resolve the label and icon from the archive.
        app.sourceDir = file.absolutePath
        app.publicSourceDir = file.absolutePath
        return ApkFacts(
            label = runCatching { pm.getApplicationLabel(app).toString() }.getOrDefault(info.packageName),
            packageName = info.packageName,
            versionName = info.versionName,
            versionCode = info.longVersionCode,
            minSdk = app.minSdkVersion,
            targetSdk = app.targetSdkVersion,
            debuggable = app.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
            permissions = permissions(pm, info),
            signers = signers(info),
            icon = runCatching { render(app.loadIcon(pm)) }.getOrNull(),
        )
    }

    /** Dangerous-level permissions first, then the rest in manifest order. */
    private fun permissions(pm: PackageManager, info: PackageInfo): List<ApkPermission> {
        val all = info.requestedPermissions.orEmpty().take(MiscLimits.MAX_PERMISSIONS).map { name ->
            val level = runCatching { pm.getPermissionInfo(name, 0).protection }.getOrNull()
            ApkPermission(name, sensitive = level == PermissionInfo.PROTECTION_DANGEROUS)
        }
        return all.sortedByDescending { it.sensitive }
    }

    private fun signers(info: PackageInfo): List<ApkSigner> {
        val signing = info.signingInfo ?: return emptyList()
        val signatures = if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        return signatures.orEmpty().map { sig ->
            val der = sig.toByteArray()
            val cert = runCatching {
                CertInfo.from(CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(der)) as X509Certificate)
            }.getOrNull()
            ApkSigner(der.digest("SHA-256"), cert)
        }
    }

    private fun render(icon: android.graphics.drawable.Drawable): Bitmap {
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, size, size)
        icon.draw(Canvas(bitmap))
        return bitmap
    }
}
