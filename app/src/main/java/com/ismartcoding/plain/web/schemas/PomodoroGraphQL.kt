package com.ismartcoding.plain.web.schemas

import com.ismartcoding.lib.kgraphql.schema.dsl.SchemaBuilder
import com.ismartcoding.lib.channel.sendEvent
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.db.AppDatabase
import com.ismartcoding.plain.events.HttpApiEvents
import com.ismartcoding.plain.helpers.TimeHelper
import com.ismartcoding.plain.preferences.PomodoroSettingsPreference
import com.ismartcoding.plain.ui.MainActivity
import com.ismartcoding.plain.web.models.PomodoroToday
import com.ismartcoding.plain.web.models.toModel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun SchemaBuilder.addPomodoroSchema() {
    query("pomodoroSettings") {
        resolver { ->
            PomodoroSettingsPreference.getValueAsync(MainApp.instance).toModel()
        }
    }
    query("pomodoroToday") {
        resolver { ->
            val dao = AppDatabase.instance.pomodoroItemDao()
            val today = TimeHelper.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
            val activity = MainActivity.instance.get()
            val vm = activity?.pomodoroVM
            if (vm != null) {
                PomodoroToday(
                    date = today,
                    completedCount = vm.completedCount.intValue,
                    currentRound = vm.currentRound.intValue,
                    timeLeft = vm.timeLeft.intValue,
                    totalTime = vm.settings.value.getTotalSeconds(vm.currentState.value),
                    isRunning = vm.isRunning.value,
                    isPause = vm.isPaused.value,
                    state = vm.currentState.value
                )
            } else {
                // Activity not alive yet (e.g. server started by boot/watchdog before user opened the app).
                // Return a safe snapshot built from persisted settings + DB so the web panel still loads.
                val settings = PomodoroSettingsPreference.getValueAsync(MainApp.instance)
                val state = com.ismartcoding.plain.ui.page.pomodoro.PomodoroState.WORK
                val totalSeconds = settings.getTotalSeconds(state)
                val completed = dao.getByDate(today)?.completedCount ?: 0
                PomodoroToday(
                    date = today,
                    completedCount = completed,
                    currentRound = 1,
                    timeLeft = totalSeconds,
                    totalTime = totalSeconds,
                    isRunning = false,
                    isPause = false,
                    state = state,
                )
            }
        }
    }
    mutation("startPomodoro") {
        resolver { timeLeft: Int ->
            sendEvent(HttpApiEvents.PomodoroStartEvent(timeLeft))
            true
        }
    }
    mutation("pausePomodoro") {
        resolver { ->
            sendEvent(HttpApiEvents.PomodoroPauseEvent())
            true
        }
    }
    mutation("stopPomodoro") {
        resolver { ->
            sendEvent(HttpApiEvents.PomodoroStopEvent())
            true
        }
    }
}
