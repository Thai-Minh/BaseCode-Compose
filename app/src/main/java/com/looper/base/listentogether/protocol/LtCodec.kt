@file:OptIn(ExperimentalSerializationApi::class)

package com.looper.base.listentogether.protocol

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class LtFrame(
    val type: String,
    private val payload: ByteArray,
) {
    fun <T> decode(deserializer: KSerializer<T>): T = LtCodec.protoBuf.decodeFromByteArray(
        deserializer = deserializer,
        bytes = payload
    )

    inline fun <reified T> decode(): T = decode(deserializer = serializer<T>())
}

object LtCodec {

    private const val COMPRESSION_THRESHOLD_BYTES = 100

    val protoBuf = ProtoBuf {
        encodeDefaults = false
    }

    inline fun <reified T> encode(type: String, payload: T, compress: Boolean): ByteArray =
        encode(
            type = type,
            payload = protoBuf.encodeToByteArray(serializer<T>(), payload),
            compress = compress
        )

    fun encode(type: String, payload: ByteArray, compress: Boolean): ByteArray {
        val shouldCompress = compress && payload.size > COMPRESSION_THRESHOLD_BYTES
        val envelope = LtEnvelope(
            type = type,
            payload = if (shouldCompress) {
                gzip(bytes = payload)
            } else {
                payload
            },
            compressed = shouldCompress,
        )
        return protoBuf.encodeToByteArray(serializer = LtEnvelope.serializer(), value = envelope)
    }

    fun decode(bytes: ByteArray): LtFrame {
        val envelope = protoBuf.decodeFromByteArray(
            deserializer = LtEnvelope.serializer(),
            bytes = bytes
        )
        return LtFrame(
            type = envelope.type,
            payload = if (envelope.compressed) {
                gunzip(bytes = envelope.payload)
            } else {
                envelope.payload
            },
        )
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use {
            it.write(bytes)
        }
        return output.toByteArray()
    }

    private fun gunzip(bytes: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(bytes)).use {
            it.readBytes()
        }
}
