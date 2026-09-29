package com.yourname.expensetrackerapp

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.UserProfileChangeRequest
import java.util.Calendar
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var avatarText: TextView
    private lateinit var profileNameText: TextView
    private lateinit var profileEmailText: TextView
    private lateinit var themeToggleGroup: MaterialButtonToggleGroup
    private lateinit var currentCurrencyText: TextView
    private lateinit var budgetAlertsSwitch: SwitchMaterial
    private lateinit var homePeriodSummaryText: TextView
    private lateinit var appLockStatusText: TextView
    private lateinit var biometricRow: View
    private lateinit var biometricRowDivider: View
    private lateinit var biometricSwitch: SwitchMaterial
    private lateinit var defaultFormatText: TextView
    private lateinit var includeDebtsSwitch: SwitchMaterial
    private lateinit var autoAddVoiceSwitch: SwitchMaterial
    private lateinit var smartVoiceSwitch: SwitchMaterial

    private var isSyncing = false

    private val passcodeFlowLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        updateAppLockUi()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        bottomNav = findViewById(R.id.bottomNav)
        avatarText = findViewById(R.id.avatarText)
        profileNameText = findViewById(R.id.profileNameText)
        profileEmailText = findViewById(R.id.profileEmailText)
        themeToggleGroup = findViewById(R.id.themeToggleGroup)
        currentCurrencyText = findViewById(R.id.currentCurrencyText)
        budgetAlertsSwitch = findViewById(R.id.budgetAlertsSwitch)
        homePeriodSummaryText = findViewById(R.id.homePeriodSummaryText)
        appLockStatusText = findViewById(R.id.appLockStatusText)
        biometricRow = findViewById(R.id.biometricRow)
        biometricRowDivider = findViewById(R.id.biometricRowDivider)
        biometricSwitch = findViewById(R.id.biometricSwitch)
        defaultFormatText = findViewById(R.id.defaultFormatText)
        includeDebtsSwitch = findViewById(R.id.includeDebtsSwitch)
        autoAddVoiceSwitch = findViewById(R.id.autoAddVoiceSwitch)
        smartVoiceSwitch = findViewById(R.id.smartVoiceSwitch)

        findViewById<MaterialButton>(R.id.editNameBtn).setOnClickListener { showEditNameDialog() }
        findViewById<MaterialButton>(R.id.changePasswordBtn).setOnClickListener { showChangePasswordConfirm() }
        findViewById<android.view.View>(R.id.manageIncomeCategoriesRow).setOnClickListener {
            startActivity(Intent(this, CategoriesActivity::class.java).putExtra(CategoriesActivity.EXTRA_TYPE, "Income"))
        }
        findViewById<android.view.View>(R.id.manageExpenseCategoriesRow).setOnClickListener {
            startActivity(Intent(this, CategoriesActivity::class.java).putExtra(CategoriesActivity.EXTRA_TYPE, "Expense"))
        }
        findViewById<android.view.View>(R.id.currencyRow).setOnClickListener {
            startActivity(Intent(this, CurrencyPickerActivity::class.java))
        }
        findViewById<android.view.View>(R.id.homePeriodRow).setOnClickListener { showHomePeriodDialog() }
        findViewById<android.view.View>(R.id.appLockRow).setOnClickListener { showAppLockOptions() }
        findViewById<android.view.View>(R.id.defaultFormatRow).setOnClickListener { showDefaultFormatDialog() }
        findViewById<android.view.View>(R.id.syncNowRow).setOnClickListener { syncNow() }
        findViewById<android.view.View>(R.id.userGuideRow).setOnClickListener {
            startActivity(Intent(this, UserGuideActivity::class.java))
        }
        findViewById<TextView>(R.id.aboutVersionText).text = "v${BuildConfig.VERSION_NAME}"
        findViewById<MaterialButton>(R.id.logoutBtn).setOnClickListener { confirmLogOut() }

        setupThemeToggle()
        budgetAlertsSwitch.setOnCheckedChangeListener { _, isChecked ->
            NotificationPrefs.setEnabled(this, isChecked)
        }
        includeDebtsSwitch.setOnCheckedChangeListener { _, isChecked ->
            IncludeDebtsPrefs.setEnabled(this, isChecked)
        }
        autoAddVoiceSwitch.setOnCheckedChangeListener { _, isChecked ->
            AutoAddVoicePrefs.setEnabled(this, isChecked)
        }
        if (GeminiClient.isConfigured()) {
            smartVoiceSwitch.setOnCheckedChangeListener { _, isChecked ->
                SmartVoicePrefs.setEnabled(this, isChecked)
            }
        } else {
            // A build without a Gemini key can't do Smart Voice at all — show why instead of a
            // switch that silently does nothing.
            smartVoiceSwitch.isEnabled = false
            findViewById<TextView>(R.id.smartVoiceDescText).setText(R.string.label_smart_voice_unavailable)
        }

        BottomNavHelper.setup(this, bottomNav, R.id.nav_settings)
    }

    override fun onResume() {
        super.onResume()
        AppLockManager.guard(this)
        populateProfile()
        currentCurrencyText.text = CurrencyPrefs.getSelectedCurrency(this).let { "${it.code} (${it.symbol})" }
        budgetAlertsSwitch.isChecked = NotificationPrefs.isEnabled(this)
        includeDebtsSwitch.isChecked = IncludeDebtsPrefs.isEnabled(this)
        autoAddVoiceSwitch.isChecked = AutoAddVoicePrefs.isEnabled(this)
        smartVoiceSwitch.isChecked = GeminiClient.isConfigured() && SmartVoicePrefs.isEnabled(this)
        updateHomePeriodSummary()
        updateAppLockUi()
        updateDefaultFormatUi()
    }

    private fun updateDefaultFormatUi() {
        defaultFormatText.text = when (ReportFormatPrefs.getDefault(this)) {
            ReportFormatPrefs.Format.PDF -> getString(R.string.format_pdf)
            ReportFormatPrefs.Format.CSV -> getString(R.string.format_csv)
            null -> getString(R.string.option_always_ask)
        }
    }

    private fun showDefaultFormatDialog() {
        val options = arrayOf(
            getString(R.string.option_always_ask),
            getString(R.string.format_pdf),
            getString(R.string.format_csv)
        )
        val current = when (ReportFormatPrefs.getDefault(this)) {
            ReportFormatPrefs.Format.PDF -> 1
            ReportFormatPrefs.Format.CSV -> 2
            null -> 0
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.title_default_download_format)
            .setSingleChoiceItems(options, current) { dialog, which ->
                val format = when (which) {
                    1 -> ReportFormatPrefs.Format.PDF
                    2 -> ReportFormatPrefs.Format.CSV
                    else -> null
                }
                ReportFormatPrefs.setDefault(this, format)
                updateDefaultFormatUi()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun updateHomePeriodSummary() {
        val type = HomePeriodPrefs.getType(this)
        homePeriodSummaryText.text = PeriodWindow.label(
            type,
            HomePeriodPrefs.getCycleStartMillis(this),
            HomePeriodPrefs.getCustomStartMillis(this),
            HomePeriodPrefs.getCustomEndMillis(this)
        )
    }

    private fun isBiometricAvailable(): Boolean =
        BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS

    private fun updateAppLockUi() {
        val enabled = AppLockPrefs.isEnabled(this)
        appLockStatusText.text = getString(if (enabled) R.string.label_app_lock_on else R.string.label_app_lock_off)

        val showBiometricRow = enabled && isBiometricAvailable()
        biometricRow.visibility = if (showBiometricRow) View.VISIBLE else View.GONE
        biometricRowDivider.visibility = if (showBiometricRow) View.VISIBLE else View.GONE
        if (showBiometricRow) {
            biometricSwitch.setOnCheckedChangeListener(null)
            biometricSwitch.isChecked = AppLockPrefs.isBiometricEnabled(this)
            biometricSwitch.setOnCheckedChangeListener { _, isChecked ->
                AppLockPrefs.setBiometricEnabled(this, isChecked)
            }
        }
    }

    private fun showAppLockOptions() {
        if (!AppLockPrefs.isEnabled(this)) {
            passcodeFlowLauncher.launch(
                Intent(this, PasscodeSetupActivity::class.java)
                    .putExtra(PasscodeSetupActivity.EXTRA_MODE, PasscodeSetupActivity.Mode.SET_NEW.name)
            )
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.label_app_lock)
            .setItems(arrayOf(getString(R.string.btn_change_passcode), getString(R.string.btn_turn_off_app_lock))) { _, which ->
                val mode = if (which == 0) PasscodeSetupActivity.Mode.CHANGE else PasscodeSetupActivity.Mode.DISABLE
                passcodeFlowLauncher.launch(
                    Intent(this, PasscodeSetupActivity::class.java)
                        .putExtra(PasscodeSetupActivity.EXTRA_MODE, mode.name)
                )
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun populateProfile() {
        val user = AuthRepository.currentUser
        val name = user?.displayName?.takeIf { it.isNotBlank() }
        profileNameText.text = name ?: getString(R.string.label_no_name)
        profileEmailText.text = user?.email ?: ""
        val initialSource = name ?: user?.email
        avatarText.text = initialSource?.firstOrNull()?.uppercase(Locale.getDefault()) ?: "?"
    }

    private fun setupThemeToggle() {
        val checkedId = when (ThemePrefs.currentMode(this)) {
            AppCompatDelegate.MODE_NIGHT_NO -> R.id.btnThemeLight
            AppCompatDelegate.MODE_NIGHT_YES -> R.id.btnThemeDark
            else -> R.id.btnThemeSystem
        }
        themeToggleGroup.check(checkedId)

        themeToggleGroup.addOnButtonCheckedListener { _, checkedButtonId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val mode = when (checkedButtonId) {
                R.id.btnThemeLight -> AppCompatDelegate.MODE_NIGHT_NO
                R.id.btnThemeDark -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            if (mode != ThemePrefs.currentMode(this)) {
                ThemePrefs.setMode(this, mode)
                recreate()
            }
        }
    }

    private val homePeriodMonthNames = arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    private fun showHomePeriodDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_home_period, null)
        val typeSpinner = dialogView.findViewById<Spinner>(R.id.periodTypeSpinner)
        val cycleInputLayout = dialogView.findViewById<TextInputLayout>(R.id.cycleInputLayout)
        val cycleInput = dialogView.findViewById<EditText>(R.id.cycleInput)
        val customEndInputLayout = dialogView.findViewById<TextInputLayout>(R.id.customEndInputLayout)
        val customEndInput = dialogView.findViewById<EditText>(R.id.customEndInput)

        val types = PeriodType.values()
        val adapter = ArrayAdapter(this, R.layout.spinner_item, types.map { it.label })
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        typeSpinner.adapter = adapter

        var selectedType = HomePeriodPrefs.getType(this)
        var cycleStart = HomePeriodPrefs.getCycleStartMillis(this)
        var customStart = HomePeriodPrefs.getCustomStartMillis(this).takeIf { it > 0L } ?: System.currentTimeMillis()
        var customEnd = HomePeriodPrefs.getCustomEndMillis(this).takeIf { it > 0L } ?: System.currentTimeMillis()

        fun refreshFieldsUi() {
            val budgetPeriod = selectedType.toBudgetPeriod()
            when {
                budgetPeriod == null && selectedType == PeriodType.ALL_TIME -> {
                    cycleInputLayout.visibility = View.GONE
                    customEndInputLayout.visibility = View.GONE
                }
                selectedType == PeriodType.CUSTOM -> {
                    cycleInputLayout.visibility = View.VISIBLE
                    customEndInputLayout.visibility = View.VISIBLE
                    cycleInputLayout.hint = getString(R.string.label_start_date)
                    customEndInputLayout.hint = getString(R.string.label_end_date)
                    val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    cycleInput.setText(dateFormat.format(Date(customStart)))
                    customEndInput.setText(dateFormat.format(Date(customEnd)))
                }
                budgetPeriod == BudgetPeriod.DAILY -> {
                    cycleInputLayout.visibility = View.GONE
                    customEndInputLayout.visibility = View.GONE
                }
                else -> {
                    cycleInputLayout.visibility = View.VISIBLE
                    customEndInputLayout.visibility = View.GONE
                    val start = if (cycleStart > 0L) cycleStart else budgetPeriod!!.defaultCycleStartMillis()
                    cycleInputLayout.hint = when (selectedType) {
                        PeriodType.WEEKLY -> getString(R.string.label_week_start_date)
                        PeriodType.MONTHLY -> getString(R.string.label_select_month)
                        PeriodType.YEARLY -> getString(R.string.label_select_year)
                        else -> ""
                    }
                    cycleInput.setText(PeriodWindow.label(selectedType, start, 0L, 0L))
                }
            }
        }

        typeSpinner.setSelection(types.indexOf(selectedType))
        refreshFieldsUi()

        typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedType = types[position]
                cycleStart = 0L
                refreshFieldsUi()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        cycleInput.setOnClickListener {
            when (selectedType) {
                PeriodType.WEEKLY -> showDatePickerDialog(cycleStart.takeIf { it > 0L } ?: BudgetPeriod.WEEKLY.defaultCycleStartMillis()) {
                    cycleStart = it; refreshFieldsUi()
                }
                PeriodType.MONTHLY -> showMonthYearPickerDialog(cycleStart.takeIf { it > 0L } ?: BudgetPeriod.MONTHLY.defaultCycleStartMillis()) {
                    cycleStart = it; refreshFieldsUi()
                }
                PeriodType.YEARLY -> showYearPickerDialog(cycleStart.takeIf { it > 0L } ?: BudgetPeriod.YEARLY.defaultCycleStartMillis()) {
                    cycleStart = it; refreshFieldsUi()
                }
                PeriodType.CUSTOM -> showDatePickerDialog(customStart, maxMillis = customEnd) {
                    customStart = it; refreshFieldsUi()
                }
                else -> {}
            }
        }
        customEndInput.setOnClickListener {
            showDatePickerDialog(customEnd, minMillis = customStart) {
                customEnd = it; refreshFieldsUi()
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.title_home_period)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                val finalCycleStart = selectedType.toBudgetPeriod()?.let { bp ->
                    if (cycleStart > 0L) cycleStart else bp.defaultCycleStartMillis()
                } ?: 0L
                HomePeriodPrefs.save(this, selectedType, finalCycleStart, customStart, customEnd)
                updateHomePeriodSummary()
                Toast.makeText(this, R.string.toast_home_period_saved, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showDatePickerDialog(initialMillis: Long, minMillis: Long? = null, maxMillis: Long? = null, onPicked: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = initialMillis }
        val dialog = DatePickerDialog(
            this,
            { _, year, month, day ->
                val picked = Calendar.getInstance().apply {
                    set(year, month, day, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                onPicked(picked.timeInMillis)
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        )
        minMillis?.let { dialog.datePicker.minDate = it }
        maxMillis?.let { dialog.datePicker.maxDate = it }
        dialog.show()
    }

    private fun showMonthYearPickerDialog(initialMillis: Long, onPicked: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = initialMillis }
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        val monthPicker = NumberPicker(this).apply {
            minValue = 0
            maxValue = 11
            displayedValues = homePeriodMonthNames
            value = cal.get(Calendar.MONTH)
        }
        val yearPicker = NumberPicker(this).apply {
            minValue = currentYear - 15
            maxValue = currentYear + 15
            value = cal.get(Calendar.YEAR)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val pad = resources.getDimensionPixelSize(R.dimen.space_16)
            setPadding(pad, pad, pad, pad)
            addView(monthPicker, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(yearPicker, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.label_select_month)
            .setView(container)
            .setPositiveButton(R.string.btn_ok) { _, _ ->
                val picked = Calendar.getInstance().apply {
                    set(yearPicker.value, monthPicker.value, 1, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                onPicked(picked.timeInMillis)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showYearPickerDialog(initialMillis: Long, onPicked: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = initialMillis }
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        val yearPicker = NumberPicker(this).apply {
            minValue = currentYear - 15
            maxValue = currentYear + 15
            value = cal.get(Calendar.YEAR)
        }
        val container = FrameLayout(this).apply {
            val pad = resources.getDimensionPixelSize(R.dimen.space_16)
            setPadding(pad, pad, pad, pad)
            addView(yearPicker)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.label_select_year)
            .setView(container)
            .setPositiveButton(R.string.btn_ok) { _, _ ->
                val picked = Calendar.getInstance().apply {
                    set(yearPicker.value, Calendar.JANUARY, 1, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                onPicked(picked.timeInMillis)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showEditNameDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_name, null)
        val input = dialogView.findViewById<EditText>(R.id.editNameInput)
        input.setText(AuthRepository.currentUser?.displayName)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.title_edit_name)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, R.string.error_enter_name, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val user = AuthRepository.currentUser ?: return@setPositiveButton
                user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build())
                    .addOnSuccessListener {
                        UserRepository.updateDisplayName(user.uid, name)
                        populateProfile()
                        Toast.makeText(this, R.string.toast_name_updated, Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, it.message ?: "Something went wrong", Toast.LENGTH_LONG).show()
                    }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showChangePasswordConfirm() {
        val email = AuthRepository.currentUser?.email ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.title_change_password)
            .setMessage(getString(R.string.msg_change_password, email))
            .setPositiveButton(R.string.btn_send) { _, _ ->
                AuthRepository.sendPasswordReset(email) { result ->
                    result.onSuccess {
                        Toast.makeText(this, R.string.toast_reset_email_sent, Toast.LENGTH_LONG).show()
                    }.onFailure {
                        Toast.makeText(this, it.message ?: "Something went wrong", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun syncNow() {
        if (isSyncing) return
        val uid = AuthRepository.currentUser?.uid ?: return
        isSyncing = true
        Toast.makeText(this, R.string.toast_syncing, Toast.LENGTH_SHORT).show()
        SyncManager.flushPendingOps(this)
        SyncManager.pullInitialData(this, uid) {
            isSyncing = false
            Toast.makeText(this, R.string.toast_sync_complete, Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmLogOut() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.btn_logout_settings)
            .setPositiveButton(R.string.btn_logout_settings) { _, _ -> logOut() }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    /** Identical to MainActivity's own logout handler — signs out and wipes the local cache so
     *  whoever signs in next on this device doesn't start out seeing this account's data. */
    private fun logOut() {
        AuthRepository.signOut()
        SyncManager.clearLocalData(this)
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
