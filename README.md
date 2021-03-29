# Kotlin Deep Copy Helper

### Motivation

Normally in kotlin, we strive to use immutable properties and collections wherever possible.  
However, for test scenarios, we often want to mutate data from a normal test case, break it or modify in some particular way.  
Standard `.copy()` method on kotlin data-classes offers poor usability on nested properties. 

Check out example from [Arrow/Optics/Lens documentation](https://arrow-kt.io/docs/optics/lens/):

```kotlin
data class Street(val number: Int, val name: String)
data class Address(val city: String, val street: Street)
data class Company(val name: String, val address: Address)
data class Employee(val name: String, val company: Company)

val employee = Employee("John Doe", Company("Arrow", Address("Functional city", Street(23, "lambda street"))))

employee.copy(
    company = employee.company.copy(
        address = employee.company.address.copy(
            street = employee.company.address.street.copy(name = "delta st.")
        )
    )
)
```

And this was not the worst case, because there were no collection fields.  

### Dependencies

* kotlin-stdlib-jdk8
* jackson-core
* jackson-module-kotlin

### Setup

You may just copy/paste source code from file `DeepCopyHelper.kt`.  
This library is not (yet) distributed via maven repository.

### Usage

You will be able to write this:
```kotlin
import ru.slix.kotlin_deep_copy_helper.deepCopy // or your package, if you copy source code

employee.deepCopy("company/address/street/name", "delta st.")
```

Arrays and sets are not a problem:
```kotlin
val orders: List<Order> = listOf(
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

val ordersCopy: List<Order> = orders.deepCopy("1/products/0/id", "pid-test")
```

You can mutate an array by inserting/appending/removing elements:
```kotlin
val order1 = Order(id = "oid-1", products = listOf())
val order2 = Order(id = "oid-0", products = listOf())

// append
val ordersCopy1 = listOf(order1).deepCopy("1", order2, ArrayModificationMode.INSERT_APPEND) // [order1, order2]
// insert
val ordersCopy2 = listOf(order2).deepCopy("0", order1, ArrayModificationMode.INSERT_APPEND) // [order1, order2]
// remove
val ordersCopy3 = listOf(order1).deepCopy("0", null, ArrayModificationMode.REMOVE) // []
```

FYI, default `ArrayModificationMode` is `REPLACE`.  

To modify multiple properties, chain calls:
```kotlin
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
```

As you can guess, these tricks are not type-safe, but `deepCopy` will produce exactly same type as source object.

For more examples and proofs check out `DeepCopyHelperTest.kt`
