package com.perdolique.poleparkla

import android.content.Context
import androidx.room.Room
import com.perdolique.poleparkla.data.ReportRepository
import com.perdolique.poleparkla.data.SecureTokenStore
import com.perdolique.poleparkla.data.SettingsRepository
import com.perdolique.poleparkla.data.local.PoleParklaDatabase
import com.perdolique.poleparkla.domain.EmailTemplateRenderer
import com.perdolique.poleparkla.service.CloudRecognitionService
import com.perdolique.poleparkla.service.EmailLauncher
import com.perdolique.poleparkla.service.LocationService
import com.perdolique.poleparkla.service.InAksAddressService
import com.perdolique.poleparkla.service.PlateRecognitionService
import com.perdolique.poleparkla.service.PlayReviewLauncher
import com.perdolique.poleparkla.service.PhotoStore
import com.perdolique.poleparkla.service.TextRecognitionService
import java.io.File
import org.maplibre.compose.offline.getOfflineManager

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val reportsDirectory = File(appContext.filesDir, "reports").apply { mkdirs() }
    private val offlineMapManager by lazy { getOfflineManager(appContext) }

    private val database = Room.databaseBuilder(
        appContext,
        PoleParklaDatabase::class.java,
        "pole-parkla.db",
    ).build()

    val settingsRepository = SettingsRepository(appContext)
    val secureTokenStore = SecureTokenStore(appContext)
    val reportRepository = ReportRepository(database, reportsDirectory)
    val emailTemplateRenderer = EmailTemplateRenderer()
    val photoStore = PhotoStore(appContext, reportsDirectory)
    val locationService = LocationService(appContext)
    val addressResolver = InAksAddressService()
    val textRecognitionService = TextRecognitionService(photoStore)
    val plateRecognitionService = PlateRecognitionService(appContext, photoStore)
    val cloudRecognitionService = CloudRecognitionService()
    val emailLauncher = EmailLauncher(appContext)
    val playReviewLauncher = PlayReviewLauncher(appContext)

    suspend fun clearMapCache() {
        offlineMapManager.clearAmbientCache()
    }
}
