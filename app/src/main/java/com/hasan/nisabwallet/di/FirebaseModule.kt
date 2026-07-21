package com.hasan.nisabwallet.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore {
        val db = FirebaseFirestore.getInstance()
        
        // Explicitly configure offline persistence
        val settings = firestoreSettings {
            setLocalCacheSettings(persistentCacheSettings {
                // Optionally set cache size (default is 100 MB)
                // setSizeBytesBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
            })
        }
        db.firestoreSettings = settings
        
        return db
    }
}