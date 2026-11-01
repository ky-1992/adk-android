package com.google.adk.demo.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.adk.session.SessionStore
import com.google.adk.session.sqlite.SqliteSessionStore

object AppModule {
    private var _sessionStore: SessionStore? = null

    fun sessionStore(context: Context): SessionStore {
        return _sessionStore ?: SqliteSessionStore(context.applicationContext).also { _sessionStore = it }
    }
}

inline fun <reified T : ViewModel> viewModelFactory(crossinline creator: () -> T): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <V : ViewModel> create(modelClass: Class<V>): V = creator() as V
    }
}