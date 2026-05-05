package com.robert.qalarm

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

// No longer used — OAuth redirect replaced by direct token entry.
class DropboxAuthActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
