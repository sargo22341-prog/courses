package org.opensources.courses.core.network

import okhttp3.Interceptor
import okhttp3.Response
import org.opensources.courses.BuildConfig

/**
 * OpenFoodFacts asks every client to identify itself (`app_name/app_version (contact)`).
 * No personal data is sent: only the application name and version.
 */
class UserAgentInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(
            chain
                .request()
                .newBuilder()
                .header("User-Agent", USER_AGENT)
                .build(),
        )

    private companion object {
        val USER_AGENT = "courses/${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})"
    }
}
