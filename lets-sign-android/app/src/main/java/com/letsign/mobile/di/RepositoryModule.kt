package com.letsign.mobile.di

import android.content.Context
import androidx.room.Room
import com.letsign.mobile.data.api.ApiClient
import com.letsign.mobile.data.api.AuthApi
import com.letsign.mobile.data.api.DocumentsApi
import com.letsign.mobile.data.api.SignersApi
import com.letsign.mobile.data.local.TokenManager
import com.letsign.mobile.data.local.db.AppDatabase
import com.letsign.mobile.data.pdf.DefaultPdfImporter
import com.letsign.mobile.data.pdf.PdfImporter
import com.letsign.mobile.data.pdf.PdfStorage
import com.letsign.mobile.data.repository.AuthRepositoryImpl
import com.letsign.mobile.data.repository.DocumentRepositoryImpl
import com.letsign.mobile.data.repository.LocalDataCleaner
import com.letsign.mobile.data.repository.SignerRepositoryImpl
import com.letsign.mobile.domain.repository.AuthRepository
import com.letsign.mobile.domain.repository.DocumentRepository
import com.letsign.mobile.domain.repository.SignerRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

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
        // Versão 1 do esquema: quando mudar, adicione uma Migration (esquemas exportados em app/schemas).
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides
    @Singleton
    fun provideAuthRepository(authApi: AuthApi, tokenManager: TokenManager, cleaner: LocalDataCleaner): AuthRepository =
        AuthRepositoryImpl(authApi, tokenManager, cleaner)

    @Provides
    @Singleton
    fun provideDocumentRepository(
        documentsApi: DocumentsApi,
        database: AppDatabase,
        tokenManager: TokenManager,
        pdfStorage: PdfStorage
    ): DocumentRepository =
        DocumentRepositoryImpl(documentsApi, database.documentDao(), database.pdfFileDao(), tokenManager, pdfStorage)

    @Provides
    fun providePdfImporter(impl: DefaultPdfImporter): PdfImporter = impl

    @Provides
    @Singleton
    fun provideSignerRepository(signersApi: SignersApi, database: AppDatabase, tokenManager: TokenManager): SignerRepository =
        SignerRepositoryImpl(signersApi, database.signerDao(), tokenManager)
}
