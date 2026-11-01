package com.letsign.mobile

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.letsign.mobile.data.local.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Quem já tinha o app (banco v1) abre a versão nova sem perder dados. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migra1Para2() {
        helper.createDatabase("migracao.db", 1).use {
            it.execSQL("INSERT INTO signers (userId, id, nome, email, telefone, criadoEm) VALUES ('u', 's1', 'João', 'j@x.com', NULL, NULL)")
        }
        helper.runMigrationsAndValidate("migracao.db", 2, true).use { db ->
            db.query("SELECT nome FROM signers").use { c ->
                c.moveToFirst()
                assertEquals("João", c.getString(0))
            }
            db.query("SELECT COUNT(*) FROM users").use { c ->
                c.moveToFirst()
                assertEquals(0, c.getInt(0))
            }
        }
    }

    /** v4: modelos antigos continuam, sem layout (é calculado no primeiro uso). */
    @Test
    fun migra3Para4() {
        helper.createDatabase("migracao4.db", 3).use {
            it.execSQL("INSERT INTO templates (id, adminId, nome, sourceDocumentId, criadoEm) VALUES ('t1', 'adm', 'NDA', 'd1', 'agora')")
        }
        helper.runMigrationsAndValidate("migracao4.db", 4, true).use { db ->
            db.query("SELECT nome, layoutJson FROM templates").use { c ->
                c.moveToFirst()
                assertEquals("NDA", c.getString(0))
                assertEquals(true, c.isNull(1))
            }
        }
    }

    /** v3: campos antigos viram "Assinatura" e contas antigas ficam sem CPF. */
    @Test
    fun migra2Para3() {
        helper.createDatabase("migracao3.db", 2).use {
            it.execSQL(
                "INSERT INTO signature_fields (userId, id, documentId, signerId, page, x, y, width, height, required, roleLabel) " +
                    "VALUES ('local', 'f1', 'd1', 's1', 1, 0.1, 0.1, 0.3, 0.08, 1, 'CONTRATADO')"
            )
            it.execSQL("INSERT INTO users (id, nome, email, telefone, role, adminId, senhaHash, criadoEm) VALUES ('s1', 'Ana', 'a@x.com', NULL, 'SIGNER', 'adm', 'h', 'agora')")
        }
        helper.runMigrationsAndValidate("migracao3.db", 3, true).use { db ->
            db.query("SELECT tipo FROM signature_fields WHERE id = 'f1'").use { c ->
                c.moveToFirst()
                assertEquals("ASSINATURA", c.getString(0))
            }
            db.query("SELECT cpf FROM users WHERE id = 's1'").use { c ->
                c.moveToFirst()
                assertEquals(true, c.isNull(0))
            }
            db.query("SELECT COUNT(*) FROM templates").use { c ->
                c.moveToFirst()
                assertEquals(0, c.getInt(0))
            }
        }
    }
}
