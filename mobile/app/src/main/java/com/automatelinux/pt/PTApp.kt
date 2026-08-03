package com.automatelinux.pt

import android.app.Application
import com.automatelinux.pt.di.androidPlatformModule
import com.automatelinux.pt.di.sharedModule
import com.automatelinux.pt.util.ServerConfig
import dagger.hilt.android.HiltAndroidApp
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

@HiltAndroidApp
class PTApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // ServerConfig lives in :shared, which cannot see this module's generated
        // BuildConfig, so the flavour-dependent values are handed over here — before
        // anything can inject PtApi and ask which server is active.
        ServerConfig.configure(
            port = BuildConfig.SERVER_PORT,
            publicServer = if (BuildConfig.FEEDBACK_ENABLED) {
                "https://pt.dev.ya-niv.com"
            } else {
                "https://pt.prod.ya-niv.com"
            },
            peerServersEnabled = BuildConfig.PEER_SERVERS_ENABLED
        )

        // Koin owns the shared graph and must be up before Hilt resolves anything from
        // it. Hilt's own initialisation happens lazily on first injection, which is
        // always after onCreate, so ordering here is sufficient.
        startKoin {
            androidContext(this@PTApp)
            modules(sharedModule, androidPlatformModule)
        }
    }
}
