package com.kenvix.web.utils

interface Getable<T, R> {
    operator fun get(a: T): R
}

interface BiGetable<T, U, R> {
    operator fun get(a: T, b: U): R
}