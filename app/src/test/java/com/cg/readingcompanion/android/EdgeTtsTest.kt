package com.cg.readingcompanion.android

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EdgeTtsTest {
    @Test
    fun tokenLooksValid() {
        val token = SecMsGec.token(1_700_000_000_000)
        println("TOKEN=" + token)
        assertTrue(token.matches(Regex("[0-9A-F]{16}-[0-9A-F]{64}")))
    }

    @Test
    fun synthesizeShortChinese() {
        runBlocking {
            val out = File.createTempFile("edge-tts-test", ".mp3")
            val result = EdgeTtsClient().synthesize("你好，我是读书陪读。", "zh-CN-XiaoxiaoNeural", out)
            assertTrue("Edge TTS failed: ${result.exceptionOrNull()}", result.isSuccess)
            assertTrue(out.length() > 1000)
            out.delete()
        }
    }
}
