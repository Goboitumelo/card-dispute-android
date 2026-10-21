package com.example.disputeportal

import android.app.Application

class DisputeApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
