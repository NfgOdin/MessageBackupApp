package com.messagebackup.app

import android.app.Application
import com.messagebackup.app.ads.AdManager
import com.messagebackup.app.billing.BillingManager
import com.messagebackup.app.data.preferences.AppPreferences
import com.messagebackup.app.data.repository.MockTelephonyRepository
import com.messagebackup.app.data.repository.TelephonyRepository
import com.messagebackup.app.data.repository.TelephonyRepositoryImpl
import com.messagebackup.app.domain.export.ExportEngine

/**
 * Base Application class managing singleton services, preferences, and managers.
 */
class MessageBackupApp : Application() {

    lateinit var appPreferences: AppPreferences
        private set

    lateinit var realTelephonyRepository: TelephonyRepository
        private set

    lateinit var adManager: AdManager
        private set

    lateinit var billingManager: BillingManager
        private set

    lateinit var exportEngine: ExportEngine
        private set

    lateinit var googleDriveSyncManager: com.messagebackup.app.domain.cloud.GoogleDriveSyncManager
        private set

    lateinit var cloudStorageDispatcher: com.messagebackup.app.domain.cloud.CloudStorageDispatcher
        private set

    lateinit var localDatabase: com.messagebackup.app.data.local.LocalMessageDatabase
        private set

    lateinit var googleAuthManager: com.messagebackup.app.domain.auth.GoogleAuthManager
        private set

    val currentTelephonyRepository: TelephonyRepository
        get() = realTelephonyRepository

    override fun onCreate() {
        super.onCreate()
        instance = this

        appPreferences = AppPreferences(this)
        realTelephonyRepository = TelephonyRepositoryImpl(this)
        adManager = AdManager(this)
        billingManager = BillingManager(this)
        googleDriveSyncManager = com.messagebackup.app.domain.cloud.GoogleDriveSyncManager(this)
        cloudStorageDispatcher = com.messagebackup.app.domain.cloud.CloudStorageDispatcher(this)
        localDatabase = com.messagebackup.app.data.local.LocalMessageDatabase.getInstance(this)
        googleAuthManager = com.messagebackup.app.domain.auth.GoogleAuthManager(this, appPreferences)

        exportEngine = ExportEngine(
            context = this,
            telephonyRepository = realTelephonyRepository
        )
    }

    companion object {
        lateinit var instance: MessageBackupApp
            private set
    }
}
