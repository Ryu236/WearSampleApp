package com.ryu236.wearsampleapp

import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.v4.content.ContextCompat
import android.support.wearable.activity.WearableActivity
import android.view.View
import android.widget.Button
import android.widget.TextClock
import android.widget.TextView
import timber.log.Timber
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit
import android.app.PendingIntent
import android.app.AlarmManager
import android.content.Context
import android.annotation.SuppressLint
import android.content.Intent
import java.text.SimpleDateFormat


class MainActivity : WearableActivity() {

    // Screen components
    private lateinit var mStartStopButton: Button
    private lateinit var mResetButton: Button
    private lateinit var mTimeView: TextView
    private lateinit var mBackground: View
    private lateinit var mClockView: TextClock
    private lateinit var mNotice: TextView

    // Wake up the Activity in ambient mode.
    private lateinit var mAmbientStateAlarmManager: AlarmManager
    private lateinit var mAmbientStatePendingIntent: PendingIntent

    // foreground and background color in active view.
    private var mActiveBackgroundColor: Int = 0
    private var mActiveForegroundColor: Int = 0

    // Milliseconds between waking processor/screen for updates when active
    private val ACTIVE_INTERVAL_MS: Long = TimeUnit.SECONDS.toMillis(1)
    // Milliseconds between waking processor/screen for updates when in ambient mode
    private val AMBIENT_INTERVAL_MS = TimeUnit.SECONDS.toMillis(10)

    @SuppressLint("SimpleDateFormat")
    private val mDebugTimeFormat = SimpleDateFormat("HH:mm:ss")

    // The last time that the stop watch was updated or the start time.
    private var mLastTick = 0L
    // Store time that was measured so far.
    private var mTimeSoFar = 0L
    // Keep track to see if the stop watch is running.
    private var mRunning = false
    // Handle
    private val mActiveModeUpdateHandler = UpdateStopwatchHandler(this)
    // Handler for updating the clock in active mode
    private val mActiveClockUpdateHandler = UpdateClockHandler(this)
    // Foreground and background color in active view.

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Enables Always-on
        setAmbientEnabled()

        mAmbientStateAlarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Create pending intent
        val intent = Intent(applicationContext, MainActivity::class.java)
        mAmbientStatePendingIntent = PendingIntent.getActivity(
            applicationContext,
            R.id.msg_update,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Get on screen items
        mStartStopButton = findViewById(R.id.startstopbtn)
        mResetButton = findViewById(R.id.resetbtn)
        mTimeView = findViewById(R.id.timeview)
        resetTimeView() // initialise TimeView

        mBackground = findViewById(R.id.gridbackground)
        mClockView = findViewById(R.id.clock)
        mNotice = findViewById(R.id.notice)
        mNotice.paint.isAntiAlias = false

        mActiveBackgroundColor = ContextCompat.getColor(this, R.color.activeBackground)
        mActiveForegroundColor = ContextCompat.getColor(this, R.color.activeText)

        mStartStopButton.setOnClickListener {
            Timber.d("Toggle start / stop state")
            toggleStartStop()
        }

        mResetButton.setOnClickListener {
            Timber.d("Reset time")
            mLastTick = 0L
            mTimeSoFar = 0L
            resetTimeView()
        }

        mActiveClockUpdateHandler.sendEmptyMessage(R.id.msg_update)
    }

