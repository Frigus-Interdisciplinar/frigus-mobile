package com.example.frigus_mobile.data.model

class Resource<T> private constructor(
    val status: Status,
    val data: T? = null,
    val message: String? = null
) {
    enum class Status {
        SUCCESS,
        ERROR,
        LOADING
    }

    val isLoading: Boolean
        get() = status == Status.LOADING

    val isSuccess: Boolean
        get() = status == Status.SUCCESS

    val isError: Boolean
        get() = status == Status.ERROR

    companion object {
        fun <T> success(data: T): Resource<T> = Resource(Status.SUCCESS, data, null)
        fun <T> error(message: String, data: T? = null): Resource<T> = Resource(Status.ERROR, data, message)
        fun <T> loading(): Resource<T> = Resource(Status.LOADING, null, null)
    }
}
