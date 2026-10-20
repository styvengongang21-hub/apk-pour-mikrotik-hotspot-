package com.salamwifi.app

import java.security.SecureRandom

enum class Kind(val short: String, val chars: String) {
    NUM("123", "0123456789"),
    UP("ABC", "ABCDEFGHJKLMNPQRSTUVWXYZ"),
    LOW("abc", "abcdefghijkmnopqrstuvwxyz"),
    MIX("Mix", "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789")
}

object Gen {
    private val rnd = SecureRandom()
    private fun rand(len: Int, k: Kind) = buildString { repeat(len) { append(k.chars[rnd.nextInt(k.chars.length)]) } }

    fun make(qty: Int, uLen: Int, uKind: Kind, pLen: Int, pKind: Kind, taken: MutableSet<String>): List<Voucher> {
        val out = ArrayList<Voucher>()
        var tries = 0
        while (out.size < qty) {
            if (++tries > qty * 200) throw IllegalStateException("Identifiant trop court pour $qty tickets uniques : augmentez sa longueur.")
            val u = rand(uLen, uKind)
            if (taken.add(u)) out.add(Voucher(u, rand(pLen, pKind)))
        }
        return out
    }
}
