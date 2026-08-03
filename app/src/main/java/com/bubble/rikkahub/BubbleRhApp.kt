package com.bubble.rikkahub

import android.app.Application
import com.bubble.rikkahub.di.AppContainer

/** Application-level holder for the DI container so Activities and the sync service share it. */
class BubbleRhApp : Application() {

    val container: AppContainer by lazy { AppContainer(this) }
}
