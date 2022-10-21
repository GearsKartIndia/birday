package com.minar.birday.fragments

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.findNavController
import androidx.preference.PreferenceManager
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.customview.customView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.minar.birday.R
import com.minar.birday.activities.MainActivity
import com.minar.birday.adapters.FavoritesAdapter
import com.minar.birday.databinding.DialogNotesBinding
import com.minar.birday.databinding.DialogStatsBinding
import com.minar.birday.databinding.FragmentFavoritesBinding
import com.minar.birday.model.Event
import com.minar.birday.model.EventResult
import com.minar.birday.utilities.StatsGenerator
import com.minar.birday.utilities.applyLoopingAnimatedVectorDrawable
import com.minar.birday.utilities.getRemainingDays
import com.minar.birday.utilities.isBirthday
import com.minar.birday.viewmodels.MainViewModel
import java.time.LocalDate
import java.util.*
import kotlin.math.min


@ExperimentalStdlibApi
class FavoritesFragment : Fragment() {
    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: FavoritesAdapter
    private lateinit var fullStats: SpannableStringBuilder
    private lateinit var act: MainActivity
    private var _binding: FragmentFavoritesBinding? = null
    private val binding get() = _binding!!
    private var _dialogStatsBinding: DialogStatsBinding? = null
    private val dialogStatsBinding get() = _dialogStatsBinding!!
    private var _dialogNotesBinding: DialogNotesBinding? = null
    private val dialogNotesBinding get() = _dialogNotesBinding!!
    private var totalEvents = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = FavoritesAdapter { item -> onItemClick(item) }
        act = activity as MainActivity
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoritesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val statsImage = binding.statsImage
        val shimmer = binding.favoritesCardShimmer
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val shimmerEnabled = sharedPrefs.getBoolean("shimmer", false)
        val favoriteMotionLayout = binding.favoritesMain
        val favoritesCard = binding.favoritesCard
        val favoritesMiniFab = binding.favoritesMiniFab
        val overviewButton = binding.overviewButton
        if (shimmerEnabled) shimmer.startShimmer()
        statsImage.applyLoopingAnimatedVectorDrawable(R.drawable.animated_candle)

        // Set motion layout state, since it's saved
        favoriteMotionLayout.progress = sharedPrefs.getFloat("favorite_motion_state", 0.0F)

        // Vibration on the mini fab (with manual managing of the transition)
        favoritesMiniFab.setOnClickListener {
            act.vibrate()
            when (favoriteMotionLayout.progress) {
                0.0F -> {
                    favoriteMotionLayout.transitionToEnd()
                    sharedPrefs.edit().putFloat("favorite_motion_state", 1.0F).apply()
                }
                1.0F -> {
                    favoriteMotionLayout.transitionToStart()
                    sharedPrefs.edit().putFloat("favorite_motion_state", 0.0F).apply()
                }
            }
        }

