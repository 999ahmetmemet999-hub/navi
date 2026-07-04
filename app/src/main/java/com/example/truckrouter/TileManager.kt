package com.example.truckrouter

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Türkiye çevrimdışı rota verisini (valhalla_tiles.tar) GitHub Release'ten indirir.
 * - Boş alan kontrolü yapar
 * - .part dosyasına indirir, bitince atomik olarak yerine taşır
 * - İptal edilebilir (coroutine cancel)
 */
class TileManager(private val context: Context) {

    private val target: File get() = OfflineRouter.tilesFile(context)
    private val part: File get() = File(context.filesDir, "valhalla_tiles.tar.part")

    fun isReady(): Boolean = target.exists() && target.length() > 10L * 1024 * 1024

    suspend fun download(url: String, onProgress: (downloaded: Long, total: Long) -> Unit) =
        withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build()

            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IOException(
                        "İndirme adresi ${resp.code} döndürdü.\n\n" +
                            "Kontrol: (1) GitHub'da \"1 - Turkiye verisi uret\" workflow'u " +
                            "başarıyla bitti mi? (2) Repo herkese açık (public) mı?"
                    )
                }
                val body = resp.body ?: throw IOException("Boş yanıt")
                val total = body.contentLength()

                if (total > 0) {
                    val free = context.filesDir.usableSpace
                    if (free < total + 200L * 1024 * 1024) {
                        val needGb = (total + 200L * 1024 * 1024) / 1_073_741_824.0
                        throw IOException(
                            "Depolama yetersiz: yaklaşık %.1f GB boş alan gerekli.".format(needGb)
                        )
                    }
                }

                part.delete()
                body.byteStream().use { input ->
                    part.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var lastCb = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n == -1) break
                            output.write(buf, 0, n)
                            done += n
                            if (done - lastCb > 3L * 1024 * 1024) {
                                lastCb = done
                                onProgress(done, total)
                            }
                        }
                        onProgress(done, total)
                    }
                }

                target.delete()
                if (!part.renameTo(target)) throw IOException("İndirilen dosya yerine taşınamadı")
            }
        }
}
