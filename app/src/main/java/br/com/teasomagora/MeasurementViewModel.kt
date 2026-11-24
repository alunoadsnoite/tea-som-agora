package br.com.teasomagora

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class Point(@param:StringRes val label: Int) {
    A(R.string.point_a), B(R.string.point_b),
}

internal class MeasurementViewModel(application: Application) : AndroidViewModel(application) {
    private val recorder = SoundRecorder(application)
    private var job: Job? = null

    var pointA by mutableStateOf<SoundReading?>(null)
        private set
    var pointB by mutableStateOf<SoundReading?>(null)
        private set
    var active by mutableStateOf<Point?>(null)
        private set
    var secondsLeft by mutableIntStateOf(MEASUREMENT_SECONDS)
        private set
    var message by mutableStateOf<Int?>(null)
        private set

    fun start(point: Point) {
        if (active != null || (point == Point.B && pointA == null)) return
        message = null
        active = point
        secondsLeft = MEASUREMENT_SECONDS
        job = viewModelScope.launch {
            try {
                val result = recorder.measure { remaining ->
                    withContext(Dispatchers.Main.immediate) { secondsLeft = remaining }
                }
                when (point) {
                    Point.A -> pointA = result
                    Point.B -> pointB = result
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: CaptureException) {
                message = when (error.problem) {
                    CaptureProblem.UNAVAILABLE -> R.string.capture_error
                    CaptureProblem.INPUT_CHANGED -> R.string.input_changed
                    CaptureProblem.CLIPPED -> R.string.clipping
                }
            } catch (_: SecurityException) {
                permissionDenied()
            } catch (_: IllegalStateException) {
                message = R.string.capture_error
            } catch (_: IllegalArgumentException) {
                message = R.string.capture_error
            } finally {
                active = null
                job = null
            }
        }
    }

    fun permissionDenied() {
        message = R.string.permission_denied
    }

    fun cancel() {
        job?.cancel()
    }

    fun reset() {
        if (active != null) return
        pointA = null
        pointB = null
        message = null
    }
}
