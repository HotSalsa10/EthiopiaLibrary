package com.ethiopialibrary.app

import android.app.Application
import com.ethiopialibrary.app.data.LibraryDatabase
import com.ethiopialibrary.app.data.LibraryRepository
import java.time.Clock

class LibraryApp : Application() {

    val database: LibraryDatabase by lazy { LibraryDatabase.create(this) }

    val repository: LibraryRepository by lazy {
        LibraryRepository(database, Clock.systemDefaultZone())
    }
}
