package com.carrie.demo.searchmock.ui.shortcut

import android.app.Activity
import android.widget.TextView
import com.carrie.demo.R

internal fun Activity.renderShortcutPage(title: String, description: String) {
    setContentView(R.layout.activity_shortcut)
    findViewById<TextView>(R.id.shortcut_title).text = title
    findViewById<TextView>(R.id.shortcut_description).text = description
}
