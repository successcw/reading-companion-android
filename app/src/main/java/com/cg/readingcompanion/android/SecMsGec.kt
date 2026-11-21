package com.cg.readingcompanion.android

import java.security.MessageDigest

/** Port of the Sec-MS-GEC v2 token used by the Edge read-aloud endpoint. */
object SecMsGec {
    private const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    private const val WINDOWS_EPOCH_OFFSET = 11_644_473_600L
    private const val PREFIX_LENGTH = 57

    private val prefixWords = uintArrayOf(
        0x79706f43u, 0x68676972u, 0x43282074u, 0x694d2029u, 0x736f7263u,
        0x2074666fu, 0x70726f43u, 0x7461726fu, 0x2e6e6f69u, 0x6c6c4120u,
        0x67697220u, 0x20737468u, 0x65736572u, 0x64657672u, 0x0000002eu,
    )
    private val coefficients = arrayOf(
        0xE82E91B1u to 0xB32700D0u, 0x44DE24C6u to 0x88DFF51Du,
        0x3BEB65BCu to 0x29514038u, 0xC89C5933u to 0xB328CBDFu,
        0x8D1EC211u to 0xD405B4D5u, 0x314EF5D5u to 0x26E55F3Cu,
        0xE92491B1u to 0x607801C6u, 0xF0A5B9D5u to 0x82B2C1FBu,
        0x06064CF3u to 0xFA51A3DAu, 0xDA79FA60u to 0x207CC464u,
        0xBED20560u to 0x3C3C74F3u, 0x89F9C9F8u to 0xD2D14B61u,
        0x35FA38C6u to 0xBB04A2C1u, 0xCB49E3C6u to 0xC5626167u,
    )
    private const val FACTOR = 0x82893933u
    private const val ADJUSTMENT_0 = 0xF2F90B1Au
    private const val ADJUSTMENT_1 = 0x0B1A0000u

    fun token(nowMillis: Long = System.currentTimeMillis()): String {
        var ticks = (nowMillis / 1000 + WINDOWS_EPOCH_OFFSET).toULong()
        ticks -= ticks % 300u
        ticks *= 10_000_000u
        val suffix = "$ticks$TRUSTED_CLIENT_TOKEN".toByteArray(Charsets.UTF_8)
        val prefix = ByteArray(PREFIX_LENGTH)
        for (i in 0 until PREFIX_LENGTH) {
            val word = prefixWords[i / 4]
            prefix[i] = ((word shr ((i % 4) * 8)) and 0xffu).toByte()
        }
        val data = prefix + suffix
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        val (a, b) = cs64(suffix, digest)
        val bytes = byteArrayOf(
            (a and 0xffu).toByte(), ((a shr 8) and 0xffu).toByte(),
            ((a shr 16) and 0xffu).toByte(), ((a shr 24) and 0xffu).toByte(),
            (b and 0xffu).toByte(), ((b shr 8) and 0xffu).toByte(),
            ((b shr 16) and 0xffu).toByte(), ((b shr 24) and 0xffu).toByte()
        )
        return bytes.toHex().uppercase() + "-" + digest.toHex().uppercase()
    }

    private fun rol16(value: UInt): UInt = (value shl 16) or (value shr 16)

    private fun words(bytes: ByteArray): UIntArray {
        val padded = bytes.copyOf(if (bytes.size % 4 == 0) bytes.size else bytes.size + 4 - bytes.size % 4)
        val count = padded.size / 4
        val result = UIntArray(if (count % 2 == 0) count else count + 1)
        for (i in 0 until count) {
            val j = i * 4
            result[i] = (padded[j].toUInt() and 0xffu) or
                ((padded[j + 1].toUInt() and 0xffu) shl 8) or
                ((padded[j + 2].toUInt() and 0xffu) shl 16) or
                ((padded[j + 3].toUInt() and 0xffu) shl 24)
        }
        return result
    }

    private fun cs64(data: ByteArray, digest: ByteArray): Pair<UInt, UInt> {
        var keyA = (digest[0].toUInt() and 0xffu) or
            ((digest[1].toUInt() and 0xffu) shl 8) or
            ((digest[2].toUInt() and 0xffu) shl 16) or
            ((digest[3].toUInt() and 0xffu) shl 24)
        var keyB = (digest[4].toUInt() and 0xffu) or
            ((digest[5].toUInt() and 0xffu) shl 8) or
            ((digest[6].toUInt() and 0xffu) shl 16) or
            ((digest[7].toUInt() and 0xffu) shl 24)
        keyA = keyA or 1u
        keyB = keyB or 1u
        val factors = keyA * FACTOR to keyB * FACTOR
        val terms = ArrayList<UInt>()
        val offsets = ArrayList<UInt>()
        coefficients.forEachIndexed { index, coefficient ->
            terms += coefficient.first * (if (index % 2 == 0) factors.first else factors.second)
            offsets += coefficient.second
        }
        val allWords = words(byteArrayOf(0) + data)
        allWords.forEachIndexed { index, word ->
            var term = word * (if (index % 2 == 0) keyA else keyB)
            var offset = rol16(word) * (if (index % 2 == 0) 0xEF0569FBu else 0xC31713DBu)
            if (index == 0) {
                term += ADJUSTMENT_0 * factors.first
                offset += ADJUSTMENT_1
            }
            terms += term
            offsets += offset
        }

        var swapAcc = 0u
        var swapLast = 0u
        var state = 0u
        var acc = 0u
        var index = 0
        while (index + 1 < terms.size) {
            val x = terms[index]
            val y = terms[index + 1]
            var t = rol16(offsets[index] + x)
            var q = t * 0x76F50F34u
            swapAcc += rol16(q) * 0xC3EFEA97u + q
            t = rol16(offsets[index + 1] + y)
            q = t * 0x1881CE3Cu
            swapLast = rol16(q) * 0x35BD1EC9u + q
            swapAcc += swapLast

            state = state * keyA + x
            state = rol16(state) * 0xCF98B111u
            state = rol16(state) * 0x87085B9Fu
            state = rol16(state) * 0x12CEB96Du
            state = rol16(state) * 0x257E1D83u
            acc += state
            t = state * keyB + y
            t = rol16(t) * 0xA27416F5u
            t = rol16(t) * 0xD38396FFu
            state = rol16(rol16(t) * 0x7C932B89u) * 0xBFA49F69u
            acc += state
            index += 2
        }
        return swapLast.xor(state) to swapAcc.xor(acc)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }
}
