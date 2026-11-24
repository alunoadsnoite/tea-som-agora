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
    private var repeatBothPending = false
    private var repeatedA: SoundReading? = null

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
    var labelA by mutableStateOf("")
        private set
    var labelB by mutableStateOf("")
        private set
    var rounds by mutableStateOf<List<ComparisonRound>>(emptyList())
        private set

    fun setLabel(point: Point, label: String) {
        if (active != null) return
        when (point) {
            Point.A -> labelA = label.take(MAX_LABEL_LENGTH)
            Point.B -> labelB = label.take(MAX_LABEL_LENGTH)
        }
    }

    fun start(point: Point) {
        if (active != null || (point == Point.B && pointA == null)) return
        message = null
        active = point
        secondsLeft = MEASUREMENT_SECONDS
        job = viewModelScope.launch {
            var capturedSuccessfully = false
            try {
                val result = recorder.measure { remaining ->
                    withContext(Dispatchers.Main.immediate) { secondsLeft = remaining }
                }
                capturedSuccessfully = true
                if (repeatBothPending) {
                    when (point) {
                        Point.A -> repeatedA = result
                        Point.B -> {
                            val newA = repeatedA ?: pointA ?: return@launch
                            pointA = newA
                            pointB = result
                            rounds = rounds + ComparisonRound(newA, result)
                            repeatBothPending = false
                            repeatedA = null
                        }
                    }
                } else {
                    when (point) {
                        Point.A -> {
                            pointA = result
                            pointB?.let { rounds = rounds + ComparisonRound(result, it) }
                        }
                        Point.B -> {
                            pointB = result
                            pointA?.let { rounds = rounds + ComparisonRound(it, result) }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: CaptureException) {
                if (error.problem == CaptureProblem.INPUT_CHANGED) {
                    pointA = null
                    pointB = null
                    rounds = emptyList()
                }
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
                if (repeatBothPending) {
                    if (point == Point.A && capturedSuccessfully) {
                        start(Point.B)
                    } else {
                        repeatBothPending = false
                        repeatedA = null
                    }
                }
            }
        }
    }

    fun repeatBoth() {
        if (active != null || pointA == null || pointB == null) return
        repeatBothPending = true
        repeatedA = null
        start(Point.A)
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
        labelA = ""
        labelB = ""
        rounds = emptyList()
        repeatBothPending = false
        repeatedA = null
    }

    private companion object {
        const val MAX_LABEL_LENGTH = 32
    }
}
