package com.kenvix.natpoked.utils

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