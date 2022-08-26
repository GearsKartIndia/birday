package com.minar.birday.fragments

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ShareCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.preference.PreferenceManager
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.WhichButton
import com.afollestad.materialdialogs.actions.getActionButton
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.customview.customView
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.transition.MaterialContainerTransform
import com.minar.birday.R
import com.minar.birday.activities.MainActivity
import com.minar.birday.databinding.DialogInsertEventBinding
import com.minar.birday.databinding.DialogNotesBinding
import com.minar.birday.databinding.FragmentDetailsBinding
import com.minar.birday.model.Event
import com.minar.birday.model.EventCode
import com.minar.birday.model.EventResult
import com.minar.birday.utilities.*
import com.minar.birday.viewmodels.MainViewModel
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*


@ExperimentalStdlibApi
class DetailsFragment : Fragment() {
    private lateinit var act: MainActivity
    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var sharedPrefs: SharedPreferences
    private val args: DetailsFragmentArgs by navArgs()
    private var _binding: FragmentDetailsBinding? = null
    private val binding get() = _binding!!
    private var _dialogInsertEventBinding: DialogInsertEventBinding? = null
    private val dialogInsertEventBinding get() = _dialogInsertEventBinding!!
    private lateinit var resultLauncher: ActivityResultLauncher<String>
    private var imageChosen = false
    private var easterEggCounter = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Recognize the image from the row of the recycler and animate the transition accordingly
        val animation = MaterialContainerTransform()
        animation.duration = 400
        animation.fadeMode = MaterialContainerTransform.FADE_MODE_THROUGH
        animation.startElevation = 0f
        animation.endElevation = 0f
        animation.setAllContainerColors((activity as MainActivity).getThemeColor(R.attr.backgroundColor))
        animation.scrimColor = (activity as MainActivity).getThemeColor(R.attr.backgroundColor)
        animation.isElevationShadowEnabled = false
        sharedElementEnterTransition = animation

