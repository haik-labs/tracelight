package com.haiklabs.tracelight

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.initialize

class TraceLightApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Firebase.initialize(this)
        // Firebase AI attaches an App Check token to every request. Debug builds use the
        // debug provider (the token is printed to logcat on first run and must be added to
        // the Firebase console allow list); release builds use Play Integrity.
        Firebase.appCheck.installAppCheckProviderFactory(
            if (BuildConfig.DEBUG) DebugAppCheckProviderFactory.getInstance()
            else PlayIntegrityAppCheckProviderFactory.getInstance()
        )
    }
}
