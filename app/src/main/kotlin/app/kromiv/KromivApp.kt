package app.kromiv

import android.app.Application
import app.kromiv.di.AppContainer
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader

class KromivApp : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return container.getImageLoader()
    }
}
