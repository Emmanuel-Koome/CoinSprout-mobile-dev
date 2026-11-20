package com.coinsprout.app.di

import android.content.Context
import androidx.room.Room
import com.coinsprout.app.data.local.CoinSproutDatabase
import com.coinsprout.app.data.prefs.SessionStore
import com.coinsprout.app.data.remote.ApiClient
import com.coinsprout.app.data.repository.CoinSproutRepository

/**
 * Tiny hand-rolled DI so the receiver, the WorkManager worker and the ViewModels all share ONE repository
 * (and therefore one database and one session). Hilt would be the next step for a larger team.
 */
object ServiceLocator {
    @Volatile private var session: SessionStore? = null
    @Volatile private var repository: CoinSproutRepository? = null

    fun session(context: Context): SessionStore =
        session ?: synchronized(this) { session ?: SessionStore(context.applicationContext).also { session = it } }

    fun repository(context: Context): CoinSproutRepository =
        repository ?: synchronized(this) {
            repository ?: run {
                val app = context.applicationContext
                val s = session(app)
                val db = Room.databaseBuilder(app, CoinSproutDatabase::class.java, "coinsprout.db")
                    .fallbackToDestructiveMigration() // prototype only: write real Migrations before shipping
                    .build()
                CoinSproutRepository(db, ApiClient(s).api, s)
            }.also { repository = it }
        }
}
