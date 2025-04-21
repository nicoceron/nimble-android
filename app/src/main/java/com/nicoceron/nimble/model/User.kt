// model/User.kt
package com.nicoceron.nimble.model // Updated package

import java.util.Date

data class User(
    val userId: Long?,
    val username: String?,
    val email: String?,
    val createdDate: Date?
)