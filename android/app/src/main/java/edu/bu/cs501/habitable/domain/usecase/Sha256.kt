package edu.bu.cs501.habitable.domain.usecase

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * SHA-256 as 64 lower-case hex characters: the fingerprint stored with every
 * photo and sent to the server's /evidence/anchor. The file itself is never
 * uploaded; anyone holding the file can recompute the hash and compare.
 */
object Sha256 {

    fun of(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    /** Streams the input in 8 KB blocks, so a multi-megabyte photo never sits in memory. Does not close [input]. */
    fun of(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return hex(digest.digest())
    }

    fun of(file: File): String = file.inputStream().use { of(it) }

    private fun hex(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            out.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return out.toString()
    }

    private const val HEX = "0123456789abcdef"
}
