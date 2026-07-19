package com.freeplex.android.core.util

fun normalizeBaseUrl(raw: String): String {
    var url = raw.trim()
    if (url.isEmpty()) return url
    if (!url.contains("://")) url = "http://$url"
    while (url.endsWith("/")) url = url.dropLast(1)
    return url
}

fun absoluteUrl(baseUrl: String, pathOrUrl: String): String {
    if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) return pathOrUrl
    val base = normalizeBaseUrl(baseUrl)
    return if (pathOrUrl.startsWith("/")) "$base$pathOrUrl" else "$base/$pathOrUrl"
}

/**
 * Artwork URL sized for the target view. Mirrors web `artworkUrl`:
 * pass short-lived JWT as `access_token` because image loaders often cannot set headers.
 */
fun artworkUrl(
    baseUrl: String,
    path: String?,
    token: String? = null,
    width: Int? = null,
    height: Int? = null,
    quality: Int = 80,
): String? {
    if (path.isNullOrBlank()) return null
    val absolute = absoluteUrl(baseUrl, path)
    val params = buildList {
        if (width != null) add("w=$width")
        if (height != null) add("h=$height")
        add("quality=$quality")
        if (!token.isNullOrBlank()) {
            // String charset overload: Charset overload requires API 33+
            @Suppress("DEPRECATION")
            add("access_token=${java.net.URLEncoder.encode(token, "UTF-8")}")
        }
    }
    if (params.isEmpty()) return absolute
    val separator = if (absolute.contains('?')) '&' else '?'
    return absolute + separator + params.joinToString("&")
}

fun rewriteLoopbackForEmulator(baseUrl: String, isEmulator: Boolean = isRunningOnEmulator()): String {
    val normalized = normalizeBaseUrl(baseUrl)
    if (!isEmulator) return normalized
    return normalized
        .replace("://localhost", "://10.0.2.2")
        .replace("://127.0.0.1", "://10.0.2.2")
}

fun isRunningOnEmulator(): Boolean {
    val fingerprint = android.os.Build.FINGERPRINT
    val model = android.os.Build.MODEL
    val product = android.os.Build.PRODUCT
    val manufacturer = android.os.Build.MANUFACTURER
    val brand = android.os.Build.BRAND
    val device = android.os.Build.DEVICE
    return fingerprint.startsWith("generic") ||
        fingerprint.startsWith("unknown") ||
        model.contains("google_sdk", ignoreCase = true) ||
        model.contains("Emulator", ignoreCase = true) ||
        model.contains("Android SDK built for", ignoreCase = true) ||
        manufacturer.contains("Genymotion", ignoreCase = true) ||
        (brand.startsWith("generic") && device.startsWith("generic")) ||
        product == "google_sdk" ||
        product.contains("sdk_gphone", ignoreCase = true)
}
