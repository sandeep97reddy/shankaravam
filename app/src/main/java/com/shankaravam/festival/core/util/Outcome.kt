package com.shankaravam.festival.core.util

/**
 * G2 operation result. Custom sealed type (instead of kotlin.Result) so use
 * cases can return typed validation failures from suspend functions.
 */
sealed interface Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>
    data class Err(val message: String) : Outcome<Nothing>
}
