package com.perdolique.poleparkla.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureTokenStoreTest {
    private lateinit var store: SecureTokenStore

    @Before
    fun setUp() {
        store = SecureTokenStore(ApplicationProvider.getApplicationContext<Context>())
        store.clear()
    }

    @After
    fun tearDown() {
        store.clear()
    }

    @Test
    fun tokenCanOnlyBeReadForTheOriginItWasSavedFor() {
        store.save("secret-token", "https://worker.example/base")

        assertEquals("secret-token", store.read("https://worker.example/other-path"))
        assertEquals("", store.read("https://different.example"))
        assertEquals("", store.read("http://worker.example"))
    }
}
