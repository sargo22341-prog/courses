package org.opensources.courses.core.network

import android.content.res.XmlResourceParser
import android.security.NetworkSecurityPolicy
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.courses.R
import java.security.KeyStore
import java.security.cert.X509Certificate

/**
 * Self-hosted Home Assistant servers (https://ha.nas.home) are often signed by a private CA the
 * user installed in Android settings. These tests check that the application really trusts such
 * user CAs, on the HTTP client actually used by the app.
 */
@RunWith(AndroidJUnit4::class)
class NetworkSecurityConfigTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun baseConfigTrustsSystemAndUserCertificatesWithoutDomainOverride() {
        val anchors = mutableListOf<String>()
        var domainConfigs = 0
        var inBaseConfig = false
        val parser: XmlResourceParser = context.resources.getXml(R.xml.network_security_config)
        parser.use {
            while (it.next() != XmlResourceParser.END_DOCUMENT) {
                when {
                    it.eventType == XmlResourceParser.START_TAG && it.name == "base-config" -> inBaseConfig = true
                    it.eventType == XmlResourceParser.END_TAG && it.name == "base-config" -> inBaseConfig = false
                    it.eventType == XmlResourceParser.START_TAG && it.name == "domain-config" -> domainConfigs++
                    it.eventType == XmlResourceParser.START_TAG && it.name == "certificates" && inBaseConfig ->
                        anchors += it.getAttributeValue(null, "src")
                }
            }
        }
        assertEquals(setOf("system", "user"), anchors.toSet())
        // A domain-config could silently drop user CAs for some hosts.
        assertEquals(0, domainConfigs)
    }

    @Test
    fun localHostnamesMayUsePlainHttp() {
        assertTrue(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("ha.nas.home"))
    }

    /**
     * Runs for real only on a device with at least one user-installed CA (Settings → Security →
     * Encryption & credentials → Install a certificate → CA certificate); skipped otherwise.
     */
    @Test
    fun appHttpClientTrustsEveryUserInstalledCertificateAuthority() {
        val store = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
        val userCertificates =
            store
                .aliases()
                .toList()
                .filter { it.startsWith("user:") }
                .map { store.getCertificate(it) as X509Certificate }
        assumeTrue("No user-installed CA on this device", userCertificates.isNotEmpty())

        val trustManager = NetworkModule.okHttpClient().x509TrustManager
        assertNotNull(trustManager)
        val accepted = trustManager!!.acceptedIssuers.toSet()
        userCertificates.forEach { certificate ->
            assertTrue("User CA not trusted: ${certificate.subjectX500Principal}", certificate in accepted)
        }
    }
}
