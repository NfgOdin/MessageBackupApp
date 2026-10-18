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

    lateinit var mockTelephonyRepository: TelephonyRepository
        private set

    lateinit var adManager: AdManager
        private set

    lateinit var billingManager: BillingManager
        private set

    lateinit var exportEngine: ExportEngine
        private set

    lateinit var googleDriveSyncManager: com.messagebackup.app.domain.cloud.GoogleDriveSyncManager
        private set

    var useMockData: Boolean = false

    val currentTelephonyRepository: TelephonyRepository
        get() = if (useMockData) mockTelephonyRepository else realTelephonyRepository

    override fun onCreate() {
        super.onCreate()
        instance = this

        appPreferences = AppPreferences(this)
        realTelephonyRepository = TelephonyRepositoryImpl(this)
        mockTelephonyRepository = MockTelephonyRepository()
        adManager = AdManager(this)
        billingManager = BillingManager(this)
        googleDriveSyncManager = com.messagebackup.app.domain.cloud.GoogleDriveSyncManager(this)

        exportEngine = ExportEngine(
            context = this,
            telephonyRepository = realTelephonyRepository
        )
    }

    fun updateExportEngineRepository(repo: TelephonyRepository) {
        exportEngine = ExportEngine(
            context = this,
            telephonyRepository = repo
        )
    }

    companion object {
        lateinit var instance: MessageBackupApp
            private set
    }
}
