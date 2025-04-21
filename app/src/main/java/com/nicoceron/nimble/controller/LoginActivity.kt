// controller/LoginActivity.kt <-- Optional: Update comment to reflect location
package com.nicoceron.nimble.controller // <-- CHANGE THIS LINE

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nicoceron.nimble.R
// No need to import RegisterActivity if it's in the same package
// import com.nicoceron.nimble.controller.RegisterActivity // Correct path if needed, but usually not required for same package
import com.nicoceron.nimble.controller.TaskListActivity
import com.nicoceron.nimble.model.SoapRepository
import com.nicoceron.nimble.model.User
import kotlinx.coroutines.launch

// Make sure the class declaration itself is correct
class LoginActivity : AppCompatActivity() {

    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var registerButton: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        usernameEditText = findViewById(R.id.editTextUsername)
        passwordEditText = findViewById(R.id.editTextPassword)
        loginButton = findViewById(R.id.buttonLogin)
        registerButton = findViewById(R.id.buttonGoToRegister)
        progressBar = findViewById(R.id.progressBarLogin)

        // --- Login Button Listener ---
        loginButton.setOnClickListener {
            val username = usernameEditText.text.toString().trim()
            val password = passwordEditText.text.toString()

            if (username.isNotEmpty() && password.isNotEmpty()) {
                performLogin(username, password)
            } else {
                Toast.makeText(this, "Please enter username and password", Toast.LENGTH_SHORT).show()
            }
        }

        // --- Register Button Listener ---
        registerButton.setOnClickListener {
            Log.d("LoginActivity", "Register button clicked!")
            // The Intent should point to the correct class in the controller package
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }

    private fun performLogin(username: String, password: String) {
        progressBar.visibility = View.VISIBLE
        loginButton.isEnabled = false
        registerButton.isEnabled = false

        lifecycleScope.launch {
            val result = SoapRepository.loginUser(username, password)

            progressBar.visibility = View.GONE
            loginButton.isEnabled = true
            registerButton.isEnabled = true

            result.onSuccess { user ->
                if (user?.userId != null) {
                    Toast.makeText(this@LoginActivity, "Login Successful! UserID: ${user.userId}", Toast.LENGTH_SHORT).show()
                    navigateToTaskList(user)
                } else {
                    Toast.makeText(this@LoginActivity, "Invalid username or password", Toast.LENGTH_LONG).show()
                }
            }.onFailure { exception ->
                Toast.makeText(this@LoginActivity, "Login failed: ${exception.message}", Toast.LENGTH_LONG).show()
                exception.printStackTrace()
            }
        }
    }

    private fun navigateToTaskList(user: User) {
        // Ensure TaskListActivity's package is also correct if it moved
        val intent = Intent(this, TaskListActivity::class.java)
        intent.putExtra("USER_ID", user.userId)
        startActivity(intent)
        finish()
    }
}