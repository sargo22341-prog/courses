package org.opensources.courses.core.network

import kotlinx.serialization.Serializable
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.courses.feature.catalog.data.remote.OpenFoodFactsApi
import org.opensources.courses.feature.homeassistant.data.remote.ApiStatusDto
import org.opensources.courses.feature.homeassistant.data.remote.HomeAssistantApi
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import kotlin.coroutines.Continuation

/**
 * Release builds only: R8 removed a response class the app never reads, Retrofit then saw `Object`
 * ("Unable to create converter") and the Home Assistant connection test failed. R8 cannot run in a
 * unit test, so this guards the keep rule of `proguard-rules.pro` and that every response class of
 * the Retrofit interfaces is covered by it.
 */
class RetrofitKeepRulesTest {
    private val retrofitInterfaces = listOf(HomeAssistantApi::class.java, OpenFoodFactsApi::class.java)

    @Test
    fun `release keep rule for network DTOs is present`() {
        val rules = File("proguard-rules.pro").readLines().map { it.trim() }

        assertTrue(KEEP_RULE in rules)
    }

    @Test
    fun `every app response class of the Retrofit interfaces is covered by the keep rule`() {
        val appClasses =
            retrofitInterfaces
                .flatMap { api -> api.declaredMethods.flatMap { classesIn(responseType(it)) } }
                .filter { it.name.startsWith(APP_PACKAGE) }
                .distinct()

        assertTrue(ApiStatusDto::class.java in appClasses)
        appClasses.forEach { type ->
            assertTrue("$type must be @Serializable", type.isAnnotationPresent(Serializable::class.java))
            assertTrue("$type must live in a data.remote package", ".data.remote" in type.packageName)
        }
    }

    /** A suspend method returns Object: its real type is the lower bound of `Continuation<? super T>`. */
    private fun responseType(method: Method): Type {
        val continuation = method.genericParameterTypes.lastOrNull() as? ParameterizedType
        if (continuation?.rawType != Continuation::class.java) return method.genericReturnType
        return when (val argument = continuation.actualTypeArguments.single()) {
            is WildcardType -> argument.lowerBounds.single()
            else -> argument
        }
    }

    private fun classesIn(type: Type): List<Class<*>> =
        when (type) {
            is Class<*> -> listOf(type)
            is ParameterizedType -> classesIn(type.rawType) + type.actualTypeArguments.flatMap(::classesIn)
            is WildcardType -> (type.lowerBounds + type.upperBounds).flatMap(::classesIn)
            else -> emptyList()
        }

    private companion object {
        const val APP_PACKAGE = "org.opensources.courses."
        const val KEEP_RULE = "-keep,allowobfuscation @kotlinx.serialization.Serializable class org.opensources.courses.**.data.remote.**"
    }
}
