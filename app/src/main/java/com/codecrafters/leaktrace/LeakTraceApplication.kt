package com.codecrafters.leaktrace

import android.app.Application
import com.codecrafters.leaktrace.di.AppContainer

class LeakTraceApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
