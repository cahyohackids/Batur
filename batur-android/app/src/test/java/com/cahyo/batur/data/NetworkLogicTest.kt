package com.cahyo.batur.data

import com.cahyo.batur.data.prefs.SettingsStore.Companion.normalizeBaseUrl
import com.cahyo.batur.data.remote.ApiException
import com.cahyo.batur.data.remote.isRetryableFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkLogicTest {

    @Test
    fun tanpaSkemaDianggapHttps() {
        assertEquals("https://batur.ezkielyna.store/", normalizeBaseUrl("batur.ezkielyna.store"))
        assertEquals("https://batur.ezkielyna.store/", normalizeBaseUrl("  batur.ezkielyna.store/api/ "))
        assertEquals("https://example.com:8443/", normalizeBaseUrl("example.com:8443"))
    }

    @Test
    fun alamatLokalTetapHttp() {
        assertEquals("http://10.0.2.2:3000/", normalizeBaseUrl("10.0.2.2:3000"))
        assertEquals("http://192.168.1.5:3000/", normalizeBaseUrl("192.168.1.5:3000/api"))
        assertEquals("http://172.20.10.2:3000/", normalizeBaseUrl("172.20.10.2:3000"))
        assertEquals("http://localhost:3000/", normalizeBaseUrl("localhost:3000"))
        assertEquals("http://127.0.0.1:3000/", normalizeBaseUrl("127.0.0.1:3000"))
        // 172.32.x bukan rentang privat.
        assertEquals("https://172.32.0.1/", normalizeBaseUrl("172.32.0.1"))
    }

    @Test
    fun skemaYangDitulisDipertahankan() {
        assertEquals("http://batur.example.com/", normalizeBaseUrl("http://batur.example.com"))
        assertEquals("https://10.0.2.2:3000/", normalizeBaseUrl("HTTPS://10.0.2.2:3000/api/"))
    }

    @Test
    fun klasifikasiErrorYangBisaDicobaUlang() {
        assertTrue(isRetryableFailure(offline = true, httpCode = null, parseError = false))
        assertTrue(isRetryableFailure(offline = false, httpCode = null, parseError = true))
        listOf(500, 502, 503, 408, 429, 401).forEach {
            assertTrue("HTTP $it", isRetryableFailure(offline = false, httpCode = it, parseError = false))
        }
        listOf(400, 403, 404, 409, 413, 422).forEach {
            assertFalse("HTTP $it", isRetryableFailure(offline = false, httpCode = it, parseError = false))
        }
        assertFalse(isRetryableFailure(offline = false, httpCode = null, parseError = false))
    }

    @Test
    fun apiExceptionMenghitungRetryable() {
        assertTrue(ApiException("kuota", httpCode = 429, code = "quota_exceeded").retryable)
        assertFalse(ApiException("validasi", httpCode = 400, code = "validation").retryable)
        assertTrue(ApiException("offline", offline = true).retryable)
        assertTrue(ApiException("token", httpCode = 401).unauthorized)
    }
}