        // Activate the overscroll effect on Android 12 and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            binding.favoritesRecycler.overScrollMode = View.OVER_SCROLL_ALWAYS
        }

        // Show full stats on long press too
        favoritesMiniFab.setOnLongClickListener {
            if (favoriteMotionLayout.progress == 1.0F) showStatsSheet()
            true
        }

        // Show full stats in a bottom sheet
        favoritesCard.setOnClickListener {
            showStatsSheet()
        }

        // Setup the recycler view
        binding.favoritesRecycler.adapter = adapter
        with(mainViewModel) {
            getFavorites().observe(viewLifecycleOwner) { events ->
                // Update the cached copy in the adapter
                if (events != null && events.isNotEmpty()) {
                    removePlaceholder()
                    adapter.submitList(events)
                }
            }
        }

        // Set the overview button TODO Only available in experimental settings
        if (sharedPrefs.getBoolean("feature_preview", false))
            overviewButton.setOnClickListener {
                // Vibrate and navigate to the overview screen
                act.vibrate()
                requireView().findNavController()
                    .navigate(R.id.action_navigationFavorites_to_overviewFragment)
            }

        // Set the data which requires the complete and unfiltered event list
        with(binding) {
            mainViewModel.allEventsUnfiltered.observe(viewLifecycleOwner) { events ->
                // Stats - Under a minimum size, no stats will be shown (at least 5 birthdays containing a year)
                if (events.filter { it.yearMatter == true && isBirthday(it) }.size > 4) generateStat(
                    events
                )
                else fullStats = SpannableStringBuilder(
                    requireActivity().applicationContext.getString(
                        R.string.no_stats_description
                    )
                )
                totalEvents = events.size

                // Quick glance - alpha set to .3 for 1 event, .6 for 2 events, 1 for 3+ events
                if (events != null) {
                    val today = LocalDate.now()
                    val nextDays = buildList {
                        for (i in 0..9L) this.add(today.plusDays(i).dayOfMonth)
                    }

                    // Prepare the dots
                    val accent = act.getThemeColor(R.attr.colorAccent)
                    overviewDot1.setColorFilter(accent, android.graphics.PorterDuff.Mode.SRC_IN)
                    overviewText1.text = nextDays[0].toString()
                    overviewDot2.setColorFilter(accent, android.graphics.PorterDuff.Mode.SRC_IN)
                    overviewText2.text = nextDays[1].toString()
                    overviewDot3.setColorFilter(accent, android.graphics.PorterDuff.Mode.SRC_IN)
                    overviewText3.text = nextDays[2].toString()
                    overviewDot4.setColorFilter(accent, android.graphics.PorterDuff.Mode.SRC_IN)
                    overviewText4.text = nextDays[3].toString()
                    overviewDot5.setColorFilter(accent, android.graphics.PorterDuff.Mode.SRC_IN)
                    overviewText5.text = nextDays[4].toString()
                    overviewDot6.setColorFilter(accent, android.graphics.PorterDuff.Mode.SRC_IN)
                    overviewText6.text = nextDays[5].toString()
                    overviewDot7.setColorFilter(accent, android.graphics.PorterDuff.Mode.SRC_IN)
                    overviewText7.text = nextDays[6].toString()
                    overviewDot8.setColorFilter(accent, android.graphics.PorterDuff.Mode.SRC_IN)
                    overviewText8.text = nextDays[7].toString()
                    overviewDot9.setColorFilter(accent, android.graphics.PorterDuff.Mode.SRC_IN)
                    overviewText9.text = nextDays[8].toString()
                    overviewDot10.setColorFilter(accent, android.graphics.PorterDuff.Mode.SRC_IN)
                    overviewText10.text = nextDays[9].toString()

                    // Set the opacities
                    overviewDot1.alpha = .0F
                    overviewDot2.alpha = .0F
                    overviewDot3.alpha = .0F
                    overviewDot4.alpha = .0F
                    overviewDot5.alpha = .0F
                    overviewDot6.alpha = .0F
                    overviewDot7.alpha = .0F
                    overviewDot8.alpha = .0F
                    overviewDot9.alpha = .0F
                    overviewDot10.alpha = .0F
                    for (event in events) {
                        when (getRemainingDays(event.nextDate!!)) {
                            0 -> overviewDot1.alpha += .30F
                            1 -> overviewDot2.alpha += .30F
                            2 -> overviewDot3.alpha += .30F
                            3 -> overviewDot4.alpha += .30F
                            4 -> overviewDot5.alpha += .30F
                            5 -> overviewDot6.alpha += .30F
                            6 -> overviewDot7.alpha += .30F
                            7 -> overviewDot8.alpha += .30F
                            8 -> overviewDot9.alpha += .30F
                            9 -> overviewDot10.alpha += .30F
                            else -> continue
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Reset each binding to null to follow the best practice
        _binding = null
        _dialogStatsBinding = null
        _dialogNotesBinding = null
    }

    private fun onItemClick(position: Int) {
        act.vibrate()
        _dialogNotesBinding = DialogNotesBinding.inflate(LayoutInflater.from(context))
        val event = adapter.getItem(position)
        val notesTitle = "${getString(R.string.notes)} - ${event.name}"
        val noteTextField = dialogNotesBinding.favoritesNotes
        noteTextField.setText(event.notes)

        // Native dialog
        MaterialAlertDialogBuilder(act)
            .setTitle(notesTitle)
            .setIcon(R.drawable.ic_note_24dp)
            .setPositiveButton(resources.getString(android.R.string.ok)) { dialog, _ ->
                val note = noteTextField.text.toString().trim()
                val tuple = Event(
                    id = event.id,
                    type = event.type,
                    originalDate = event.originalDate,
                    name = event.name,
                    yearMatter = event.yearMatter,
                    surname = event.surname,
                    favorite = event.favorite,
                    notes = note,
                    image = event.image
                )
                mainViewModel.update(tuple)
                dialog.dismiss()
            }
            .setNegativeButton(resources.getString(android.R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .setView(dialogNotesBinding.root)
            .show()
    }

    // Remove the placeholder or return if the placeholder was already removed before
    private fun removePlaceholder() {
        val placeholder = binding.noFavorites
        placeholder.visibility = View.GONE
    }

    // Show a bottom sheet containing the stats
    private fun showStatsSheet() {
        act.vibrate()
        _dialogStatsBinding = DialogStatsBinding.inflate(LayoutInflater.from(context))
        MaterialDialog(requireContext(), BottomSheet(LayoutMode.WRAP_CONTENT)).show {
            cornerRadius(res = R.dimen.rounded_corners)
            title(R.string.stats_summary)
            icon(R.drawable.ic_stats_24dp)
            // Don't use scrollable here, instead use a nestedScrollView in the layout
            customView(view = dialogStatsBinding.root)
        }
        dialogStatsBinding.fullStats.text = fullStats
        // Prepare the toast
        var toast: Toast? = null
        // Display the total number of birthdays, start the animated drawable
        dialogStatsBinding.eventCounter.text = totalEvents.toString()
        val backgroundDrawable = dialogStatsBinding.eventCounterBackground
        // Link the opacity of the background to the number of events (min = 0.05 / max = 100)
        backgroundDrawable.alpha = min(0.01F * totalEvents + 0.05F, 1.0F)
        backgroundDrawable.applyLoopingAnimatedVectorDrawable(R.drawable.animated_counter_background)
        // Show an explanation for the counter, even if it's quite obvious
        backgroundDrawable.setOnClickListener {
            act.vibrate()
            toast?.cancel()
            @SuppressLint("ShowToast") // The toast is shown, stupid lint
            toast = Toast.makeText(
                context, resources.getQuantityString(
                    R.plurals.stats_total,
                    totalEvents,
                    totalEvents
                ), Toast.LENGTH_LONG
            )
            toast!!.show()
        }
    }

    // Use the generator to generate a random stat and display it
    private fun generateStat(events: List<EventResult>) {
        val cardSubtitle: TextView = binding.statsSubtitle
        val cardDescription: TextView = binding.statsDescription
        val generator = StatsGenerator(events, context)
        cardSubtitle.text = generator.generateRandomStat()
        fullStats = generator.generateFullStats()
        val summary = resources.getQuantityString(R.plurals.event, events.size, events.size)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        cardDescription.text = summary
    }
}
