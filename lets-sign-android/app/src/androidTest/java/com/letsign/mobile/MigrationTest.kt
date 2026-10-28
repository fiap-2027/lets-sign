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
}
