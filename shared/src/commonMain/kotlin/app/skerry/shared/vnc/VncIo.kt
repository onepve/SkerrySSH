package app.skerry.shared.vnc

/**
 * Byte source the [RfbCodec] pulls from. [readFully] must block (suspend) until exactly [len] bytes
 * are read into [dst] at [offset], or throw on EOF/close. This pull model is what lets the decoder
 * stay linear despite RFB's deeply nested variable-length structures: partial socket reads are
 * absorbed here, so decoder logic never has to resume mid-structure (see the codec's design note).
 */
fun interface VncSource {
    suspend fun readFully(dst: ByteArray, offset: Int, len: Int)
}

/** Byte sink the codec writes client→server messages to (handshake replies, input events, requests). */
fun interface VncSink {
    suspend fun write(bytes: ByteArray)
}

/**
 * Result of a mid-handshake TLS upgrade (VeNCrypt/TLS security types): the [source]/[sink] the
 * codec should switch to once the socket is wrapped in TLS. Produced by the transport (which owns
 * the socket) and handed to the codec, which is pure bytes and cannot do TLS itself.
 */
data class VncTlsUpgrade(val source: VncSource, val sink: VncSink)
