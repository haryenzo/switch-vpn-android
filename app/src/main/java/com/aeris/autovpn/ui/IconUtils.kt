package com.aeris.autovpn.ui

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import com.aeris.autovpn.data.VpnAppTarget

fun Drawable.toIconBitmap(sizePx: Int = 96): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, sizePx, sizePx)
    draw(canvas)
    return bitmap
}

// Looks up the real launcher icon for a package (the watched app, or an installed VPN target
// like Happ/Incy). Returns null when the app isn't installed, so callers fall back to a
// letter placeholder instead of crashing.
@Composable
fun rememberAppIcon(packageName: String): ImageBitmap? {
    val context = LocalContext.current
    return remember(packageName) {
        if (packageName.isEmpty()) {
            null
        } else {
            try {
                context.packageManager.getApplicationIcon(packageName).toIconBitmap().asImageBitmap()
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
    }
}

// Happ ships under a couple of known package-name variants (see VpnAppTarget.packageNameAliases)
// depending on the build the user installed — try the primary id, then each alias, before
// falling back to a bundled icon (fallbackIconRes) or, lacking that, a letter placeholder.
@Composable
fun rememberAppIcon(target: VpnAppTarget): ImageBitmap? {
    val context = LocalContext.current
    val installed = remember(target.id) {
        (listOf(target.packageName) + target.packageNameAliases).firstNotNullOfOrNull { pkg ->
            try {
                context.packageManager.getApplicationIcon(pkg).toIconBitmap().asImageBitmap()
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
    }
    if (installed != null) return installed
    return remember(target.fallbackIconRes) {
        target.fallbackIconRes?.let { resId ->
            ResourcesCompat.getDrawable(context.resources, resId, context.theme)?.toIconBitmap()?.asImageBitmap()
        }
    }
}
