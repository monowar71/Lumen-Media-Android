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
 * Artwork URL sized for the target view.
 *
 * Deliberately token-free: Coil authenticates via the shared OkHttpClient's
 * Authorization header, and a token in the URL would poison Coil's cache keys
 * (every token refresh invalidates the whole poster cache).
 */
fun artworkUrl(
    baseUrl: String,
    path: String?,
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
    }
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
