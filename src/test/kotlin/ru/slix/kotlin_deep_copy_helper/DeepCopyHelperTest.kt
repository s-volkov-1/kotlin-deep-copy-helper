package ru.slix.kotlin_deep_copy_helper

import com.fasterxml.jackson.databind.exc.InvalidFormatException
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

internal class DeepCopyHelperTest {

    @Nested
    inner class Replace {
        @Test
        fun `replace top-level property in DTO`() {
            val product = Product("pid", 20)

            val productCopy = product.deepCopy("price", 10)

            productCopy.price shouldBe 10
        }

        @Test
        fun `replace top-level property in map`() {
            val product: Map<String, Any> = mapOf("id" to "pid", "price" to 20)

            val productCopy = product.deepCopy("price", 10)

            productCopy["price"] shouldBe 10
        }

        @Test
        fun `replace top-level array element in DTO`() {
            val products = listOf(
                Product("pid-1", 10),
                Product("pid-2", 20),
            )
            val newProduct = Product("pid-3", 30)

            val productsCopy = products.deepCopy("1", newProduct)

            productsCopy shouldHaveSize 2
            productsCopy[1] shouldBe newProduct
        }

        @Test
        fun `clear array orders(1)_products in DTO`() {
            val orders = listOf(
                Order(
                    id = "oid-2",
                    products = listOf(
                        Product("pid-1", 10),
                        Product("pid-2", 20)
                    )
                )
            )

            val ordersCopy = orders.deepCopy("0/products", listOf<Product>())

            ordersCopy[0].products shouldHaveSize 0
        }

        @Test
        fun `replace nested property orders(1)_products(0)_id in DTO`() {
            val orders = listOf(
                Order(
                    id = "oid-1",
                    products = listOf()
                ),
                Order(
                    id = "oid-2",
                    products = listOf(
                        Product("pid-1", 10),
                        Product("pid-2", 20)
                    )
                ),
                Order(
                    id = "oid-3",
                    products = listOf(
                        Product("pid-x", 99)
                    )
                )
            )

            val ordersCopy = orders.deepCopy("1/products/0/id", "ZZZ")

            ordersCopy shouldHaveSize 3
            ordersCopy[1].products shouldHaveSize 2
            ordersCopy[1].products[0].id shouldBe "ZZZ"
        }

        @Test
        fun `replace value in nested list`() {
            val values = listOf(listOf("X"))

            val valuesCopy = values.deepCopy("0/0", "Y")

            valuesCopy shouldBe listOf(listOf("Y"))
        }

        @Test
        fun `replace value in array with null`() {
            val values = listOf(null, "A")

            val valuesCopy = values.deepCopy("1", null)

            valuesCopy shouldBe listOf(null, null)
        }

        @Test
        fun `demo - compare deepCopy with native copy method`() {
            val order = Order(
                id = "oid-2",
                products = listOf(
                    Product("pid-1", 10),
                    Product("pid-2", 20)
                )
            )
            // imagine how bad this will look in case we deal with list inside list, like in one test above
            val nativeOrderCopy = order.copy(
                products = order.products.toMutableList().apply {
                    set(0, order.products[0].copy(id = "ZZZ"))
                }
            )

            val orderCopy = order.deepCopy("products/0/id", "ZZZ")

            orderCopy shouldBe nativeOrderCopy
        }
    }

    @Nested
    inner class Append {
        @Test
        fun `add element to array of DTOs`() {
            val order1 = Order(id = "oid-1", products = listOf())
            val order2 = Order(id = "oid-0", products = listOf())

            val ordersCopy = listOf(order1).deepCopy("1", order2, ArrayModificationMode.INSERT_APPEND)

            ordersCopy shouldBe listOf(order1, order2)
        }
    }

    @Nested
    inner class Insert {
        @Test
        fun `insert element to array of DTOs`() {
            val order1 = Order(id = "oid-1", products = listOf())
            val order0 = Order(id = "oid-0", products = listOf())

            val ordersCopy = listOf(order1).deepCopy("0", order0, ArrayModificationMode.INSERT_APPEND)

            ordersCopy shouldBe listOf(order0, order1)
        }
    }

    @Nested
    inner class Remove {
        @Test
        fun `remove element from array of DTOs`() {
            val orders = listOf(
                Order(
                    id = "oid-1",
                    products = listOf(
                        Product("pid-1", 10)
                    )
                )
            )

            val ordersCopy = orders.deepCopy("0/products/0", null, ArrayModificationMode.REMOVE)

            ordersCopy shouldHaveSize 1
            ordersCopy[0].products shouldHaveSize 0
        }
    }

    @Nested
    inner class MixedOperations {
        @Test
        fun `replace & append & remove`() {
            val products = listOf(
                Product("pid-1", 10),
                Product("pid-2", 20),
            )
            val newProduct = Product("pid-3", 30)

            val productsCopy = products
                .deepCopy("0/price", 999)
                .deepCopy("0/id", "pid-x")
                .deepCopy("2", newProduct, ArrayModificationMode.INSERT_APPEND)
                .deepCopy("1", null, ArrayModificationMode.REMOVE)

            productsCopy shouldBe listOf(
                Product("pid-x", 999),
                newProduct
            )
        }
    }

