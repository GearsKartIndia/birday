package com.minar.birday.fragments.dialogs

import android.content.Context
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
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.minar.birday.R
import com.minar.birday.activities.MainActivity
import com.minar.birday.adapters.ContactsFilterArrayAdapter
import com.minar.birday.databinding.BottomSheetInsertEventBinding
import com.minar.birday.model.ContactInfo
import com.minar.birday.model.Event
import com.minar.birday.model.EventCode
import com.minar.birday.model.EventResult
import com.minar.birday.utilities.START_YEAR
import com.minar.birday.utilities.bitmapToByteArray
import com.minar.birday.utilities.checkName
import com.minar.birday.utilities.getAvailableTypes
import com.minar.birday.utilities.getBitmapSquareSize
import com.minar.birday.utilities.getStringForTypeCodename
import com.minar.birday.utilities.resultToEvent
import com.minar.birday.utilities.setEventImageOrPlaceholder
import com.minar.birday.utilities.smartFixName
import com.minar.birday.viewmodels.InsertEventViewModel
import com.minar.birday.viewmodels.MainViewModel
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Calendar
import java.util.TimeZone
import java.util.zip.GZIPOutputStream


@OptIn(ExperimentalStdlibApi::class)
class InsertEventBottomSheet(
    private val act: MainActivity,
    private val event: EventResult? = null
) :
    BottomSheetDialogFragment() {
    private var _binding: BottomSheetInsertEventBinding? = null
    private val binding get() = _binding!!
    private lateinit var resultLauncher: ActivityResultLauncher<String>
    private var imageChosen = false
    private val viewModel: InsertEventViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the bottom sheet, initialize the shared preferences and the recent options list
        _binding = BottomSheetInsertEventBinding.inflate(inflater, container, false)

        // Result launcher stuff
        resultLauncher =
            registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                // Handle the returned Uri (atm, the image can't be cropped)
                if (uri != null) {
                    imageChosen = true
                    setImage(uri)
                }
            }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Fully expand the dialog
        (dialog as BottomSheetDialog).behavior.state = BottomSheetBehavior.STATE_EXPANDED

        // Animate the drawable in loop
        val titleIcon = binding.insertEventImage
        val title = binding.insertEventTitle
        if (event == null) act.animateAvd(
            titleIcon,
            R.drawable.animated_insert_event,
            2500L
        )
        else act.animateAvd(titleIcon, R.drawable.animated_edit_event, 2500L)

        // Show a bottom sheet containing the form to insert a new event
        imageChosen = false
        // The initial date is today
        var eventDateValue: LocalDate = LocalDate.now()
        var dueDateValue: LocalDate = LocalDate.now()
        var countYearValue = true
        val positiveButton = binding.positiveButton
        val negativeButton = binding.negativeButton
        val eventImage = binding.imageEvent
        var typeValue = EventCode.BIRTHDAY.name
        positiveButton.isEnabled = false

        if (event != null) {
            typeValue = event.type!!
            countYearValue = event.yearMatter ?: true
            eventDateValue = event.originalDate
            positiveButton.text = getString(R.string.update_event)
            title.text = getString(R.string.edit_event)

            // Set the fields
            val eventDate = binding.dateEvent
            val countYear = binding.countYearSwitch

            when (typeValue) {
                getString(R.string.vehicle_insurance_caps) -> {
                    binding.otherEventLayout.visibility=View.GONE
                    binding.vehicleInsuranceRenewalLayout.visibility=View.GONE
                    binding.vehicleInsuranceLayout.visibility=View.VISIBLE
                    val formatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                    binding.dueDateEvent.setText(eventDateValue.format(formatter))
                }
                getString(R.string.vehicle_insurance_renewal_caps) -> {
                    binding.otherEventLayout.visibility=View.GONE
                    binding.vehicleInsuranceLayout.visibility=View.GONE
                    binding.vehicleInsuranceRenewalLayout.visibility=View.VISIBLE
                }
                else -> {
                    binding.vehicleInsuranceLayout.visibility=View.GONE
                    binding.vehicleInsuranceRenewalLayout.visibility=View.GONE
                    binding.otherEventLayout.visibility=View.VISIBLE
                }
            }

            binding.typeEvent.setText(typeValue, false)
            binding.nameEvent.setText(event.name)
            binding.surnameEvent.setText(event.surname)
            countYear.isChecked = countYearValue
            val formatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            eventDate.setText(eventDateValue.format(formatter))
            imageChosen = setEventImageOrPlaceholder(event, eventImage)
            positiveButton.isEnabled = true

            //vehicle insurance add event
            binding.vehicleManufacturerEvent.setText(event.manufacturer_name.toString())
            binding.vehicleManufacturerEvent1.setText(event.manufacturer_name1.toString())
            binding.vehicleManufacturerEvent2.setText(event.manufacturer_name2.toString())
            binding.vehicleManufacturerEvent3.setText(event.manufacturer_name3.toString())

            binding.vehicleModelEvent.setText(event.model_name.toString())
            binding.vehicleModel1Event.setText(event.model_name1.toString())
            binding.vehicleModel2Event.setText(event.model_name2.toString())
            binding.vehicleModel3Event.setText(event.model_name3.toString())

            binding.vehicleInsuranceProviderEvent.setText(event.insurance_provider.toString())

            //vehicle insurance renewal event add
            binding.input1Event.setText(event.input1.toString())
            binding.input2Event.setText(event.input2.toString())
            binding.input3Event.setText(event.input3.toString())
            binding.input4Event.setText(event.input4.toString())
            binding.input5Event.setText(event.input5.toString())
            binding.input6Event.setText(event.input6.toString())
            binding.input7Event.setText(event.input7.toString())
            binding.input8Event.setText(event.input8.toString())
            binding.input9Event.setText(event.input9.toString())
            binding.input10Event.setText(event.input10.toString())
        }

        positiveButton.setOnClickListener {
            var image: ByteArray? = null
            if (imageChosen)
                image = bitmapToByteArray(eventImage.drawable.toBitmap())
            // Use the data to create an event object and insert it in the db
            val tuple = if (event != null) Event(
                id = event.id,
                type = typeValue,
                originalDate = eventDateValue,
                name = binding.nameEvent.text.toString().trim(),
                yearMatter = countYearValue,
                surname = binding.surnameEvent.text.toString().trim(),
                favorite = event.favorite,
                notes = event.notes,
                image = image,
                //vehicle insurance add event
                manufacturer_name = binding.vehicleManufacturerEvent.text.toString().trim(),
                manufacturer_name1 = binding.vehicleManufacturerEvent1.text.toString().trim(),
                manufacturer_name2 = binding.vehicleManufacturerEvent2.text.toString().trim(),
                manufacturer_name3 = binding.vehicleManufacturerEvent3.text.toString().trim(),

                model_name = binding.vehicleModelEvent.text.toString().trim(),
                model_name1 = binding.vehicleModel1Event.text.toString().trim(),
                model_name2 = binding.vehicleModel2Event.text.toString().trim(),
                model_name3 = binding.vehicleModel3Event.text.toString().trim(),

                //vehicle insurance renewal add event
                input1 = binding.input1Event.text.toString(),
                input2 = binding.input2Event.text.toString(),
                input3 = binding.input3Event.text.toString(),
                input4= binding.input4Event.text.toString(),
                input5= binding.input5Event.text.toString(),
                input6= binding.input6Event.text.toString(),
                input7= binding.input7Event.text.toString(),
                input8= binding.input8Event.text.toString(),
                input9= binding.input9Event.text.toString(),
                input10= binding.input10Event.text.toString(),

                insurance_provider = binding.vehicleInsuranceProviderEvent.text.toString().trim()
            ) else
                Event(
                    id = 0,
                    originalDate = eventDateValue,
                    name = binding.nameEvent.text.toString().trim(),
                    surname = binding.surnameEvent.text.toString().trim(),
                    yearMatter = countYearValue,
                    type = typeValue,
                    image = image,
                    //vehicle insurance add event
                    manufacturer_name = binding.vehicleManufacturerEvent.text.toString().trim(),
                    manufacturer_name1 = binding.vehicleManufacturerEvent1.text.toString().trim(),
                    manufacturer_name2 = binding.vehicleManufacturerEvent2.text.toString().trim(),
                    manufacturer_name3 = binding.vehicleManufacturerEvent3.text.toString().trim(),

                    model_name = binding.vehicleModelEvent.text.toString().trim(),
                    model_name1 = binding.vehicleModel1Event.text.toString().trim(),
                    model_name2 = binding.vehicleModel2Event.text.toString().trim(),
                    model_name3 = binding.vehicleModel3Event.text.toString().trim(),

                    //vehicle insurance add event
                    input1 = binding.input1Event.text.toString(),
                    input2 = binding.input2Event.text.toString(),
                    input3 = binding.input3Event.text.toString(),
                    input4= binding.input4Event.text.toString(),
                    input5= binding.input5Event.text.toString(),
                    input6= binding.input6Event.text.toString(),
                    input7= binding.input7Event.text.toString(),
                    input8= binding.input8Event.text.toString(),
                    input9= binding.input9Event.text.toString(),
                    input10= binding.input10Event.text.toString(),

                    insurance_provider = binding.vehicleInsuranceProviderEvent.text.toString().trim()
                )
            // Insert using another thread
            val thread = Thread {
                if (event != null) {
                    act.mainViewModel.update(tuple)
                    // Go back to the first screen to avoid updating the displayed details
                    act.showSnackbar(
                        requireContext().getString(R.string.updated_event)
                    )
                    act.runOnUiThread {
                        findNavController().popBackStack()
                    }
                } else {
                    act.mainViewModel.insert(tuple)
                    act.showSnackbar(
                        requireContext().getString(R.string.added_event)
                    )
                    act.runOnUiThread {
                        findNavController().popBackStack()
                    }
                }
            }
            thread.start()
            dismiss()
        }

        negativeButton.setOnClickListener {
            dismiss()
        }

        // Setup listeners and checks on the fields
        val type = binding.typeEvent
        val name = binding.nameEvent
        val surname = binding.surnameEvent
        val eventDate = binding.dateEvent
        val countYear = binding.countYearSwitch

        binding.vehicleManufacturerEvent.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                binding.vehicleManufacturerListLayout.visibility = View.VISIBLE
                binding.vehicleManufacturerEvent.requestFocus()

                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(binding.vehicleManufacturerEvent, InputMethodManager.SHOW_IMPLICIT)
            }
            true
        }

        binding.vehicleModelEvent.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                binding.vehicleModelListLayout.visibility = View.VISIBLE
                binding.vehicleModelEvent.requestFocus()

                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(binding.vehicleModelEvent, InputMethodManager.SHOW_IMPLICIT)
            }
            true
        }

        // Set the dropdown to show the available event types
        val items = getAvailableTypes(act)
        val eventTypeAdapter = ArrayAdapter(act, R.layout.event_type_list_item, items)
        with(type) {
            setAdapter(eventTypeAdapter)
            setText(getStringForTypeCodename(context, typeValue), false)
            onItemClickListener =
                AdapterView.OnItemClickListener { _, _, position, _ ->
                    typeValue = items[position].codeName.name
                    // Automatically uncheck "the year matters" for name days
                    if (typeValue == EventCode.NAME_DAY.name) {
                        countYear.isChecked = false
                        countYear.isEnabled = false
                        countYearValue = false

                        binding.otherEventLayout.visibility = View.VISIBLE
                        binding.vehicleInsuranceLayout.visibility = View.GONE
                        binding.vehicleInsuranceRenewalLayout.visibility = View.GONE
                    } else if(typeValue == EventCode.VEHICLE_INSURANCE.name){
                        binding.vehicleInsuranceLayout.visibility = View.VISIBLE
                        binding.otherEventLayout.visibility = View.GONE
                        binding.vehicleInsuranceRenewalLayout.visibility = View.GONE
                    }else if(typeValue == EventCode.VEHICLE_INSURANCE_RENEWAL.name){
                        binding.vehicleInsuranceRenewalLayout.visibility = View.VISIBLE
                        binding.otherEventLayout.visibility = View.GONE
                        binding.vehicleInsuranceLayout.visibility = View.GONE
                    }
                    else {
                        countYear.isChecked = true
                        countYear.isEnabled = true
                        countYearValue = true

                        binding.otherEventLayout.visibility = View.VISIBLE
                        binding.vehicleInsuranceLayout.visibility = View.GONE
                    }

                    if (!imageChosen)
                        eventImage.setImageDrawable(
                            ContextCompat.getDrawable(
                                context,
                                // Set the image depending on the event type
                                when (typeValue) {
                                    EventCode.BIRTHDAY.name -> R.drawable.placeholder_birthday_image
                                    EventCode.ANNIVERSARY.name -> R.drawable.placeholder_anniversary_image
                                    EventCode.DEATH.name -> R.drawable.placeholder_death_image
                                    EventCode.NAME_DAY.name -> R.drawable.placeholder_name_day_image
                                    EventCode.VEHICLE_INSURANCE.name -> R.drawable.placeholder_vehicle_image
                                    EventCode.VEHICLE_INSURANCE_RENEWAL.name -> R.drawable.placeholder_vehicle_image
                                    else -> R.drawable.placeholder_other_image
                                }
                            )
                        )

                }
        }

        // Initialize contacts list, using InsertEventViewModel
        viewModel.initContactsList(act)
        viewModel.contactsList.observe(viewLifecycleOwner) { contacts ->
            // Setup AutoCompleteEditText adapters
            binding.nameEvent.setAdapter(
                ContactsFilterArrayAdapter(
                    requireContext(),
                    contacts,
                    ContactInfo::name,
                )
            )
            binding.surnameEvent.setAdapter(
                ContactsFilterArrayAdapter(
                    requireContext(),
                    contacts,
                    ContactInfo::surname,
                )
            )

            val onAutocompleteClick = AdapterView.OnItemClickListener { parent, _, i, _ ->
                val clickedItem =
                    parent.getItemAtPosition(i) as? ContactInfo ?: return@OnItemClickListener
                binding.nameEvent.setText(clickedItem.name)
                binding.surnameEvent.setText(clickedItem.surname)
            }
            binding.nameEvent.onItemClickListener = onAutocompleteClick
            binding.surnameEvent.onItemClickListener = onAutocompleteClick
        }

        // Calendar setup. The end date is the last day in the following year (dumb users)
        val startDate = Calendar.getInstance()
        val endDate = Calendar.getInstance()
        endDate.set(Calendar.YEAR, endDate.get(Calendar.YEAR) + 1)
        endDate.set(Calendar.DAY_OF_YEAR, endDate.getActualMaximum(Calendar.DAY_OF_YEAR))
        startDate.set(START_YEAR, 1, 1)
        val formatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
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

                        eventDate.setText(eventDateValue.format(formatter))
                        // The last selected date is saved if the dialog is reopened
                        lastDate.set(eventDateValue.year, month - 1, day)
                    }

                }
                // Show the picker and wait to reset the variable
                dateDialog!!.show(act.supportFragmentManager, "main_act_picker")
                Handler(Looper.getMainLooper()).postDelayed({ dateDialog = null }, 750)
            }
        }

        binding.dueDateEvent.setOnClickListener {
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
                        dueDateValue = LocalDate.of(year, month, day)

                        binding.dueDateEvent.setText(dueDateValue.format(formatter))
                        // The last selected date is saved if the dialog is reopened
                        lastDate.set(dueDateValue.year, month - 1, day)
                    }

                }
                // Show the picker and wait to reset the variable
                dateDialog!!.show(act.supportFragmentManager, "main_act_picker")
                Handler(Looper.getMainLooper()).postDelayed({ dateDialog = null }, 750)
            }
        }

        // Validate each field in the form with the same watcher
        var nameCorrect = false
        var surnameCorrect = true // Surname is not mandatory
        var eventDateCorrect = event != null
        //vehicle insurance add event
        var manufacturerCorrect = false
        var modelCorrect = false
        var insuranceCorrect = false

        val watcher = afterTextChangedWatcher { editable ->
            when {
                editable === name.editableText -> {
                    val nameText = name.text.toString()
                    if (nameText.isBlank() || !checkName(nameText)) {
                        // Setting the error on the layout is important to make the properties work
                        binding.nameEventLayout.error =
                            getString(R.string.invalid_value_name)
                        positiveButton.isEnabled = false
                        nameCorrect = false
                    } else {
                        binding.nameEventLayout.error = null
                        nameCorrect = true
                    }
                }

                editable === surname.editableText -> {
                    val surnameText = surname.text.toString()
                    if (!checkName(surnameText)) {
                        // Setting the error on the layout is important to make the properties work
                        binding.surnameEventLayout.error =
                            getString(R.string.invalid_value_name)
                        positiveButton.isEnabled = false
                        surnameCorrect = false
                    } else {
                        binding.surnameEventLayout.error = null
                        surnameCorrect = true
                    }
                }
                // Once selected, the date can't be blank anymore
                editable === eventDate.editableText -> eventDateCorrect = true

                //vehicle insurance add event
                editable === binding.vehicleManufacturerEvent.editableText -> {
                    val manufacturer_name = binding.vehicleManufacturerEvent.text.toString()
                    if (manufacturer_name.isNotEmpty()) {
                        binding.vehicleManufacturerLayout.error = null
                        manufacturerCorrect = true
                    } else {
                        // Setting the error on the layout is important to make the properties work
                        binding.vehicleManufacturerLayout.error =
                            getString(R.string.invalid_value_name)
                        positiveButton.isEnabled = false
                        manufacturerCorrect = false
                    }
                }

                editable === binding.vehicleModelEvent.editableText -> {
                    val model_name = binding.vehicleModelEvent.text.toString()
                    if (model_name.isEmpty()) {
                        // Setting the error on the layout is important to make the properties work
                        binding.vehicleModelLayout.error =
                            getString(R.string.invalid_value_name)
                        positiveButton.isEnabled = false
                        modelCorrect = false
                    } else {
                        binding.vehicleManufacturerLayout.error = null
                        modelCorrect = true
                    }
                }

                editable === binding.vehicleInsuranceProviderEvent.editableText -> {
                    val insurance_provider_name = binding.vehicleInsuranceProviderEvent.text.toString()
                    if (insurance_provider_name.isEmpty()) {
                        // Setting the error on the layout is important to make the properties work
                        binding.insuranceProviderLayout.error =
                            getString(R.string.invalid_value_name)
                        positiveButton.isEnabled = false
                        insuranceCorrect = false
                    } else {
                        binding.insuranceProviderLayout.error = null
                        insuranceCorrect = true
                    }
                }

            }

            if(typeValue == getString(R.string.vehicle_insurance_caps)){
               if(binding.dueDateEvent.text!!.isNotEmpty()){
                    eventDateCorrect = true
                }
                if (manufacturerCorrect && modelCorrect && insuranceCorrect && eventDateCorrect) positiveButton.isEnabled =true

            }else if(typeValue == getString(R.string.vehicle_insurance_renewal_caps)){
                positiveButton.isEnabled = true
            }else {
                if (eventDateCorrect && nameCorrect && surnameCorrect) positiveButton.isEnabled = true
            }
        }

        name.addTextChangedListener(watcher)
        binding.surnameEvent.addTextChangedListener(watcher)
        eventDate.addTextChangedListener(watcher)
        //vehicle insurance add event
        binding.dueDateEvent.addTextChangedListener(watcher)
        binding.vehicleManufacturerEvent1.addTextChangedListener(watcher)
        binding.vehicleManufacturerEvent.addTextChangedListener(watcher)
        binding.vehicleManufacturerEvent2.addTextChangedListener(watcher)
        binding.vehicleManufacturerEvent3.addTextChangedListener(watcher)

        binding.vehicleModelEvent.addTextChangedListener(watcher)
        binding.vehicleModel1Event.addTextChangedListener(watcher)
        binding.vehicleModel2Event.addTextChangedListener(watcher)
        binding.vehicleModel3Event.addTextChangedListener(watcher)
        binding.vehicleInsuranceProviderEvent.addTextChangedListener(watcher)
        //vehicle insurance renewal add event
        binding.input1Event.addTextChangedListener(watcher)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Reset the binding to null to follow the best practice
        _binding = null
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
        } catch (_: IOException) {
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
        val image = binding.imageEvent
        image.setImageBitmap(resizedBitmap)
    }

    private inline fun afterTextChangedWatcher(crossinline afterTextChanged: (editable: Editable) -> Unit) =
        object : TextWatcher {
            override fun beforeTextChanged(
                charSequence: CharSequence,
                i: Int,
                i1: Int,
                i2: Int
            ) {
            }

            override fun onTextChanged(
                charSequence: CharSequence,
                i: Int,
                i1: Int,
                i2: Int
            ) {
            }

            override fun afterTextChanged(editable: Editable) {
                afterTextChanged(editable)
            }
        }

}