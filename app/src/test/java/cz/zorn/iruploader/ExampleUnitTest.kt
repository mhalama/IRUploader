package cz.zorn.iruploader

import org.junit.Test

@OptIn(ExperimentalUnsignedTypes::class)
class ExampleUnitTest {
    @OptIn(ExperimentalUnsignedTypes::class)
    @Test
    fun test() {
        val data = arrayOf(100, 200, 100, 200, 222, 111)
//        val x = WavZip2().compress(data)
//        val y = WavZip().encode(data.toIntArray())

        //assertEquals(x, y)
    }
}