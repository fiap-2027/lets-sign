package com.letsign.mobile.di

import android.content.Context
import com.letsign.mobile.BuildConfig
import androidx.room.Room
import com.letsign.mobile.data.api.ApiClient
import com.letsign.mobile.data.api.AuthApi
import com.letsign.mobile.data.api.DocumentsApi
import com.letsign.mobile.data.api.SignersApi
import com.letsign.mobile.data.local.LocalAuthRepository
import com.letsign.mobile.data.local.LocalDocumentRepository
import com.letsign.mobile.data.local.LocalSignerRepository
import com.letsign.mobile.data.local.TokenManager
import com.letsign.mobile.data.local.db.AppDatabase
import com.letsign.mobile.data.pdf.DefaultPdfImporter
import com.letsign.mobile.data.pdf.PdfImporter
import com.letsign.mobile.data.pdf.PdfStamper
import com.letsign.mobile.data.pdf.PdfStorage
import com.letsign.mobile.data.repository.AuthRepositoryImpl
import com.letsign.mobile.data.repository.DocumentRepositoryImpl
import com.letsign.mobile.data.repository.LocalDataCleaner
import com.letsign.mobile.data.repository.SignerRepositoryImpl
import com.letsign.mobile.domain.repository.AuthRepository
import com.letsign.mobile.domain.repository.DocumentRepository
import com.letsign.mobile.domain.repository.SignerRepository
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

// BuildConfig.USE_API escolhe a implementação (ver app/build.gradle.kts):
// false (padrão) = tudo no aparelho (Local*Repository); true = backend REST com o Room como cache.
// As APIs entram como Lazy: no modo local o Retrofit nem é criado.
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideRetrofit(tokenManager: TokenManager): Retrofit = ApiClient.createRetrofit(tokenManager)

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideDocumentsApi(retrofit: Retrofit): DocumentsApi = retrofit.create(DocumentsApi::class.java)

    @Provides
    @Singleton
    fun provideSignersApi(retrofit: Retrofit): SignersApi = retrofit.create(SignersApi::class.java)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        // Migrações declaradas em AppDatabase (esquemas exportados em app/schemas).
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides
    @Singleton
    fun provideAuthRepository(authApi: Lazy<AuthApi>, database: AppDatabase, tokenManager: TokenManager, cleaner: LocalDataCleaner): AuthRepository =
        if (BuildConfig.USE_API) AuthRepositoryImpl(authApi.get(), tokenManager, cleaner)
        else LocalAuthRepository(database.userDao(), tokenManager)

    @Provides
    @Singleton
    fun provideDocumentRepository(
        documentsApi: Lazy<DocumentsApi>,
        database: AppDatabase,
        tokenManager: TokenManager,
        pdfStorage: PdfStorage,
        stamper: PdfStamper
    ): DocumentRepository =
        if (BuildConfig.USE_API) DocumentRepositoryImpl(documentsApi.get(), database.documentDao(), database.pdfFileDao(), tokenManager, pdfStorage)
        else LocalDocumentRepository(database, tokenManager, pdfStorage, stamper)

    @Provides
    fun providePdfImporter(impl: DefaultPdfImporter): PdfImporter = impl

    @Provides
    @Singleton
    fun provideSignerRepository(signersApi: Lazy<SignersApi>, database: AppDatabase, tokenManager: TokenManager): SignerRepository =
        if (BuildConfig.USE_API) SignerRepositoryImpl(signersApi.get(), database.signerDao(), tokenManager)
        else LocalSignerRepository(database, tokenManager)
}
