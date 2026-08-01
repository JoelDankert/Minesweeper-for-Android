package com.joeld.minesweeper

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.joeld.minesweeper.databinding.ActivityRecentGamesBinding
import com.joeld.minesweeper.databinding.ItemRecentGameBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecentGamesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRecentGamesBinding
    private lateinit var repository: PrefsRepository
    private lateinit var palette: ThemePalette
    private lateinit var settings: AppSettings
    private lateinit var adapter: RecentGamesAdapter
    private val entries = mutableListOf<RecentGameEntry>()
    private var loading = false
    private var hasMore = true

    override fun onCreate(savedInstanceState: Bundle?) {
        repository = PrefsRepository(this)
        settings = repository.loadSettings()
        AppCompatDelegate.setDefaultNightMode(settings.nightMode())
        super.onCreate(savedInstanceState)
        binding = ActivityRecentGamesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        palette = ThemeCatalog.resolve(settings.themeId, settings.usesDarkPalette(this), settings.usesAmoledPalette(this))
        adapter = RecentGamesAdapter()

        setupInsets()
        applyPalette()
        binding.backButton.setOnClickListener { finish() }
        binding.recentRecycler.layoutManager = LinearLayoutManager(this)
        binding.recentRecycler.adapter = adapter
        binding.recentRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val layout = recyclerView.layoutManager as? LinearLayoutManager ?: return
                if (hasMore && !loading && layout.findLastVisibleItemPosition() >= entries.size - LOAD_AHEAD) {
                    loadMore()
                }
            }
        })
        loadMore()
    }

    private fun setupInsets() {
        val topPadding = binding.topBar.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.topBar.setPadding(20.dp, topPadding + bars.top, 20.dp, 8.dp)
            binding.recentRecycler.setPadding(20.dp, 12.dp, 20.dp, 20.dp + bars.bottom)
            insets
        }
    }

    private fun loadMore() {
        if (loading || !hasMore) return
        loading = true
        val next = repository.loadRecentGameEntries(entries.size, PAGE_SIZE)
        val insertAt = entries.size
        entries += next
        hasMore = next.size == PAGE_SIZE
        loading = false
        adapter.notifyItemRangeInserted(insertAt, next.size)
        updateEmptyState()
    }

    private fun removeEntry(position: Int) {
        val entry = entries.getOrNull(position) ?: return
        repository.deleteRecentGame(entry.record)
        entries.removeAt(position)
        adapter.notifyItemRemoved(position)
        adapter.notifyItemRangeChanged(position, entries.size - position)
        if (hasMore && entries.size < PAGE_SIZE) loadMore()
        updateEmptyState()
    }

    private fun updateEmptyState() {
        binding.emptyText.visibility = if (entries.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun applyPalette() {
        binding.root.setBackgroundColor(palette.background)
        binding.topBar.setBackgroundColor(palette.background)
        binding.titleText.setTextColor(palette.ink)
        binding.emptyText.setTextColor(palette.inkSoft)
        binding.backButton.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(palette.panel)
        }
        binding.backButton.imageTintList = ColorStateList.valueOf(palette.ink)
    }

    private fun formatElapsed(seconds: Int): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return if (minutes == 0) "${remainingSeconds}s" else "${minutes}m ${remainingSeconds.toString().padStart(2, '0')}s"
    }

    private fun formatDate(epochMs: Long): String {
        return dateFormat.format(Date(epochMs))
    }

    private inner class RecentGamesAdapter : RecyclerView.Adapter<RecentGamesAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val rowBinding = ItemRecentGameBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(rowBinding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(entries[position])
        }

        override fun getItemCount(): Int = entries.size

        inner class ViewHolder(private val rowBinding: ItemRecentGameBinding) : RecyclerView.ViewHolder(rowBinding.root) {
            fun bind(entry: RecentGameEntry) {
                rowBinding.recentGameRow.background = GradientDrawable().apply {
                    cornerRadius = 16f.dpF
                    setColor(palette.panel)
                }
                rowBinding.dateText.text = formatDate(entry.record.finishedAtEpochMs)
                rowBinding.dateText.setTextColor(palette.ink)
                val modeText = listOf(entry.modeName, entry.modeMeta)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(" · ")
                rowBinding.modeText.text = ModeTextFormatter.styleDensityInText(modeText, settings, palette, palette.inkSoft)
                rowBinding.modeText.setTextColor(palette.inkSoft)
                rowBinding.timeText.text = formatElapsed(entry.record.elapsedSeconds)
                rowBinding.timeText.setTextColor(palette.ink)
                rowBinding.deleteButton.background = null
                rowBinding.deleteButton.imageTintList = ColorStateList.valueOf(Color.parseColor("#D94B4B"))
                rowBinding.deleteButton.setOnClickListener {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) removeEntry(position)
                }
            }
        }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    private val Float.dpF: Float
        get() = this * resources.displayMetrics.density

    private companion object {
        const val PAGE_SIZE = 24
        const val LOAD_AHEAD = 6
        val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    }
}
