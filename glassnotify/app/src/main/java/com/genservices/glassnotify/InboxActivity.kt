package com.genservices.glassnotify

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Simple history screen: newest first, search box, clear button. Built in code (no layout file). */
class InboxActivity : Activity() {
    private var all: List<History.Item> = emptyList()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var search: EditText
    private val fmt = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16f), dp(24f), dp(16f), dp(16f))

        search = EditText(this)
        search.hint = "Search history"
        search.setTextColor(Color.WHITE)
        search.setHintTextColor(Color.argb(120, 255, 255, 255))
        col.addView(search)

        val clear = Button(this)
        clear.text = "Clear all history"
        clear.isAllCaps = false
        col.addView(clear)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList<String>())
        val list = ListView(this)
        list.adapter = adapter
        col.addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(col)

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { render() }
        })
        clear.setOnClickListener { History.clear(this); all = emptyList(); render() }

        History.load(this) { items -> all = items; render() }
    }

    private fun render() {
        val q = search.text.toString().trim().lowercase()
        val rows = all
            .filter { q.isEmpty() || it.title.lowercase().contains(q) || it.text.lowercase().contains(q) || it.app.lowercase().contains(q) }
            .map { "${fmt.format(Date(it.time))} · ${it.app}\n" + (if (it.title.isEmpty()) "" else it.title + ": ") + it.text }
        adapter.clear()
        adapter.addAll(rows)
        adapter.notifyDataSetChanged()
    }
}
