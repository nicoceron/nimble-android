package com.nicoceron.nimble.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ksoap2.SoapEnvelope
import org.ksoap2.serialization.SoapObject
import org.ksoap2.serialization.SoapPrimitive
import org.ksoap2.serialization.SoapSerializationEnvelope
import org.ksoap2.transport.HttpTransportSE
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
// Note: java.sql.Timestamp is not typically used directly in Android client model

// URLs and Namespaces
private const val USER_NAMESPACE = "http://ws.nimblev5.nicoceron.com/"
private const val USER_URL = "http://10.0.2.2:8080/nimblev5-1.0-SNAPSHOT/UserService"
private const val TASK_NAMESPACE = "http://ws.nimblev5.nicoceron.com/" // Assuming same namespace
private const val TASK_URL = "http://10.0.2.2:8080/nimblev5-1.0-SNAPSHOT/TaskService"

object SoapRepository {

    // --- User Service Calls ---
    suspend fun loginUser(username: String, plainPassword: String): Result<User?> {
        val methodName = "loginUser"
        val soapAction = USER_NAMESPACE + methodName
        val request = SoapObject(USER_NAMESPACE, methodName).apply {
            addProperty("username", username)
            addProperty("plainPassword", plainPassword)
        }
        return makeSoapCall(USER_URL, soapAction, request) { response -> parseUser(response) }
    }

    // Add registerUser call if needed

    // --- Task Service Calls ---
    suspend fun getTasksForUser(userId: Long): Result<List<Task>?> {
        val methodName = "getTasksForUser"
        val soapAction = TASK_NAMESPACE + methodName
        val request = SoapObject(TASK_NAMESPACE, methodName).apply {
            addProperty("userId", userId)
        }
        return makeSoapCall(TASK_URL, soapAction, request) { response -> parseTaskList(response) }
    }

    // --- Task Creation (New Overloaded Version - No Date) ---
    suspend fun createTaskWithoutDate(
        userId: Long,
        title: String,
        description: String?,
        priority: TaskPriority
    ): Result<Task?> {
        val methodName = "createTaskWithoutDate" // Use the new operation name
        val soapAction = TASK_NAMESPACE + methodName

        val request = SoapObject(TASK_NAMESPACE, methodName).apply {
            addProperty("userId", userId)
            addProperty("title", title)
            addProperty("description", description ?: "")
            // No dueDate property added
            addProperty("priority", priority.name)
        }
        return makeSoapCall(TASK_URL, soapAction, request) { response -> parseTask(response) }
    }
    // --- End of New Function ---


    // --- Delete Task ---
    suspend fun deleteTask(taskId: Long): Result<Boolean> {
        val methodName = "deleteTask"
        val soapAction = TASK_NAMESPACE + methodName
        val request = SoapObject(TASK_NAMESPACE, methodName).apply {
            addProperty("taskId", taskId)
        }
        return makePrimitiveSoapCall(TASK_URL, soapAction, request) { response ->
            response?.toString()?.toBoolean() ?: false
        }
    }

    // --- Helper Functions (makeSoapCall, makePrimitiveSoapCall) ---
    // Unchanged from previous version...
    private suspend fun <T> makeSoapCall(
        url: String, soapAction: String, request: SoapObject, parser: (SoapObject?) -> T?
    ): Result<T?> {
        return withContext(Dispatchers.IO) {
            try {
                val envelope = SoapSerializationEnvelope(SoapEnvelope.VER11)
                envelope.setOutputSoapObject(request)
                val transport = HttpTransportSE(url, 60000)
                transport.call(soapAction, envelope)
                if (envelope.bodyIn is SoapObject) {
                    val response = envelope.bodyIn as SoapObject
                    if (response.hasProperty("faultcode")) {
                        Result.failure(Exception("SOAP Fault: ${response.getPropertyAsString("faultstring") ?: "Unknown"}"))
                    } else {
                        var resultObject: Any? = null
                        if (response.propertyCount > 0) { resultObject = response.getProperty(0) }
                        if (resultObject is SoapObject) { Result.success(parser(resultObject)) }
                        else if (resultObject == null || resultObject.toString() == "anyType{}") { Result.success(parser(null)) }
                        else { Result.success(parser(response)) } // Fallback: try parsing outer response
                    }
                } else if (envelope.bodyIn == null) { Result.success(parser(null)) }
                else { Result.failure(Exception("Unexpected SOAP body type: ${envelope.bodyIn?.javaClass?.name}")) }
            } catch (e: Exception) { Result.failure(e) }
        }
    }

    private suspend fun <T> makePrimitiveSoapCall(
        url: String, soapAction: String, request: SoapObject, parser: (SoapPrimitive?) -> T?
    ): Result<T> {
        return withContext(Dispatchers.IO) {
            try {
                val envelope = SoapSerializationEnvelope(SoapEnvelope.VER11)
                envelope.setOutputSoapObject(request)
                val transport = HttpTransportSE(url, 60000)
                transport.call(soapAction, envelope)
                if (envelope.bodyIn is SoapObject) {
                    val response = envelope.bodyIn as SoapObject
                    if (response.hasProperty("faultcode")) {
                        Result.failure(Exception("SOAP Fault: ${response.getPropertyAsString("faultstring") ?: "Unknown"}"))
                    } else if (response.propertyCount > 0 && response.getProperty(0) is SoapPrimitive) {
                        Result.success(parser(response.getProperty(0) as SoapPrimitive) ?: throw IllegalStateException("Parser returned null"))
                    } else { Result.failure(Exception("Expected primitive SOAP response, but got: $response")) }
                } else if (envelope.bodyIn is SoapPrimitive) {
                    Result.success(parser(envelope.bodyIn as SoapPrimitive) ?: throw IllegalStateException("Parser returned null"))
                } else { Result.failure(Exception("Unexpected SOAP response type for primitive: ${envelope.bodyIn?.javaClass?.name}")) }
            } catch (e: Exception) { Result.failure(e) }
        }
    }

