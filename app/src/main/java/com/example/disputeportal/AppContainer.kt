package com.example.disputeportal

import android.content.Context
import androidx.room.Room
import com.example.disputeportal.data.DisputeRepository
import com.example.disputeportal.data.local.AppDatabase
import com.example.disputeportal.data.remote.buildApi
import com.example.disputeportal.session.Session

class AppContainer(context: Context) {
    val session = Session()

    private val database: AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "dispute-portal.db").build()

    val repository = DisputeRepository(
        api = buildApi(session, BuildConfig.API_BASE_URL),
        db = database,
    )
}
