package com.xiaojiwei.fbng

import android.app.Application
import com.xiaojiwei.fbng.update.AppUpdate

class FbngApplication : Application() {
    internal val appUpdate by lazy { AppUpdate(this) }
}
