package com.example.birthdaywidget

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.birthdaywidget.data.BirthdayWidgetStateStore
import com.example.birthdaywidget.network.ContactPhotoCache
import com.example.birthdaywidget.work.BirthdaySyncWorker
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.api.services.people.v1.PeopleServiceScopes
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        if (task.isSuccessful) {
            updateUi(task.result)
            BirthdaySyncWorker.enqueueImmediate(this)
        } else {
            updateUi(null)
        }
    }

    private lateinit var signInButton: SignInButton
    private lateinit var signOutButton: Button
    private lateinit var refreshButton: Button
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        signInButton = findViewById(R.id.signInButton)
        signOutButton = findViewById(R.id.signOutButton)
        refreshButton = findViewById(R.id.refreshButton)
        statusText = findViewById(R.id.statusText)

        signInButton.setSize(SignInButton.SIZE_WIDE)
        signInButton.setOnClickListener { launchSignIn() }
        signOutButton.setOnClickListener { signOut() }
        refreshButton.setOnClickListener { BirthdaySyncWorker.enqueueImmediate(this) }

        updateUi(GoogleSignIn.getLastSignedInAccount(this))
    }

    private fun launchSignIn() {
        val client = googleSignInClient()
        val intent: Intent = client.signInIntent
        signInLauncher.launch(intent)
    }

    private fun signOut() {
        val client = googleSignInClient()
        client.signOut().addOnCompleteListener {
            lifecycleScope.launch {
                ContactPhotoCache(applicationContext).clear()
                BirthdayWidgetStateStore.clear(this@MainActivity)
                updateUi(null)
            }
        }
    }

    private fun updateUi(account: GoogleSignInAccount?) {
        if (account != null) {
            statusText.text = getString(R.string.status_signed_in, account.email)
            signInButton.visibility = View.GONE
            signOutButton.visibility = View.VISIBLE
            refreshButton.visibility = View.VISIBLE
        } else {
            statusText.text = getString(R.string.status_signed_out)
            signInButton.visibility = View.VISIBLE
            signOutButton.visibility = View.GONE
            refreshButton.visibility = View.GONE
        }
    }

    private fun googleSignInClient(): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(com.google.android.gms.common.api.Scope(PeopleServiceScopes.CONTACTS_READONLY))
            .build()
        return GoogleSignIn.getClient(this, options)
    }
}
