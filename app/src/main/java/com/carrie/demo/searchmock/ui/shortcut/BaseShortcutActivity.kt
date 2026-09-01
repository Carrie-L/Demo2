package com.carrie.demo.searchmock.ui.shortcut

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.carrie.demo.R

abstract class BaseShortcutActivity : AppCompatActivity() {
    protected abstract val pageTitle: String
    protected abstract val pageDescription: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shortcut)
        findViewById<TextView>(R.id.shortcut_title).text = pageTitle
        findViewById<TextView>(R.id.shortcut_description).text = pageDescription
    }
}

