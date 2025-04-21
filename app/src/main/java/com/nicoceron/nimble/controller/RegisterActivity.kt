package com.nicoceron.nimble.controller // <-- ENSURE THIS IS CORRECT

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nicoceron.nimble.R
import com.nicoceron.nimble.model.SoapRepository
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {


    private lateinit var usernameEditText: EditText
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var confirmPasswordEditText: EditText
    private lateinit var registerButton: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        usernameEditText = findViewById(R.id.editTextRegisterUsername)
        emailEditText = findViewById(R.id.editTextRegisterEmail)
        passwordEditText = findViewById(R.id.editTextRegisterPassword)
        confirmPasswordEditText = findViewById(R.id.editTextRegisterConfirmPassword)
        registerButton = findViewById(R.id.buttonRegisterSubmit)
        progressBar = findViewById(R.id.progressBarRegister)

        registerButton.setOnClickListener {
            performRegistration()
        }
    }

    private fun performRegistration() {
        val username = usernameEditText.text.toString().trim()
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString()
        val confirmPassword = confirmPasswordEditText.text.toString()

        // --- Basic Validation ---
        if (username.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show()
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Invalid email format", Toast.LENGTH_SHORT).show()
            return
        }
        if (password != confirmPassword) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
            return
        }
        if (password.length < 6) { // Example minimum length
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
            return
        }
        // --- End Validation ---

        progressBar.visibility = View.VISIBLE
        registerButton.isEnabled = false

        lifecycleScope.launch {
            val result = SoapRepository.registerUser(username, email, password)

            progressBar.visibility = View.GONE
            registerButton.isEnabled = true

            result.onSuccess { registeredUser ->
                if (registeredUser?.userId != null) {
                    Toast.makeText(this@RegisterActivity, "Registration successful! Please log in.", Toast.LENGTH_LONG).show()
                    finish() // Close RegisterActivity
                } else {
                    Toast.makeText(this@RegisterActivity, "Registration failed. Username or email might already exist.", Toast.LENGTH_LONG).show()
                }
            }.onFailure { exception ->
                Toast.makeText(this@RegisterActivity, "Registration failed: ${exception.message}", Toast.LENGTH_LONG).show()
                exception.printStackTrace()
            }
        }
    }
}