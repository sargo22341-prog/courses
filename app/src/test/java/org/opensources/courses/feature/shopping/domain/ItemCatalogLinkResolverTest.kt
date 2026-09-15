package org.opensources.courses.feature.shopping.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.opensources.courses.feature.catalog.domain.CatalogProductRef
import org.opensources.courses.feature.catalog.domain.CatalogSource

class ItemCatalogLinkResolverTest {
    private val seedMilk = CatalogProductRef("seed:lait", "milk", CatalogSource.SEED)
    private val offMilk = CatalogProductRef("en:milks", "milks", CatalogSource.OPEN_FOOD_FACTS)
    private val customMilk = CatalogProductRef("custom:milk", "milk", CatalogSource.CUSTOM)
    private val customOther = CatalogProductRef("custom:other", "other", CatalogSource.CUSTOM)

    @Test
    fun `a catalog product named like the item wins over a custom one`() {
        assertEquals("en:milks", ItemCatalogLinkResolver.resolve(listOf(customMilk, offMilk), current = customOther))
    }

    @Test
    fun `the most faithful catalog name wins`() {
        assertEquals("seed:lait", ItemCatalogLinkResolver.resolve(listOf(seedMilk, offMilk), current = null))
    }

    @Test
    fun `without catalog name, an item keeps its catalog product whatever the language`() {
        assertEquals("seed:lait", ItemCatalogLinkResolver.resolve(listOf(customMilk), current = seedMilk))
    }

    @Test
    fun `without catalog product, a custom product named like the item, then the current custom link`() {
        assertEquals("custom:milk", ItemCatalogLinkResolver.resolve(listOf(customMilk), current = customOther))
        assertEquals("custom:other", ItemCatalogLinkResolver.resolve(emptyList(), current = customOther))
    }

    @Test
    fun `nothing fits`() {
        assertNull(ItemCatalogLinkResolver.resolve(emptyList(), current = null))
    }
}
