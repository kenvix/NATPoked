package com.kenvix.natpoked.utils

infix fun ByteArray.xor(other: ByteArray): ByteArray {
    if (this.size != other.size)
        throw IllegalArgumentException("ByteArray size not equal")

    val result = ByteArray(this.size)
    for (i in this.indices) {
        result[i] = (this[i].toInt() xor other[i].toInt()).toByte()
    }

    return result
}

infix fun IntArray.xor(other: IntArray): IntArray {
    if (this.size != other.size)
        throw IllegalArgumentException("IntArray size not equal")

    val result = IntArray(this.size)
    for (i in this.indices) {
        result[i] = this[i] xor other[i]
    }

    return result
}

infix fun LongArray.xor(other: LongArray): LongArray {
    if (this.size != other.size)
        throw IllegalArgumentException("LongArray size not equal")

    val result = LongArray(this.size)
    for (i in this.indices) {
        result[i] = this[i] xor other[i]
    }

    return result
}

infix fun List<Byte>.xor(other: List<Byte>): MutableList<Byte> {
    if (this.size != other.size)
        throw IllegalArgumentException("ByteArray size not equal")

    val result = ArrayList<Byte>(this.size)
    for (i in this.indices) {
        result.add((this[i].toInt() xor other[i].toInt()).toByte())
    }

    return result
}

fun parseIntRangeToArray(str: String, spliter: Char = ' '): IntArray {
    val s1 = str.split(spliter).filter { it.isNotEmpty() }
    val v = ArrayList<Any>(s1.size)
    var size = 0

    for (s in s1) {
        if (s.contains('-')) {
            val s2 = s.split('-')
            val start = s2[0].toInt()
            val end = s2[1].toInt()
            if (start > end) {
                throw IllegalArgumentException("Invalid range: $s")
            }

            size += end - start + 1
            v.add(IntRange(start, end))
        } else {
            size++
            v.add(s.toInt())
        }
    }

    val result = IntArray(size)
    var j = 0
    for (i in 0 until v.size) {
        val v1 = v[i]
        if (v1 is IntRange) {
            for (k in v1.first..v1.last) {
                result[j++] = k
            }
        } else {
            result[j++] = v1 as Int
        }
    }

    return result
}