package com.tsarev.stacktracebox

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test

class StackTraceFilterTest {

    private val simpleTrace = """
Exception in thread "main" java.lang.RuntimeException: some
	at com.tsarev.stacktracebox.MainKt.some1(main.kt:13)
	at com.tsarev.stacktracebox.MainKt.some2(main.kt:9)
	at com.tsarev.stacktracebox.MainKt.main(main.kt:5)
	at com.tsarev.stacktracebox.MainKt.main(main.kt)

Process finished with exit code 1
    """.trimIndent()

    @Test
    fun testSimpleTrace() {
        val eventsFlow = imitateProcessEventsFlow(simpleTrace)
        val result = runBlocking { eventsFlow.filterStackTraces().toList() }

        Assert.assertEquals(3, result.size)

        Assert.assertTrue(result[0] is ProcessStartTraceBoxEvent)
        Assert.assertTrue(result[2] is ProcessEndTraceBoxEvent)

        Assert.assertTrue(result[1] is TraceTraceBoxEvent)
        val event = result[1] as TraceTraceBoxEvent
        Assert.assertEquals("Exception in thread \"main\" java.lang.RuntimeException: some", event.firstLine.text)
        Assert.assertEquals("some", event.firstLine.exceptionText)
        Assert.assertEquals("java.lang.RuntimeException", event.firstLine.exception)
    }

    private val stdErrTrace = """
Exception in thread "main" java.lang.RuntimeException: some
	at com.tsarev.stacktracebox.MainKt.some1(main.kt:13)
	at com.tsarev.stacktracebox.MainKt.some2(main.kt:9)
	at com.tsarev.stacktracebox.MainKt.main(main.kt:5)
	at com.tsarev.stacktracebox.MainKt.main(main.kt)
    """.trimIndent()

    private val stdOutTrace = """
Process finished with exit code 1
    """.trimIndent()

    @Test
    fun testDifferentOutputTypes() {
        val eventsFlow = imitateProcessEventsFlow(
                stdErrTrace to "stderr",
                stdOutTrace to "stdout",
        )
        val result = runBlocking { eventsFlow.filterStackTraces().toList() }

        Assert.assertEquals(3, result.size)

        Assert.assertTrue(result[0] is ProcessStartTraceBoxEvent)
        Assert.assertTrue(result[2] is ProcessEndTraceBoxEvent)

        Assert.assertTrue(result[1] is TraceTraceBoxEvent)
        val event = result[1] as TraceTraceBoxEvent
        Assert.assertEquals("Exception in thread \"main\" java.lang.RuntimeException: some", event.firstLine.text)
        Assert.assertEquals("some", event.firstLine.exceptionText)
        Assert.assertEquals("java.lang.RuntimeException", event.firstLine.exception)
    }

    private val multipleTraces = """
Exception in thread "main" java.lang.RuntimeException: some
	at com.tsarev.stacktracebox.MainKt.some1(main.kt:13)

some other text

Exception in thread "main" some.OtherException
	at com.tsarev.stacktracebox.MainKt.some1
    """.trimIndent()

    @Test
    fun testMultipleTraces() {
        val eventsFlow = imitateProcessEventsFlow(multipleTraces)
        val result = runBlocking { eventsFlow.filterStackTraces().toList() }

        Assert.assertEquals(4, result.size)

        Assert.assertTrue(result[0] is ProcessStartTraceBoxEvent)
        Assert.assertTrue(result[3] is ProcessEndTraceBoxEvent)

        Assert.assertTrue(result[1] is TraceTraceBoxEvent)
        val firstTraceEvent = result[1] as TraceTraceBoxEvent
        Assert.assertEquals("Exception in thread \"main\" java.lang.RuntimeException: some", firstTraceEvent.firstLine.text)
        Assert.assertEquals("some", firstTraceEvent.firstLine.exceptionText)
        Assert.assertEquals("java.lang.RuntimeException", firstTraceEvent.firstLine.exception)

        Assert.assertTrue(result[2] is TraceTraceBoxEvent)
        val secondTraceEvent = result[2] as TraceTraceBoxEvent
        Assert.assertEquals("Exception in thread \"main\" some.OtherException", secondTraceEvent.firstLine.text)
        Assert.assertEquals(null, secondTraceEvent.firstLine.exceptionText)
        Assert.assertEquals("some.OtherException", secondTraceEvent.firstLine.exception)
    }

    private fun imitateProcessEventsFlow(text: String, type: String = "stderr") = imitateProcessEventsFlow(text to type)

    private fun imitateProcessEventsFlow(vararg chunks: Pair<String, String>) = flow {
        emit(ProcessStartTraceBoxEvent)
        chunks.forEach { (text, type) ->
            text.split("\n")
                    .forEach { emit(TextTraceBoxEvent(it, type)) }
        }
        emit(ProcessEndTraceBoxEvent)
    }

}