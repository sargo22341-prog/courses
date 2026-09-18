package org.opensources.courses.feature.shopping.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuantityStepperTest {
    @Test
    fun `a counted product goes one by one and never below one`() {
        assertEquals(3.0, QuantityStepper.increase(2.0, null), 0.0)
        assertEquals(1.0, QuantityStepper.decrease(2.0, null) ?: -1.0, 0.0)
        assertNull(QuantityStepper.decrease(1.0, null))
    }

    @Test
    fun `a measured product goes by a step that suits its unit`() {
        assertEquals(600.0, QuantityStepper.increase(500.0, "g"), 0.0)
        assertEquals(2.0, QuantityStepper.increase(1.5, "kg"), 0.0)
        assertEquals(1.0, QuantityStepper.decrease(1.5, "L") ?: -1.0, 0.0)
        assertEquals(35.0, QuantityStepper.increase(25.0, "cl"), 0.0)
        assertEquals(3.0, QuantityStepper.increase(2.0, "boîtes"), 0.0)
    }

    @Test
    fun `a step that would leave nothing is refused`() {
        assertNull(QuantityStepper.decrease(50.0, "g"))
        assertNull(QuantityStepper.decrease(0.5, "kg"))
    }

    @Test
    fun `decimal steps do not drift`() {
        assertEquals(1.3, QuantityStepper.increase(0.8, "kg"), 0.0)
        assertEquals(0.7, QuantityStepper.decrease(1.2, "kg") ?: -1.0, 0.0)
    }

    @Test
    fun `minus is offered only while it leaves something`() {
        val item = ShoppingItem("i", "l", "Pain", 1.0, null, isChecked = false, catalogProductId = null)
        assertFalse(item.canDecreaseQuantity)
        assertTrue(item.copy(quantity = 2.0).canDecreaseQuantity)
    }
}
