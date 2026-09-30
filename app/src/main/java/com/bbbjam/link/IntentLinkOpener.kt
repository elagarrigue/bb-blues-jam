package com.bbbjam.link

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.bbbjam.core.ui.link.ExternalLinkOpener

/**
 * Opens a link with an `ACTION_VIEW` intent, in the browser or the site's own app. [context] is the
 * application context, so the intent needs `FLAG_ACTIVITY_NEW_TASK`.
 */
class IntentLinkOpener(private val context: Context) : ExternalLinkOpener {
    override fun open(url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (ignored: ActivityNotFoundException) {
            false
        }
    }
}