    // --- Parsers (parseUser, parseTaskList, parseTask) ---
    // Mostly unchanged, ensure parseTask handles potentially nested User if needed
    private fun parseUser(soapObject: SoapObject?): User? {
        // ... (same as before) ...
        if (soapObject == null || soapObject.toString() == "anyType{}") return null
        return try {
            User(
                userId = soapObject.getPropertySafelyAsString("userId")?.toLongOrNull(),
                username = soapObject.getPropertySafelyAsString("username"),
                email = soapObject.getPropertySafelyAsString("email"),
                createdDate = soapObject.getPropertySafelyAsString("createdDate")?.let { parseSoapDateTime(it) }
            )
        } catch (e: Exception) { println("Error parsing User: ${e.message}"); null }
    }

    private fun parseTaskList(soapObject: SoapObject?): List<Task>? {
        // ... (same as before) ...
        if (soapObject == null || soapObject.toString() == "anyType{}") return emptyList()
        val tasks = mutableListOf<Task>()
        for (i in 0 until soapObject.propertyCount) {
            val property = soapObject.getProperty(i)
            if (property is SoapObject) {
                if (property.hasProperty("taskId") || property.hasProperty("title")) {
                    parseTask(property)?.let { tasks.add(it) }
                }
            }
        }
        if (tasks.isEmpty() && (soapObject.hasProperty("taskId") || soapObject.hasProperty("title"))) {
            parseTask(soapObject)?.let { tasks.add(it) }
        }
        return tasks
    }

    private fun parseTask(soapObject: SoapObject?): Task? {
        if (soapObject == null || soapObject.toString() == "anyType{}") return null
        return try {
            // Check if User details are nested
            val userObj = soapObject.getPropertyAsSoapObject("user") // Helper needed?
            val userIdFromTask = userObj?.getPropertySafelyAsString("userId")?.toLongOrNull()
                ?: soapObject.getPropertySafelyAsString("userId")?.toLongOrNull()

            Task(
                taskId = soapObject.getPropertySafelyAsString("taskId")?.toLongOrNull(),
                userId = userIdFromTask, // Get ID from nested or direct field
                title = soapObject.getPropertySafelyAsString("title"),
                description = soapObject.getPropertySafelyAsString("description"),
                dueDate = soapObject.getPropertySafelyAsString("dueDate")?.let { parseSoapDateTime(it) },
                priority = soapObject.getPropertySafelyAsString("priority")?.let { safeValueOf<TaskPriority>(it) },
                status = soapObject.getPropertySafelyAsString("status")?.let { safeValueOf<TaskStatus>(it) },
                createdDate = soapObject.getPropertySafelyAsString("createdDate")?.let { parseSoapDateTime(it) },
                lastModifiedDate = soapObject.getPropertySafelyAsString("lastModifiedDate")?.let { parseSoapDateTime(it) }
            )
        } catch (e: Exception) {
            println("Error parsing Task: ${e.message}")
            null
        }
    }

    // --- Utility Functions (getPropertySafelyAsString, safeValueOf, parseSoapDateTime) ---
    // Unchanged...
    fun SoapObject.getPropertySafelyAsString(name: String): String? {
        return if (this.hasProperty(name)) {
            val prop = this.getProperty(name)
            // Ksoap returns PropertyInfo for complex types, need .toString()
            if (prop !is String) prop?.toString() else prop
        } else null
    }
    // Helper to get nested SoapObject safely
    fun SoapObject.getPropertyAsSoapObject(name: String): SoapObject? {
        return if (this.hasProperty(name)) this.getProperty(name) as? SoapObject else null
    }

    inline fun <reified T : Enum<T>> safeValueOf(value: String?): T? {
        if (value == null) return null
        return try { java.lang.Enum.valueOf(T::class.java, value.uppercase(Locale.ROOT)) }
        catch (e: IllegalArgumentException) { println("Warning: Unknown enum value '$value' for ${T::class.java.simpleName}"); null }
    }

    private fun parseSoapDateTime(dateTimeString: String?): Date? {
        if (dateTimeString.isNullOrBlank()) return null
        // Add more formats if needed based on actual server response
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US), // Offset + millis
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),    // Offset, no millis
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US), // Zulu + millis
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)       // Zulu, no millis
        )
        for (format in formats) {
            try { return format.parse(dateTimeString) } catch (e: Exception) { /* Try next */ }
        }
        println("Warning: Could not parse date-time: $dateTimeString")
        return null
    }

    suspend fun registerUser(username: String, email: String, plainPassword: String): Result<User?> {
        val methodName = "registerUser" // Matches @WebMethod in UserSoapService
        val soapAction = USER_NAMESPACE + methodName

        val request = SoapObject(USER_NAMESPACE, methodName).apply {
            addProperty("username", username)
            addProperty("email", email)
            addProperty("plainPassword", plainPassword)
        }

        // Calls the backend, expects a User object (or null on failure)
        return makeSoapCall(USER_URL, soapAction, request) { response ->
            parseUser(response) // Use the existing parser
        }
    }
}