package com.yourname.expensetrackerapp

import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * The Ask MoneyMate AI conversation for one Statistics period. [snapshot] is taken once when the
 * dialog opens, so follow-up questions all talk about the same numbers. Closing the dialog cancels
 * any request still in flight, and only one request can run at a time.
 */
class AiAdvisorDialog(
    private val activity: AppCompatActivity,
    private val snapshot: AiAdvisor.PeriodSnapshot
) {
    private val history = mutableListOf<AiAdvisor.Exchange>()
    private var inFlight: GeminiClient.Call? = null
    private var dialog: AlertDialog? = null

    private lateinit var scroll: NestedScrollView
    private lateinit var messages: LinearLayout
    private lateinit var suggestionsLabel: View
    private lateinit var suggestions: ChipGroup
    private lateinit var questionLayout: TextInputLayout
    private lateinit var questionInput: TextInputEditText
    private lateinit var progress: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var askBtn: Button

    fun show() {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_ai_advisor, null)
        scroll = view.findViewById(R.id.aiScroll)
        messages = view.findViewById(R.id.aiMessagesContainer)
        suggestionsLabel = view.findViewById(R.id.aiSuggestionsLabel)
        suggestions = view.findViewById(R.id.aiSuggestionsGroup)
        questionLayout = view.findViewById(R.id.aiQuestionLayout)
        questionInput = view.findViewById(R.id.aiQuestionInput)
        progress = view.findViewById(R.id.aiProgress)
        statusText = view.findViewById(R.id.aiStatusText)
        askBtn = view.findViewById(R.id.aiAskBtn)

        view.findViewById<TextView>(R.id.aiPeriodText).text =
            activity.getString(R.string.label_ai_about_period, snapshot.label)

        addSuggestionChips()
        askBtn.setOnClickListener { submit(questionInput.text?.toString().orEmpty()) }
        questionInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submit(questionInput.text?.toString().orEmpty())
                true
            } else false
        }

        dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.label_ai_advisor)
            .setView(view)
            .setNegativeButton(R.string.btn_close, null)
            .setOnDismissListener { inFlight?.cancel() }
            .show()
        @Suppress("DEPRECATION")
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    fun dismiss() {
        dialog?.dismiss()
    }

    /** Only suggestions that make sense for this period's data. */
    private fun addSuggestionChips() {
        val options = buildList {
            add(R.string.ai_suggestion_summary)
            add(R.string.ai_suggestion_top_spend)
            add(R.string.ai_suggestion_save)
            if (snapshot.previousPeriod != null) add(R.string.ai_suggestion_compare)
            if (snapshot.budget != null) add(R.string.ai_suggestion_budget)
        }
        for (res in options) {
            val text = activity.getString(res)
            val chip = Chip(activity).apply {
                this.text = text
                isCheckable = false
                setOnClickListener { submit(text) }
            }
            suggestions.addView(chip)
        }
    }

    private fun submit(rawQuestion: String) {
        if (inFlight != null) return
        val question = rawQuestion.trim()
        if (question.isEmpty()) {
            questionLayout.error = activity.getString(R.string.error_ai_empty_question)
            return
        }
        questionLayout.error = null

        if (!GeminiClient.hasNetwork(activity)) {
            showStatus(activity.getString(R.string.error_ai_offline), isError = true)
            return
        }

        questionInput.setText("")
        suggestionsLabel.visibility = View.GONE
        suggestions.visibility = View.GONE
        val questionCard = addMessage(Role.QUESTION, question)
        scrollTo(questionCard)
        setBusy(true)

        inFlight = AiAdvisor.ask(snapshot, history.toList(), question) { answer, failure ->
            inFlight = null
            if (dialog?.isShowing != true || activity.isFinishing || activity.isDestroyed) return@ask
            setBusy(false)
            if (answer != null) {
                history += AiAdvisor.Exchange(question, answer)
                showStatus("", isError = false)
                scrollTo(addMessage(Role.ANSWER, answer))
            } else {
                // Give the question back so a retry is one tap.
                questionInput.setText(question)
                questionInput.setSelection(question.length)
                val message = activity.getString(messageFor(failure))
                scrollTo(addMessage(Role.ERROR, message))
            }
        }
    }

    private fun messageFor(failure: GeminiClient.Failure?): Int = when (failure) {
        GeminiClient.Failure.OFFLINE -> R.string.error_ai_offline
        GeminiClient.Failure.TIMEOUT -> R.string.error_ai_timeout
        GeminiClient.Failure.RATE_LIMITED, GeminiClient.Failure.SERVER -> R.string.error_ai_busy
        GeminiClient.Failure.EMPTY_RESPONSE -> R.string.error_ai_blocked
        else -> R.string.error_ai_failed
    }

    private enum class Role { QUESTION, ANSWER, ERROR }

    private fun addMessage(role: Role, text: String): View {
        val card = LayoutInflater.from(activity).inflate(R.layout.ai_message_item, messages, false) as MaterialCardView
        val (background, foreground, label) = when (role) {
            Role.QUESTION -> Triple(R.color.primary_container, R.color.on_primary_container, R.string.label_ai_you)
            Role.ANSWER -> Triple(R.color.surface_variant, R.color.on_surface, R.string.label_ai_role)
            Role.ERROR -> Triple(R.color.error_container, R.color.on_error_container, R.string.label_ai_role)
        }
        card.setCardBackgroundColor(ContextCompat.getColor(activity, background))
        card.findViewById<TextView>(R.id.aiMessageRole).apply {
            setText(label)
            setTextColor(ContextCompat.getColor(activity, foreground))
        }
        card.findViewById<TextView>(R.id.aiMessageText).apply {
            this.text = text
            setTextColor(ContextCompat.getColor(activity, foreground))
        }
        messages.addView(card)
        return card
    }

    private fun setBusy(busy: Boolean) {
        askBtn.isEnabled = !busy
        questionInput.isEnabled = !busy
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        if (busy) showStatus(activity.getString(R.string.label_ai_thinking), isError = false)
    }

    private fun showStatus(text: String, isError: Boolean) {
        statusText.text = text
        statusText.setTextColor(ContextCompat.getColor(activity, if (isError) R.color.error else R.color.on_surface_variant))
    }

    private fun scrollTo(target: View) {
        scroll.post { scroll.smoothScrollTo(0, messages.top + target.top) }
    }
}
