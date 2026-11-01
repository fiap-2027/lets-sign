package com.letsign.mobile

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.letsign.mobile.data.local.DemoData
import com.letsign.mobile.data.local.LocalDocumentRepository
import com.letsign.mobile.data.local.TokenManager
import com.letsign.mobile.data.local.db.AppDatabase
import com.letsign.mobile.data.pdf.PdfStamper
import com.letsign.mobile.data.pdf.PdfStorage
import com.letsign.mobile.data.pdf.PdfTextIndex
import com.letsign.mobile.domain.model.DocumentStatus
import com.letsign.mobile.domain.model.SignerStatus
import com.letsign.mobile.domain.rules.EsgImpact
import com.letsign.mobile.domain.rules.EsgLevel
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A demonstração cobre todos os estágios e dá dados para o painel ESG. */
@RunWith(AndroidJUnit4::class)
class DemoDataTest {
    @Test
    fun demonstracaoCobreTodosOsEstagios() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PDFBoxResourceLoader.init(context)
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val tokens = TokenManager(context).also { it.clearTokens() }
        val docs = LocalDocumentRepository(db, tokens, PdfStorage(context), PdfStamper(context), PdfTextIndex(context))
        DemoData(context, db, tokens, PdfStorage(context), PdfStamper(context), PdfTextIndex(context)).load().getOrThrow()

        assertEquals(DemoData.ADMIN_EMAIL, tokens.currentUser.value?.email)
        val lista = docs.observeDocuments().first()
        assertEquals(17, lista.size)
        assertEquals(DocumentStatus.entries.toSet(), lista.map { it.status }.toSet())
        val todos = lista.map { docs.refreshDocument(it.id).getOrThrow() }
        assertEquals(SignerStatus.entries.toSet(), todos.flatMap { d -> d.signers.map { it.status } }.toSet())
        assertTrue("concluídos têm PDF final", todos.filter { it.isCompleted }.all { it.hashFinal != null })
        val esg = EsgImpact.of(lista)
        assertTrue("folhas = ${esg.folhas}", esg.level == EsgLevel.ARVORE)

        // Segunda vez só entra, sem duplicar.
        tokens.clearTokens()
        DemoData(context, db, tokens, PdfStorage(context), PdfStamper(context), PdfTextIndex(context)).load().getOrThrow()
        assertEquals(17, docs.observeDocuments().first().size)
        tokens.clearTokens()
        db.close()
    }
}