    @Nested
    inner class ComplexFieldTypes {
        @Test
        fun `date-time fields preserve format`() {
            val dtv = DateTimeValues(
                id = "X",
                localDateTime = LocalDateTime.parse("2021-04-09T10:11:12.123456"),
                offsetDateTime = OffsetDateTime.parse("2021-04-09T10:11:12.123456+07:00"),
                zonedDateTime = ZonedDateTime.parse("2021-04-09T10:11:12.123456+03:00[Europe/Moscow]"),
                zoneOffset = ZoneOffset.ofHours(7)
            )

            val dtvCopy = dtv.deepCopy("id", "Y")

            dtvCopy shouldBe dtv.copy(id = "Y")
        }

        @ParameterizedTest
        @ValueSource(strings = [ "10", "10.1", "0.1", "0", "-10.1" ])
        fun `big decimal preserves string format`(value: String) {
            val bdv = BigDecimalValue("Y", BigDecimal(value))

            val bdvCopy = bdv.deepCopy("id", "Y")

            bdvCopy shouldBe bdv.copy(id = "Y")
        }

        @ParameterizedTest
        @ValueSource(ints = [ 10, 0, -10 ])
        fun `big decimal preserves int format`(value: Int) {
            val bdv = BigDecimalValue("Y", BigDecimal(value))

            val bdvCopy = bdv.deepCopy("id", "Y")

            bdvCopy shouldBe bdv.copy(id = "Y")
        }

        @ParameterizedTest
        @ValueSource(doubles = [ 10.0, 10.1, 0.0, 0.1, -10.1 ])
        fun `big decimal preserves double format`(value: Double) {
            val bdv = BigDecimalValue("Y", BigDecimal(value))

            val bdvCopy = bdv.deepCopy("id", "Y")

            bdvCopy shouldBe bdv.copy(id = "Y")
        }
    }

    @Nested
    inner class Errors {
        private val product = Product("pid", 20)

        @ParameterizedTest
        @ValueSource(strings = [ "", "/", "/price", "price/", "price//0" ])
        fun `when empty parts in propertyPath - then IllegalArgumentException`(path: String) {
            shouldThrow<IllegalArgumentException> {
                product.deepCopy(path, 10)
            }.also {
                it.message shouldBe "propertyPath must not contain empty parts"
            }
        }

        @ParameterizedTest
        @ValueSource(strings = [ "product-price", "product*price" ])
        fun `when bad chars in propertyPath - then IllegalArgumentException`(path: String) {
            shouldThrow<IllegalArgumentException> {
                product.deepCopy(path, 10)
            }.also {
                it.message shouldBe "propertyPath must contain only [A-Za-z0-9] chars"
            }
        }

        /** This may not throw exceptions if you configure ObjectMapper to ignore unknown properties */
        @Test
        fun `when non-existing property name - then UnrecognizedPropertyException`() {
            shouldThrow<UnrecognizedPropertyException> {
                product.deepCopy("unknown", 10)
            }.also {
                it.message shouldContain "Unrecognized field \"unknown\""
            }
        }

        @Test
        fun `when non-existing propertyPath 1 - then IllegalStateException`() {
            shouldThrow<IllegalStateException> {
                product.deepCopy("0", 10)
            }.also {
                it.message shouldBe "Bad propertyPath. Expected property name at the end."
            }
        }

        @Test
        fun `when non-existing propertyPath 2 - then IllegalStateException`() {
            shouldThrow<IllegalStateException> {
                product.deepCopy("0/bad", 10)
            }.also {
                it.message shouldBe "Bad index in propertyPath"
            }
        }

        @Test
        fun `when bad property value type - then InvalidFormatException`() {
            shouldThrow<InvalidFormatException> {
                product.deepCopy("price", "bad value type")
            }.also {
                it.message shouldContain "Cannot deserialize value of type `int` from String"
            }
        }

        @Test
        fun `when first index out of bounds - then IndexOutOfBoundsException`() {
            shouldThrow<IndexOutOfBoundsException> {
                listOf("X").deepCopy("2", "Y")
            }.also {
                it.message shouldBe "Can't set/add/insert element at index 2. Check propertyPath."
            }
        }

        @Test
        fun `when second index out of bounds - then IllegalStateException`() {
            shouldThrow<IllegalStateException> {
                listOf(listOf("X")).deepCopy("1/0", "Y")
            }.also {
                it.message shouldBe "Bad index in propertyPath"
            }
        }
    }

    private data class Order(
        val id: String,
        val products: List<Product>
    )

    private data class Product(
        val id: String,
        val price: Int
    )

    private data class BigDecimalValue(
        val id: String,
        val bigDecimal: BigDecimal,
    )

    private data class DateTimeValues(
        val id: String,
        val localDateTime: LocalDateTime,
        val offsetDateTime: OffsetDateTime,
        val zonedDateTime: ZonedDateTime,
        val zoneOffset: ZoneOffset
    )

}
