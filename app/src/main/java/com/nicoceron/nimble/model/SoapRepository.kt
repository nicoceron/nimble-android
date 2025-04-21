package com.nicoceron.nimble.model

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ksoap2.SoapEnvelope
import org.ksoap2.serialization.PropertyInfo
import org.ksoap2.serialization.SoapObject
import org.ksoap2.serialization.SoapPrimitive
import org.ksoap2.serialization.SoapSerializationEnvelope
import org.ksoap2.transport.HttpTransportSE
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// URLs and Namespaces (ensure these are correct)
private const val USER_NAMESPACE = "http://ws.nimblev5.nicoceron.com/"
private const val USER_URL = "http://10.0.2.2:8080/nimblev5-1.0-SNAPSHOT/UserService"
private const val TASK_NAMESPACE = "http://ws.nimblev5.nicoceron.com/" // Assuming same namespace
private const val TASK_URL = "http://10.0.2.2:8080/nimblev5-1.0-SNAPSHOT/TaskService"

private const val SOAP_REPO_TAG = "SoapRepository"

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

    suspend fun registerUser(username: String, email: String, plainPassword: String): Result<User?> {
        val methodName = "registerUser"
        val soapAction = USER_NAMESPACE + methodName
        val request = SoapObject(USER_NAMESPACE, methodName).apply {
            addProperty("username", username)
            addProperty("email", email)
            addProperty("plainPassword", plainPassword)
        }
        return makeSoapCall(USER_URL, soapAction, request) { response -> parseUser(response) }
    }


    // --- Task Service Calls ---
    suspend fun getTasksForUser(userId: Long): Result<List<Task>> {
        val methodName = "getTasksForUser"
        val soapAction = TASK_NAMESPACE + methodName
        val request = SoapObject(TASK_NAMESPACE, methodName).apply {
            addProperty("userId", userId)
        }
        Log.d(SOAP_REPO_TAG, "Requesting tasks for userId: $userId")

        // Use a specialized call for multiple tasks
        return getMultipleTasks(TASK_URL, soapAction, request)
    }

    private suspend fun getMultipleTasks(
        url: String, soapAction: String, request: SoapObject
    ): Result<List<Task>> {
        return withContext(Dispatchers.IO) {
            try {
                val envelope = SoapSerializationEnvelope(SoapEnvelope.VER11)
                envelope.setOutputSoapObject(request)
                val transport = HttpTransportSE(url, 60000)
                Log.d(SOAP_REPO_TAG, "Executing SOAP Call for multiple tasks: Action=$soapAction, URL=$url")
                transport.call(soapAction, envelope)
                Log.d(SOAP_REPO_TAG, "SOAP Response Received. Body Class: ${envelope.bodyIn?.javaClass?.name}")
                if (envelope.bodyIn != null) { Log.v(SOAP_REPO_TAG, "Raw SOAP Response Body: ${envelope.bodyIn}") }
                else { Log.w(SOAP_REPO_TAG, "SOAP Response Body is NULL") }

                if (envelope.bodyIn is SoapObject) {
                    val response = envelope.bodyIn as SoapObject

                    // Check for SOAP Fault
                    if (response.hasProperty("faultCode")) {
                        val faultString = response.getSafeStringProperty("faultString") ?: "Unknown SOAP fault"
                        Log.e(SOAP_REPO_TAG, "SOAP Fault: $faultString")
                        return@withContext Result.failure(Exception("SOAP Fault: $faultString"))
                    }

                    // Process all "return" properties
                    val tasks = mutableListOf<Task>()

                    // Log property names for debugging
                    val propertyNames = (0 until response.propertyCount).map {
                        val propertyInfo = PropertyInfo()
                        response.getPropertyInfo(it, propertyInfo)
                        propertyInfo.name
                    }
                    Log.d(SOAP_REPO_TAG, "Response contains ${propertyNames.count { it == "return" }} 'return' properties")

                    // Process each "return" property
                    for (i in 0 until response.propertyCount) {
                        val propertyInfo = PropertyInfo()
                        response.getPropertyInfo(i, propertyInfo)

                        if (propertyInfo.name == "return") {
                            val taskSoapObject = response.getProperty(i) as? SoapObject
                            if (taskSoapObject != null) {
                                parseTask(taskSoapObject)?.let { task ->
                                    tasks.add(task)
                                    Log.d(SOAP_REPO_TAG, "Added task #${tasks.size}: id=${task.taskId}, title='${task.title}'")
                                }
                            }
                        }
                    }

                    Log.d(SOAP_REPO_TAG, "Finished processing tasks. Found ${tasks.size} tasks.")
                    Result.success(tasks)
                } else {
                    Log.e(SOAP_REPO_TAG, "Unexpected SOAP body type: ${envelope.bodyIn?.javaClass?.name}")
                    Result.failure(Exception("Unexpected SOAP body type for task list"))
                }
            } catch (e: Exception) {
                Log.e(SOAP_REPO_TAG, "SOAP Call failed for action '$soapAction': ${e.message}", e)
                Result.failure(e)
            }
        }
    }


    suspend fun createTaskWithoutDate(
        userId: Long,
        title: String,
        description: String?,
        priority: TaskPriority
    ): Result<Task?> {
        val methodName = "createTaskWithoutDate"
        val soapAction = TASK_NAMESPACE + methodName
        val request = SoapObject(TASK_NAMESPACE, methodName).apply {
            addProperty("userId", userId)
            addProperty("title", title)
            addProperty("description", description ?: "")
            addProperty("priority", priority.name)
        }
        Log.d(SOAP_REPO_TAG, "Creating task: userId=$userId, title=$title")
        return makeSoapCall(TASK_URL, soapAction, request) { response -> parseTask(response) }
    }

    suspend fun deleteTask(taskId: Long): Result<Boolean> {
        val methodName = "deleteTask"
        val soapAction = TASK_NAMESPACE + methodName
        val request = SoapObject(TASK_NAMESPACE, methodName).apply {
            addProperty("taskId", taskId)
        }
        Log.d(SOAP_REPO_TAG, "Deleting task: taskId=$taskId")
        return makePrimitiveSoapCall(TASK_URL, soapAction, request) { response ->
            response?.toString()?.toBoolean() ?: false
        }
    }

    suspend fun updateTask(
        taskToUpdate: Task,
        newStatus: TaskStatus
    ): Result<Task?> {
        val methodName = "updateTask"
        val soapAction = TASK_NAMESPACE + methodName
        val request = SoapObject(TASK_NAMESPACE, methodName).apply {
            addProperty("taskId", taskToUpdate.taskId ?: throw IllegalArgumentException("Task ID cannot be null for update"))
            addProperty("title", taskToUpdate.title ?: "")
            addProperty("description", taskToUpdate.description ?: "")
            addPropertyIfValueNotNull("dueDate", null)
            addProperty("priority", taskToUpdate.priority?.name ?: TaskPriority.MEDIUM.name)
            addProperty("status", newStatus.name)
        }
        Log.d(SOAP_REPO_TAG, "Updating task: taskId=${taskToUpdate.taskId}, newStatus=$newStatus")
        return makeSoapCall(TASK_URL, soapAction, request) { response -> parseTask(response) }
    }

    // --- Helper Functions ---

    private suspend fun <T> makeSoapCall(
        url: String, soapAction: String, request: SoapObject, parser: (SoapObject?) -> T?
    ): Result<T?> {
        return withContext(Dispatchers.IO) {
            try {
                val envelope = SoapSerializationEnvelope(SoapEnvelope.VER11)
                envelope.setOutputSoapObject(request)
                val transport = HttpTransportSE(url, 60000)
                Log.d(SOAP_REPO_TAG, "Executing SOAP Call: Action=$soapAction, URL=$url")
                transport.call(soapAction, envelope)
                Log.d(SOAP_REPO_TAG, "SOAP Response Received. Body Class: ${envelope.bodyIn?.javaClass?.name}")
                if (envelope.bodyIn != null) { Log.v(SOAP_REPO_TAG, "Raw SOAP Response Body: ${envelope.bodyIn}") }
                else { Log.w(SOAP_REPO_TAG, "SOAP Response Body is NULL") }

                if (envelope.bodyIn is SoapObject) {
                    val response = envelope.bodyIn as SoapObject

                    // Check for SOAP Fault (using safe property access)
                    if (response.hasProperty("faultCode")) {
                        val faultString = response.getSafeStringProperty("faultString") ?: "Unknown SOAP fault"
                        Log.e(SOAP_REPO_TAG, "SOAP Fault: $faultString")
                        Result.failure(Exception("SOAP Fault: $faultString"))
                    } else {
                        var objectToParse: SoapObject? = response

                        if (response.propertyCount > 0) {
                            // Log all property names for debugging
                            val propertyNames = (0 until response.propertyCount).map {
                                val propertyInfo = PropertyInfo()
                                response.getPropertyInfo(it, propertyInfo)
                                propertyInfo.name
                            }
                            Log.d(SOAP_REPO_TAG, "Response properties: $propertyNames")

                            // Check for "return" property by name directly
                            val returnPropertyIndex = propertyNames.indexOf("return")
                            if (returnPropertyIndex >= 0) {
                                val returnProperty = response.getProperty(returnPropertyIndex)
                                if (returnProperty is SoapObject) {
                                    objectToParse = returnProperty
                                    Log.d(SOAP_REPO_TAG, "Detected 'return' SoapObject at index $returnPropertyIndex, passing it to parser.")
                                } else {
                                    Log.d(SOAP_REPO_TAG, "Detected 'return' property at index $returnPropertyIndex, but it's not a SoapObject (${returnProperty?.javaClass?.simpleName}). Passing outer response.")
                                }
                            } else {
                                Log.d(SOAP_REPO_TAG, "No 'return' property found in response. Passing outer response.")
                            }
                        } else {
                            Log.d(SOAP_REPO_TAG, "Empty response (property count=0). Passing outer response.")
                        }

                        // Log class name instead of accessing .name directly here
                        val logClassName = objectToParse?.javaClass?.simpleName ?: "null"
                        Log.d(SOAP_REPO_TAG, "Passing object of type '$logClassName' to parser.")

                        Result.success(parser(objectToParse))
                    }
                } else if (envelope.bodyIn == null) {
                    Log.d(SOAP_REPO_TAG, "SOAP Body is null (e.g., void response), passing null to parser.")
                    Result.success(parser(null))
                } else {
                    Log.e(SOAP_REPO_TAG, "Unexpected SOAP body type: ${envelope.bodyIn?.javaClass?.name}")
                    Result.failure(Exception("Unexpected SOAP body type: ${envelope.bodyIn?.javaClass?.name}"))
                }
            } catch (e: Exception) {
                Log.e(SOAP_REPO_TAG, "SOAP Call failed for action '$soapAction': ${e.message}", e)
                Result.failure(e)
            }
        }
    }


    private suspend fun <T> makePrimitiveSoapCall(
        url: String, soapAction: String, request: SoapObject, parser: (SoapPrimitive?) -> T?
    ): Result<T> {
        Log.d(SOAP_REPO_TAG, "Executing Primitive SOAP Call: Action=$soapAction, URL=$url")
        return withContext(Dispatchers.IO) {
            try {
                val envelope = SoapSerializationEnvelope(SoapEnvelope.VER11)
                envelope.setOutputSoapObject(request)
                val transport = HttpTransportSE(url, 60000)
                transport.call(soapAction, envelope)
                Log.d(SOAP_REPO_TAG, "Primitive SOAP Response Received. Body Class: ${envelope.bodyIn?.javaClass?.name}")

                when (val body = envelope.bodyIn) { // Moved declaration into 'when'
                    is SoapObject -> {
                        if (body.hasProperty("faultCode")) { // Check before access
                            val faultString = body.getSafeStringProperty("faultString") ?: "Unknown"
                            Log.e(SOAP_REPO_TAG, "SOAP Fault in primitive call: $faultString")
                            Result.failure(Exception("SOAP Fault: $faultString"))
                        } else if (body.propertyCount > 0 && body.getProperty(0) is SoapPrimitive) {
                            Log.d(SOAP_REPO_TAG, "Parsing primitive from nested SoapObject.")
                            Result.success(parser(body.getProperty(0) as SoapPrimitive) ?: throw IllegalStateException("Parser returned null for primitive"))
                        } else {
                            Log.e(SOAP_REPO_TAG, "Expected primitive SOAP response or Fault, but got object: $body")
                            Result.failure(Exception("Expected primitive SOAP response, but got object: $body"))
                        }
                    }
                    is SoapPrimitive -> {
                        Log.d(SOAP_REPO_TAG, "Parsing primitive directly from body.")
                        Result.success(parser(body) ?: throw IllegalStateException("Parser returned null for primitive"))
                    }
                    else -> {
                        Log.e(SOAP_REPO_TAG, "Unexpected SOAP response type for primitive: ${body?.javaClass?.name}")
                        Result.failure(Exception("Unexpected SOAP response type for primitive: ${body?.javaClass?.name}"))
                    }
                }
            } catch (e: Exception) {
                Log.e(SOAP_REPO_TAG, "Primitive SOAP Call failed for action '$soapAction': ${e.message}", e)
                Result.failure(e)
            }
        }
    }


    // --- Parsers ---

    private fun parseUser(userSoapObject: SoapObject?): User? {
        if (userSoapObject == null || userSoapObject.toString() == "anyType{}") {
            Log.w(SOAP_REPO_TAG, "parseUser received null or empty SoapObject.")
            return null
        }
        Log.d(SOAP_REPO_TAG, "--- Parsing User from SoapObject: ${userSoapObject.javaClass.simpleName} ---")

        return try {
            // First try extracting direct properties
            var userId = userSoapObject.getSafeStringProperty("userId")?.toLongOrNull()
            var username = userSoapObject.getSafeStringProperty("username")
            var email = userSoapObject.getSafeStringProperty("email")
            var createdDate = userSoapObject.getSafeStringProperty("createdDate")?.let { parseSoapDateTime(it) }

            // If essential properties are missing, see if there's a nested "return" object
            if ((userId == null || username == null || email == null) && userSoapObject.hasProperty("return")) {
                Log.d(SOAP_REPO_TAG, "  Essential User properties missing, checking for nested 'return' object")
                val returnObj = userSoapObject.getPropertyAsSoapObject("return")
                if (returnObj != null) {
                    userId = returnObj.getSafeStringProperty("userId")?.toLongOrNull() ?: userId
                    username = returnObj.getSafeStringProperty("username") ?: username
                    email = returnObj.getSafeStringProperty("email") ?: email
                    createdDate = returnObj.getSafeStringProperty("createdDate")?.let { parseSoapDateTime(it) } ?: createdDate
                }
            }

            Log.d(SOAP_REPO_TAG, "  Parsed User: id=$userId, username=$username, email=$email")
            if (userId == null || username == null || email == null) {
                Log.e(SOAP_REPO_TAG, "  Failed to parse essential User fields (userId, username, or email is null)")
                return null
            }
            User(userId = userId, username = username, email = email, createdDate = createdDate)
        } catch (e: Exception) {
            Log.e(SOAP_REPO_TAG, "!!! Exception during parseUser for ${userSoapObject.javaClass.simpleName}: ${e.message}", e)
            null
        }
    }

    // Add this function to check if a SoapObject is likely a task
    private fun SoapObject.isLikelyTask(): Boolean {
        return this.hasProperty("taskId") &&
                (this.hasProperty("title") || this.hasProperty("status") || this.hasProperty("user"))
    }

    // This completely revised parseTaskList method checks ALL possible task sources
    private fun parseTaskList(containerSoapObject: SoapObject?): List<Task> {
        if (containerSoapObject == null) {
            Log.d(SOAP_REPO_TAG, "parseTaskList received null object, returning emptyList.")
            return emptyList()
        }

        val tasks = mutableListOf<Task>()
        Log.d(SOAP_REPO_TAG, "Parsing container of type: ${containerSoapObject.javaClass.simpleName} with ${containerSoapObject.propertyCount} properties")

        // Store property names for debugging
        val propertyNames = (0 until containerSoapObject.propertyCount).map {
            val propInfo = PropertyInfo()
            containerSoapObject.getPropertyInfo(it, propInfo)
            propInfo.name
        }
        Log.d(SOAP_REPO_TAG, "Container properties: $propertyNames")

        // CASE 1: The container might be a response with multiple 'return' properties
        var hasReturnProperties = false
        for (i in 0 until containerSoapObject.propertyCount) {
            val propertyInfo = PropertyInfo()
            containerSoapObject.getPropertyInfo(i, propertyInfo)

            if (propertyInfo.name == "return") {
                hasReturnProperties = true
                val propertyValue = containerSoapObject.getProperty(i)

                if (propertyValue is SoapObject) {
                    Log.d(SOAP_REPO_TAG, "Examining 'return' property at index $i")

                    parseTask(propertyValue)?.let { task ->
                        tasks.add(task)
                        Log.d(SOAP_REPO_TAG, "✓ Successfully parsed 'return' at index $i as Task: id=${task.taskId}, title='${task.title}'")
                    } ?: run {
                        Log.d(SOAP_REPO_TAG, "✗ Failed to parse 'return' at index $i as a task")
                    }
                } else {
                    Log.d(SOAP_REPO_TAG, "❓ 'return' property at index $i is not a SoapObject: ${propertyValue?.javaClass?.simpleName}")
                }
            }
        }

        // CASE 2: If no return properties found, the object itself might be a task
        if (!hasReturnProperties && containerSoapObject.isLikelyTask()) {
            Log.d(SOAP_REPO_TAG, "Container appears to be a Task itself. Parsing directly.")
            parseTask(containerSoapObject)?.let {
                tasks.add(it)
                Log.d(SOAP_REPO_TAG, "✓ Successfully parsed container directly as a Task: id=${it.taskId}, title='${it.title}'")
            }
        }

        // CASE 3: Fallback - look for task properties in child objects
        if (tasks.isEmpty() && !hasReturnProperties) {
            Log.d(SOAP_REPO_TAG, "No tasks found through normal means. Searching all properties for task-like objects.")
            for (i in 0 until containerSoapObject.propertyCount) {
                val propertyValue = containerSoapObject.getProperty(i)
                if (propertyValue is SoapObject && propertyValue.isLikelyTask()) {
                    parseTask(propertyValue)?.let { task ->
                        tasks.add(task)
                        Log.d(SOAP_REPO_TAG, "✓ Found task-like property at index $i: id=${task.taskId}, title='${task.title}'")
                    }
                }
            }
        }

        Log.d(SOAP_REPO_TAG, "parseTaskList finished. Found ${tasks.size} tasks: ${tasks.map { it.taskId }}")
        return tasks
    }


    private fun parseTask(taskSoapObject: SoapObject?): Task? {
        if (taskSoapObject == null || taskSoapObject.toString() == "anyType{}") {
            Log.w(SOAP_REPO_TAG, "parseTask received null or empty SoapObject.")
            return null
        }
        Log.d(SOAP_REPO_TAG, "--- Parsing Task from SoapObject: ${taskSoapObject.javaClass.simpleName} ---")

        return try {
            val taskId = taskSoapObject.getSafeStringProperty("taskId")?.toLongOrNull()
            val title = taskSoapObject.getSafeStringProperty("title")
            val description = taskSoapObject.getSafeStringProperty("description")
            val dueDate = taskSoapObject.getSafeStringProperty("dueDate")?.let { parseSoapDateTime(it) }
            val priority = taskSoapObject.getSafeStringProperty("priority")?.let { safeValueOf<TaskPriority>(it) }
            val status = taskSoapObject.getSafeStringProperty("status")?.let { safeValueOf<TaskStatus>(it) }
            val createdDate = taskSoapObject.getSafeStringProperty("createdDate")?.let { parseSoapDateTime(it) }
            val lastModifiedDate = taskSoapObject.getSafeStringProperty("lastModifiedDate")?.let { parseSoapDateTime(it) }

            val userObj = taskSoapObject.getPropertyAsSoapObject("user")
            val userId = userObj?.getSafeStringProperty("userId")?.toLongOrNull()
                ?: run { Log.w(SOAP_REPO_TAG, "   Task taskId=$taskId missing nested 'user' object or 'userId' within it."); null }

            if (taskId == null || userId == null) {
                Log.e(SOAP_REPO_TAG, "   Failed to parse essential Task fields (taskId or userId is null). Skipping this task.")
                return null
            }

            Log.d(SOAP_REPO_TAG, "  Parsed taskId: $taskId, title: '$title', priority: $priority, status: $status, userId: $userId")

            val task = Task(
                taskId = taskId, userId = userId, title = title, description = description,
                dueDate = dueDate, priority = priority, status = status,
                createdDate = createdDate, lastModifiedDate = lastModifiedDate
            )
            Log.d(SOAP_REPO_TAG, "--- Successfully built Task object: $task ---")
            task
        } catch (e: Exception) {
            Log.e(SOAP_REPO_TAG, "!!! Exception during parseTask for ${taskSoapObject.javaClass.simpleName}: ${e.message}", e)
            null
        }
    }

    // --- Utility Functions ---

    /** Safely gets property as String, returning null if missing/blank. */
    private fun SoapObject.getSafeStringProperty(name: String): String? {
        if (!this.hasProperty(name)) { return null }
        val prop = this.getProperty(name) ?: return null
        val valueAsString = when (prop) {
            is String -> prop
            is SoapPrimitive -> prop.toString()
            else -> prop.toString().takeIf { it != "anyType{}" }
        }
        return valueAsString?.ifBlank { null }
    }

    /** Safely gets a nested property as a SoapObject. */
    private fun SoapObject.getPropertyAsSoapObject(name: String): SoapObject? {
        if (!this.hasProperty(name)) { return null }
        val prop = this.getProperty(name)
        return prop as? SoapObject
    }

    /** Safely converts a String to an Enum. */
    private inline fun <reified T : Enum<T>> safeValueOf(value: String?): T? {
        if (value == null) return null
        return try {
            java.lang.Enum.valueOf(T::class.java, value.uppercase(Locale.ROOT))
        } catch (e: IllegalArgumentException) {
            Log.w(SOAP_REPO_TAG,"Warning: Unknown enum value '$value' for ${T::class.java.simpleName}")
            null
        }
    }

    /** Parses common ISO 8601 date/time formats. */
    private fun parseSoapDateTime(dateTimeString: String?): Date? {
        if (dateTimeString.isNullOrBlank()) return null
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US), SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US), SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
            SimpleDateFormat("yyyy-MM-dd", Locale.US)
        )
        for (format in formats) {
            try {
                format.isLenient = false
                return format.parse(dateTimeString)
            } catch (e: Exception) { /* Ignore */ }
        }
        Log.w(SOAP_REPO_TAG,"Warning: Could not parse date-time string '$dateTimeString' with any known format.")
        return null
    }

    /** Adds a property to a SoapObject. */
    private fun SoapObject.addPropertyIfValueNotNull(name: String, value: Any?) {
        this.addProperty(name, value)
    }
}