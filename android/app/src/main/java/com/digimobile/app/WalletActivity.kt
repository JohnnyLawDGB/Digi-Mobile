package com.digimobile.app

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class WalletActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet)
        findViewById<Button>(R.id.buttonSend).setOnClickListener {
            Toast.makeText(this, "Send feature coming soon!", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.buttonReceive).setOnClickListener {
            Toast.makeText(this, "Receive feature coming soon!", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.buttonTransactions).setOnClickListener {
            Toast.makeText(this, "Transaction history coming soon!", Toast.LENGTH_SHORT).show()
        }
    }
}