        act = activity as MainActivity
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        // Initialize the result launcher to pick the image
        resultLauncher =
            registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                // Handle the returned Uri
                if (uri != null) {
                    imageChosen = true
                    setImage(uri)
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Reset the binding to null to follow the best practice
        _binding = null
        _dialogInsertEventBinding = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()
        val event = args.event
        val position = args.position

        val fullView = binding.detailsMotionLayout
        val shimmer = binding.detailsCountdownShimmer
        val shimmerEnabled = sharedPrefs.getBoolean("shimmer", false)
        val titleText = "${getString(R.string.event_details)} - ${event.name}"
        val title = binding.detailsEventName
        val image = binding.detailsEventImage
        val imageBg = binding.detailsEventImageBackground
        val deleteButton = binding.detailsDeleteButton
        val editButton = binding.detailsEditButton
        val shareButton = binding.detailsShareButton
        val notesButton = binding.detailsNotesButton

        // Manage the shimmer
        if (shimmerEnabled) {
            shimmer.startShimmer()
            shimmer.showShimmer(true)
        }

        // Bind the data on the views and set the transition name, to play it in reverse
        title.text = titleText
        val hideImage = sharedPrefs.getBoolean("hide_images", false)
        if (hideImage) {
            image.visibility = View.GONE
            imageBg.visibility = View.GONE
            ViewCompat.setTransitionName(fullView, "shared_full_view$position")
        } else {
            ViewCompat.setTransitionName(image, "shared_image$position")
            ViewCompat.setTransitionName(title, "shared_title$position")
            if (event.image != null)
                image.setImageBitmap(byteArrayToBitmap(event.image))
            else {
                image.setImageDrawable(
                    ContextCompat.getDrawable(
                        requireContext(),
                        // Set the image depending on the event type
                        when (event.type) {
                            EventCode.BIRTHDAY.name -> R.drawable.placeholder_birthday_image
                            EventCode.ANNIVERSARY.name -> R.drawable.placeholder_anniversary_image
                            EventCode.DEATH.name -> R.drawable.placeholder_death_image
                            EventCode.NAME_DAY.name -> R.drawable.placeholder_name_day_image
                            else -> R.drawable.placeholder_other_image
                        }
                    )
                )
            }
            imageBg.applyLoopingAnimatedVectorDrawable(R.drawable.animated_ripple_circle)
        }

        // Small easter egg/motion on the image (with a slight zoom)
        image.setOnClickListener {
            easterEggCounter++
            if (easterEggCounter == 3) {
                easterEggCounter = 0
                if (binding.detailsMotionLayout.progress == 0F)
                    binding.detailsMotionLayout.transitionToEnd()
                else binding.detailsMotionLayout.transitionToStart()
            }
        }

        // Setup quick actions and corresponding navigation
        deleteButton.setOnClickListener {
            act.vibrate()
            deleteEvent(event)
            findNavController().popBackStack()
        }

        editButton.setOnClickListener {
            act.vibrate()
            editEvent(event)
        }

        shareButton.setOnClickListener {
            act.vibrate()
            shareEvent(event)
            findNavController().popBackStack()
        }

        // Manage the icon of the notes button (no notes / notes)
        if (event.notes.isNullOrBlank())
            (notesButton as MaterialButton).icon =
                AppCompatResources.getDrawable(act, R.drawable.ic_note_missing_24dp)

        notesButton.setOnClickListener {
            act.vibrate()
            val dialogNotesBinding = DialogNotesBinding.inflate(LayoutInflater.from(context))
            val notesTitle = "${getString(R.string.notes)} - ${event.name}"
            MaterialDialog(act).show {
                title(text = notesTitle)
                icon(R.drawable.ic_note_24dp)
                cornerRadius(res = R.dimen.rounded_corners)
                customView(view = dialogNotesBinding.root)
                val noteTextField = dialogNotesBinding.favoritesNotes
                noteTextField.setText(event.notes)
                negativeButton(R.string.cancel) {
                    dismiss()
                }
                positiveButton {
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
                    // Update locally (no livedata here)
                    event.notes = note
                    if (note.isBlank())
                        (notesButton as MaterialButton).icon =
                            AppCompatResources.getDrawable(act, R.drawable.ic_note_missing_24dp)
                    else
                        (notesButton as MaterialButton).icon =
                            AppCompatResources.getDrawable(act, R.drawable.ic_note_24dp)
                    dismiss()
                }
            }
        }

        val formatter: DateTimeFormatter =
            DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
        val subject: MutableList<EventResult> = mutableListOf()
        subject.add(event)
        val statsGenerator = StatsGenerator(subject, context)
        val daysCountdown =
            formatDaysRemaining(getRemainingDays(event.nextDate!!), requireContext())
        binding.detailsZodiacSignValue.text =
            statsGenerator.getZodiacSign(event)
        binding.detailsCountdown.text = daysCountdown

        // Manage the different event types
        if (event.type == (EventCode.BIRTHDAY.name)) {
            // Hide the age and the chinese sign and use a shorter birth date if the year is unknown
            if (!event.yearMatter!!) {
                binding.detailsNextAge.visibility = View.GONE
                binding.detailsNextAgeValue.visibility = View.GONE
                binding.detailsChineseSign.visibility = View.GONE
                binding.detailsChineseSignValue.visibility = View.GONE
                val reducedBirthDate = getReducedDate(event.originalDate)
                binding.detailsBirthDateValue.text = reducedBirthDate
            } else {
                binding.detailsNextAgeValue.text = getNextYears(event).toString()
                binding.detailsBirthDateValue.text =
                    event.originalDate.format(formatter)
                binding.detailsChineseSignValue.text =
                    statsGenerator.getChineseSign(event)
            }

            // Set the drawable of the zodiac sign
            when (statsGenerator.getZodiacSignNumber(event)) {
                0 -> binding.detailsClearBackground.setImageDrawable(
                    ContextCompat.getDrawable(
                        requireContext(), R.drawable.ic_zodiac_sagittarius
                    )
                )
                1 -> binding.detailsClearBackground.setImageDrawable(
                    ContextCompat.getDrawable(
                        requireContext(), R.drawable.ic_zodiac_capricorn
                    )
                )
                2 -> binding.detailsClearBackground.setImageDrawable(
                    ContextCompat.getDrawable(
                        requireContext(), R.drawable.ic_zodiac_aquarius
                    )
                )
                3 -> binding.detailsClearBackground.setImageDrawable(
                    ContextCompat.getDrawable(
                        requireContext(), R.drawable.ic_zodiac_pisces
                    )
                )
                4 -> binding.detailsClearBackground.setImageDrawable(
                    ContextCompat.getDrawable(
                        requireContext(), R.drawable.ic_zodiac_aries
                    )
                )
                5 -> binding.detailsClearBackground.setImageDrawable(
                    ContextCompat.getDrawable(
                        requireContext(), R.drawable.ic_zodiac_taurus
                    )
                )
                6 -> binding.detailsClearBackground.setImageDrawable(
                    ContextCompat.getDrawable(
                        requireContext(), R.drawable.ic_zodiac_gemini
                    )
                )
                7 -> binding.detailsClearBackground.setImageDrawable(
                    ContextCompat.getDrawable(
                        requireContext(), R.drawable.ic_zodiac_cancer
                    )
                )
                8 -> binding.detailsClearBackground.setImageDrawable(
                    ContextCompat.getDrawable(
                        requireContext(), R.drawable.ic_zodiac_leo
                    )
                )
                9 -> binding.detailsClearBackground.setImageDrawable(
                    ContextCompat.getDrawable(
                        requireContext(), R.drawable.ic_zodiac_virgo
                    )
                )
                10 -> binding.detailsClearBackground.setImageDrawable(
                    ContextCompat.getDrawable(
                        requireContext(), R.drawable.ic_zodiac_libra
                    )
                )
                11 -> binding.detailsClearBackground.setImageDrawable(
                    ContextCompat.getDrawable(
                        requireContext(), R.drawable.ic_zodiac_scorpio
                    )
                )
            }
        } else {
            // Set the drawable of the event type
            when (event.type) {
                EventCode.ANNIVERSARY.name -> binding.detailsClearBackground.setImageDrawable(
                    ContextCompat.getDrawable(
                        requireContext(), R.drawable.ic_anniversary_24dp
                    )
                )
                EventCode.DEATH.name -> {
                    binding.detailsClearBackground.setImageDrawable(
                        ContextCompat.getDrawable(
                            requireContext(), R.drawable.ic_death_anniversary_24dp
                        )
                    )
                    binding.detailsEventNameImage.setImageDrawable(
                        ContextCompat.getDrawable(
                            requireContext(),
                            R.drawable.ic_candle_24dp
                        )
                    )
                }
                EventCode.NAME_DAY.name -> binding.detailsClearBackground.setImageDrawable(
                    ContextCompat.getDrawable(
                        requireContext(), R.drawable.ic_name_day_24dp
                    )
                )
                EventCode.OTHER.name -> binding.detailsClearBackground.setImageDrawable(
                    ContextCompat.getDrawable(
                        requireContext(), R.drawable.ic_other_24dp
                    )
                )
            }
            if (event.yearMatter!!) {
                binding.detailsNextAgeValue.text = String.format(
                    resources.getQuantityString(R.plurals.years, getNextYears(event)),
                    getNextYears(event)
                )
            } else binding.detailsNextAgeValue.visibility = View.GONE
            binding.detailsBirthDateValue.text =
                getStringForTypeCodename(requireContext(), event.type!!)
            binding.detailsBirthDate.visibility = View.INVISIBLE
            binding.detailsNextAge.visibility = View.GONE
            binding.detailsZodiacSign.visibility = View.GONE
            binding.detailsZodiacSignValue.visibility = View.GONE
            binding.detailsClearBackground.visibility = View.GONE
            binding.detailsChineseSign.visibility = View.GONE
            binding.detailsChineseSignValue.visibility = View.GONE
        }
        startPostponedEnterTransition()
    }

