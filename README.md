# Nimble Task Management - Android Client

This is the Android client application for the Nimble Task Management system. It allows users to register, log in, and manage their tasks by communicating with a backend service.

## Architecture Overview

* **Overall System Style:** Service-Oriented Architecture (SOA)
* **Component:** Frontend Client
* **Platform:** Android
* **Language:** Kotlin
* **Architecture Pattern:** MVC (Model-View-Controller) - *Note: While Android Activities/Fragments often blend View and Controller roles, the separation of `model` (data classes, `SoapRepository`), `controller` (Activities, Adapters), and `layout` (XML views) aligns with MVC principles.*
* **Integration:** Consumes a SOAP API provided by the backend.

## Features

* **User Authentication:**
    * Register a new user account.
    * Log in with existing credentials.
* **Task Management:**
    * View a list of tasks assigned to the logged-in user.
    * Create new tasks with title, description, and priority (LOW, MEDIUM, HIGH).
    * Update task status (PENDING -> IN_PROGRESS -> COMPLETED).
    * Delete existing tasks.
* **User Interface:**
    * Displays tasks in a RecyclerView.
    * Shows task details including title, description, status, and priority.
    * Provides visual indicators for task priority and completion status.
    * Includes specific buttons to change task status based on the current state.
    * Handles empty states when no tasks are available.
    * Uses progress bars during network operations.

## Technical Details

* **UI:** Android XML Layouts, RecyclerView for lists.
* **Asynchronous Operations:** Kotlin Coroutines (lifecycleScope).
* **Networking:** Communicates with the backend SOAP service using ksoap2-android library via `SoapRepository`.
* **Structure:**
    * `controller`: Activities (`LoginActivity`, `RegisterActivity`, `TaskListActivity`) and Adapters (`TaskAdapter`).
    * `model`: Data classes (`User`, `Task`, `TaskStatus`, `TaskPriority`) and `SoapRepository` for backend communication.

## Setup

1.  Ensure the backend Nimble SOAP service is running on a Glassfish server and is accessible.
2.  Update the `USER_URL` and `TASK_URL` constants in `model/SoapRepository.kt` to point to the correct backend address. (Note: Currently uses `http://10.0.2.2:8080/...` which is typical for accessing localhost from an Android emulator).
3.  Build and run the application on an Android device or emulator.

## Key Files

* `controller/LoginActivity.kt`: Handles user login.
* `controller/RegisterActivity.kt`: Handles new user registration.
* `controller/TaskListActivity.kt`: Displays and manages the user's tasks.
* `controller/TaskAdapter.kt`: Adapts task data for display in the RecyclerView.
* `model/SoapRepository.kt`: Manages all communication with the backend SOAP API.
* `model/User.kt`, `model/Task.kt`, `model/TaskStatus.kt`, `model/TaskPriority.kt`: Data model classes.