    private fun updateDisplayAndSetRefresh() {
        if (!mRunning) {
            return
        }
        incrementTimeSoFar()

        var seconds = (mTimeSoFar / 1000).toInt()
        val minutes = seconds / 60
        seconds %= 60

        setTimeView(minutes, seconds)

        if (!isAmbient) {
            // In Active mode update directly via handler.
            val timeMs = System.currentTimeMillis()
            val delayMs = ACTIVE_INTERVAL_MS - timeMs % ACTIVE_INTERVAL_MS
            Timber.d("NOT ambient - delaying by: $delayMs")
            mActiveModeUpdateHandler.sendEmptyMessageDelayed(R.id.msg_update, delayMs)
        } else {
            // In Ambient mode update via AlarmManager.
            val timeMs = System.currentTimeMillis()
            val delayMs = AMBIENT_INTERVAL_MS - timeMs % AMBIENT_INTERVAL_MS
            val triggerTimeMs = timeMs + delayMs
            Timber.d("In ambient - trigger time: %s".format(mDebugTimeFormat.format(triggerTimeMs)))
            mAmbientStateAlarmManager.cancel(mAmbientStatePendingIntent)
            mAmbientStateAlarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerTimeMs,
                mAmbientStatePendingIntent
            )
        }
    }

    private fun incrementTimeSoFar() {
        // Update display time
        val now = System.currentTimeMillis()
        Timber.d(String.format("current time: %d. start: %d", now, mLastTick))
        mTimeSoFar = mTimeSoFar + now - mLastTick
        mLastTick = now
    }

    /**
     * This is mostly triggered by the Alarms we set in Ambient mode and informs us we need to
     * update the screen (and process any data).
     */
    public override fun onNewIntent(intent: Intent) {
        Timber.d("onNewIntent(): $intent")
        super.onNewIntent(intent)
        setIntent(intent)
        Timber.d("Running? $mRunning, Start time: $mLastTick")
        updateDisplayAndSetRefresh()
    }

    /**
     * Set time view to its initial state.
     */
    private fun resetTimeView() {
        setTimeView(0, 0)
    }

    /**
     * Set time view to a specified time.
     */
    private fun setTimeView(minutes: Int, seconds: Int) {
        if (seconds < 10) {
            mTimeView.text = "%d:0%d".format(minutes, seconds)
        } else {
            mTimeView.text = "%d:%d".format(minutes, seconds)
        }
    }

    private fun toggleStartStop() {
        Timber.d("mRunning: $mRunning")
        if (mRunning) {
            // This can only happen in interactive mode - so we only need to stop the handler
            // AlarmManager should be clear
            mActiveModeUpdateHandler.removeMessages(R.id.msg_update)
            incrementTimeSoFar()
            // Currently running - turn it to stop
            mStartStopButton.text = getString(R.string.btn_label_start)
            mRunning = false
            mResetButton.isEnabled = true
        } else {
            mLastTick = System.currentTimeMillis()
            mStartStopButton.text = getString(R.string.btn_label_pause)
            mRunning = true
            mResetButton.isEnabled = false
            updateDisplayAndSetRefresh()
        }
    }


    override fun onEnterAmbient(ambientDetails: Bundle?) {
        Timber.d("ENTER Ambient")
        super.onEnterAmbient(ambientDetails)

        if (mRunning) {
            mActiveModeUpdateHandler.removeMessages(R.id.msg_update)
            mNotice.visibility = View.VISIBLE
        }

        mActiveClockUpdateHandler.removeMessages(R.id.msg_update)

        mTimeView.setTextColor(Color.WHITE)
        val textPaint = mTimeView.paint
        textPaint.isAntiAlias = false
        textPaint.style = Paint.Style.STROKE
        textPaint.strokeWidth = 2F

        mStartStopButton.visibility = View.INVISIBLE
        mResetButton.visibility = View.INVISIBLE
        mBackground.setBackgroundColor(Color.BLACK)

        mClockView.setTextColor(Color.WHITE)
        mClockView.paint.isAntiAlias = false

        updateDisplayAndSetRefresh()
    }


    override fun onExitAmbient() {
        Timber.d("EXIT Ambient")
        super.onExitAmbient()

        if (mRunning) {
            mAmbientStateAlarmManager.cancel(mAmbientStatePendingIntent)
        }

        mTimeView.setTextColor(mActiveForegroundColor)
        val textPaint = mTimeView.paint
        textPaint.isAntiAlias = true
        textPaint.style = Paint.Style.FILL

        mStartStopButton.visibility = View.VISIBLE
        mResetButton.visibility = View.VISIBLE
        mBackground.setBackgroundColor(mActiveBackgroundColor)

        mClockView.setTextColor(mActiveForegroundColor)
        mClockView.paint.isAntiAlias = true

        mActiveClockUpdateHandler.sendEmptyMessage(R.id.msg_update)

        if (mRunning) {
            mNotice.visibility = View.INVISIBLE
            updateDisplayAndSetRefresh()
        }
    }


    override fun onDestroy() {
        Timber.d("onDestroy()")

        mActiveModeUpdateHandler.removeMessages(R.id.msg_update)
        mActiveClockUpdateHandler.removeMessages(R.id.msg_update)
        mAmbientStateAlarmManager.cancel(mAmbientStatePendingIntent)

        super.onDestroy()
    }

    /**
     * Simplify update handling for different types of updates.
     */
    private abstract class UpdateHandler(reference: MainActivity) : Handler() {

        val mMainActivityWeakReference: WeakReference<MainActivity> = WeakReference(reference)

        override fun handleMessage(message: Message) {
            val mainActivity = mMainActivityWeakReference.get() ?: return

            when (message.what) {
                R.id.msg_update -> handleUpdate(mainActivity)
            }
        }

        /**
         * Handle the update within this method.
         */
        abstract fun handleUpdate(mainActivity: MainActivity)
    }

    /**
     * Handle clock updates every minute.
     */
    private class UpdateClockHandler(reference: MainActivity) : UpdateHandler(reference) {

        // 60 seconds for updating the clock in active mode
        private val MINUTE_INTERVAL_MS: Long = TimeUnit.SECONDS.toMillis(60)

        override fun handleUpdate(mainActivity: MainActivity) {
            val timeMs = System.currentTimeMillis()
            val delayMs = MINUTE_INTERVAL_MS - timeMs % MINUTE_INTERVAL_MS
            Timber.d("NOT ambient - delaying by: $delayMs")
            mainActivity.mActiveClockUpdateHandler.sendEmptyMessageDelayed(R.id.msg_update, delayMs)
        }
    }


    private class UpdateStopwatchHandler(reference: MainActivity) : UpdateHandler(reference) {

        override fun handleUpdate(mainActivity: MainActivity) {
            mainActivity.updateDisplayAndSetRefresh()
        }
    }
}