    // Delete an existing event and show a snackbar
    private fun deleteEvent(eventResult: EventResult) {
        mainViewModel.delete(resultToEvent(eventResult))
        act.showSnackbar(
            requireContext().getString(R.string.deleted),
            actionText = requireContext().getString(R.string.cancel),
            action = fun() = insertBack(eventResult),
        )
    }

    // Insert a previously deleted event back in the database
    private fun insertBack(eventResult: EventResult) {
        mainViewModel.insert(resultToEvent(eventResult))
    }

    @ExperimentalStdlibApi
    private fun editEvent(eventResult: EventResult) {
        _dialogInsertEventBinding = DialogInsertEventBinding.inflate(LayoutInflater.from(context))
        imageChosen = false
        val typeLabel = getStringForTypeCodename(act, eventResult.type!!)
        var typeValue = eventResult.type
        var nameValue = eventResult.name
        var surnameValue = eventResult.surname
        var countYearValue = eventResult.yearMatter
        var eventDateValue: LocalDate = eventResult.originalDate
        val dialog = MaterialDialog(act, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
            cornerRadius(res = R.dimen.rounded_corners)
            title(R.string.edit_event)
            icon(R.drawable.ic_edit_24dp)
            customView(view = dialogInsertEventBinding.root)
            positiveButton(R.string.update_event) {
                val image = if (imageChosen)
                    bitmapToByteArray(dialogInsertEventBinding.imageEvent.drawable.toBitmap())
                else eventResult.image
                // Use the data to create an event object and update the db
                val tuple = Event(
                    id = eventResult.id,
                    type = typeValue,
                    originalDate = eventDateValue,
                    name = nameValue.smartFixName(),
                    yearMatter = countYearValue,
                    surname = surnameValue?.smartFixName(),
                    favorite = eventResult.favorite,
                    notes = eventResult.notes,
                    image = image
                )
                mainViewModel.update(tuple)
                dismiss()
                // Go back to the first screen to avoid updating the displayed details
                findNavController().popBackStack()
            }
            negativeButton(R.string.cancel) {
                dismiss()
            }
        }

        // Setup listeners and checks on the fields
        dialog.getActionButton(WhichButton.POSITIVE).isEnabled = true
        val type = dialogInsertEventBinding.typeEvent
        val name = dialogInsertEventBinding.nameEvent
        val surname = dialogInsertEventBinding.surnameEvent
        val eventDate = dialogInsertEventBinding.dateEvent
        val countYear = dialogInsertEventBinding.countYearSwitch
        val eventImage = dialogInsertEventBinding.imageEvent
        type.setText(typeLabel, false)
        name.setText(nameValue)
        surname.setText(surnameValue)
        countYear.isChecked = countYearValue!!
        val formatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        eventDate.setText(eventDateValue.format(formatter))
        setEventImageOrPlaceholder(eventResult, eventImage)

        // Calendar setup. The end date is the last day in the following year (dumb users)
        val startDate = Calendar.getInstance()
        val endDate = Calendar.getInstance()
        endDate.set(Calendar.YEAR, endDate.get(Calendar.YEAR) + 1)
        endDate.set(Calendar.DAY_OF_YEAR, endDate.getActualMaximum(Calendar.DAY_OF_YEAR))
        startDate.set(1500, 1, 1)
        var dateDialog: MaterialDatePicker<Long>? = null

        // To automatically show the last selected date, parse it to another Calendar object
        val lastDate = Calendar.getInstance()
        lastDate.set(eventDateValue.year, eventDateValue.monthValue - 1, eventDateValue.dayOfMonth)

        // Update the boolean value on each click
        countYear.setOnCheckedChangeListener { _, isChecked ->
            countYearValue = isChecked
        }

        eventImage.setOnClickListener {
            resultLauncher.launch("image/*")
        }

        eventDate.setOnClickListener {
            // Prevent double dialogs on fast click
            if (dateDialog == null) {
                // Build constraints
                val constraints =
                    CalendarConstraints.Builder()
                        .setStart(startDate.timeInMillis)
                        .setEnd(endDate.timeInMillis)
                        .build()

                // Build the dialog itself
                dateDialog =
                    MaterialDatePicker.Builder.datePicker()
                        .setTitleText(R.string.insert_date_hint)
                        .setSelection(lastDate.timeInMillis)
                        .setCalendarConstraints(constraints)
                        .build()

                // The user pressed ok
                dateDialog!!.addOnPositiveButtonClickListener {
                    val selection = it
                    if (selection != null) {
                        val date = Calendar.getInstance()
                        // Use a standard timezone to avoid wrong date on different time zones
                        date.timeZone = TimeZone.getTimeZone("UTC")
                        date.timeInMillis = selection
                        val year = date.get(Calendar.YEAR)
                        val month = date.get(Calendar.MONTH) + 1
                        val day = date.get(Calendar.DAY_OF_MONTH)
                        eventDateValue = LocalDate.of(year, month, day)
                        val todayDate = LocalDate.now()

                        // Force the date to be before today programmatically
                        while (eventDateValue.isAfter(todayDate)) {
                            eventDateValue = LocalDate.of(
                                todayDate.year - 1,
                                eventDateValue.monthValue,
                                eventDateValue.dayOfMonth
                            )
                        }
                        eventDate.setText(eventDateValue.format(formatter))
                        // The last selected date is saved if the dialog is reopened
                        lastDate.set(eventDateValue.year, month - 1, day)
                    }

                }
                // Show the picker and wait to reset the variable
                dateDialog!!.show(parentFragmentManager, "home_picker")
                Handler(Looper.getMainLooper()).postDelayed({ dateDialog = null }, 750)
            }
        }

        // Set the dropdown to show the available event types
        val items = getAvailableTypes(requireContext())
        val adapter = ArrayAdapter(requireContext(), R.layout.event_type_list_item, items)
        with(type) {
            setAdapter(adapter)
            onItemClickListener =
                AdapterView.OnItemClickListener { _, _, position, _ ->
                    typeValue = items[position].codeName.name
                    if (!imageChosen && (eventResult.image == null || eventResult.image.isEmpty()))
                        eventImage.setImageDrawable(
                            ContextCompat.getDrawable(
                                context,
                                // Set the image depending on the event type
                                when (typeValue) {
                                    EventCode.BIRTHDAY.name -> R.drawable.placeholder_birthday_image
                                    EventCode.ANNIVERSARY.name -> R.drawable.placeholder_anniversary_image
                                    EventCode.DEATH.name -> R.drawable.placeholder_death_image
                                    EventCode.NAME_DAY.name -> R.drawable.placeholder_name_day_image
                                    else -> R.drawable.placeholder_other_image
                                }
                            )
                        )
                }
        }

        // Validate each field in the form with the same watcher
        var nameCorrect = true
        var surnameCorrect = true
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun afterTextChanged(editable: Editable) {
                when {
                    editable === name.editableText -> {
                        val nameText = name.text.toString()
                        if (nameText.isBlank() || !checkName(nameText)) {
                            dialogInsertEventBinding.nameEventLayout.error =
                                getString(R.string.invalid_value_name)
                            dialog.getActionButton(WhichButton.POSITIVE).isEnabled = false
                            nameCorrect = false
                        } else {
                            nameValue = nameText
                            dialogInsertEventBinding.nameEventLayout.error = null
                            nameCorrect = true
                        }
                    }
                    editable === surname.editableText -> {
                        val surnameText = surname.text.toString()
                        if (!checkName(surnameText)) {
                            dialogInsertEventBinding.surnameEventLayout.error =
                                getString(R.string.invalid_value_name)
                            dialog.getActionButton(WhichButton.POSITIVE).isEnabled = false
                            surnameCorrect = false
                        } else {
                            surnameValue = surnameText
                            dialogInsertEventBinding.surnameEventLayout.error = null
                            surnameCorrect = true
                        }
                    }
                }
                if (nameCorrect && surnameCorrect) dialog.getActionButton(WhichButton.POSITIVE).isEnabled =
                    true
            }
        }
        name.addTextChangedListener(watcher)
        surname.addTextChangedListener(watcher)
        eventDate.addTextChangedListener(watcher)
    }

    // Share an event as a plain string (plus some explanatory emotes) on every supported app
    private fun shareEvent(event: EventResult) {
        val formatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
        var typeEmoji = String(Character.toChars(0x1F973))
        when (event.type) {
            EventCode.ANNIVERSARY.name -> typeEmoji = String(Character.toChars(0x1F495))
            EventCode.DEATH.name -> typeEmoji = String(Character.toChars(0x1FAA6))
            EventCode.NAME_DAY.name -> typeEmoji = String(Character.toChars(0x1F607))
            EventCode.OTHER.name -> typeEmoji = String(Character.toChars(0x1F7E2))
        }
        val eventInformation =
            String(Character.toChars(0x1F388)) + "  " +
                    getString(R.string.notification_title) +
                    "\n" + typeEmoji + "  " +
                    formatName(event, sharedPrefs.getBoolean("surname_first", false)) +
                    " (" + getStringForTypeCodename(requireContext(), event.type!!) +
                    ")\n" + String(Character.toChars(0x1F56F)) + "  " +
                    event.nextDate!!.format(formatter) +
                    // Add a fourth line with the original date, if the year matters
                    if (event.yearMatter!!)
                        "\n" + String(Character.toChars(0x1F4C5)) + "  " +
                                event.originalDate.format(formatter)
                    else ""
        ShareCompat.IntentBuilder(requireActivity())
            .setText(eventInformation)
            .setType("text/plain")
            .setChooserTitle(getString(R.string.share_event))
            .startChooser()
    }

    // Set the chosen image in the circular image
    private fun setImage(data: Uri) {
        var bitmap: Bitmap? = null
        try {
            if (Build.VERSION.SDK_INT < 29) {
                @Suppress("DEPRECATION")
                bitmap = MediaStore.Images.Media.getBitmap(act.contentResolver, data)
            } else {
                val source = ImageDecoder.createSource(act.contentResolver, data)
                bitmap = ImageDecoder.decodeBitmap(source)
            }
        } catch (e: IOException) {
        }
        if (bitmap == null) return

        // Bitmap ready. Avoid images larger than 450*450
        var dimension: Int = getBitmapSquareSize(bitmap)
        if (dimension > 450) dimension = 450
        val resizedBitmap = ThumbnailUtils.extractThumbnail(
            bitmap,
            dimension,
            dimension,
            ThumbnailUtils.OPTIONS_RECYCLE_INPUT,
        )
        val image = dialogInsertEventBinding.imageEvent
        image.setImageBitmap(resizedBitmap)
    }
}