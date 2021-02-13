package marto.rtl_tcp_andro.core

import android.os.SystemClock
import android.util.Log
import com.flightaware.android.flightfeeder.BuildConfig

object RtlTcp {
    val EXIT_CANNOT_CLOSE: Int = 6
    val EXIT_CANNOT_RESTART: Int = 5
    val EXIT_FAILED_TO_OPEN_DEVICE: Int = 4
    val EXIT_INVALID_FD: Int = 2
    val EXIT_NO_DEVICES: Int = 3
    val EXIT_NOT_ENOUGH_POWER: Int = 9
    val EXIT_OK: Int = 0
    val EXIT_SIGNAL_CAUGHT: Int = 8
    val EXIT_UNKNOWN: Int = 7
    val EXIT_WRONG_ARGS: Int = 1

    @Volatile
    private var sExitCode: Int = EXIT_UNKNOWN
    private var sListener: RtlTcpProcessListener? = null
    private val TAG: String = "RTLTCP"
    private external fun close(): Int // throws RtlTcpException;
    val isNativeRunning: Boolean
        external get

    private fun onclose(exitcode: Int) {
        sExitCode = exitcode
        if (BuildConfig.DEBUG) {
            if (exitcode != EXIT_OK) Log.e(TAG, "Exitcode " + exitcode) else Log.d(TAG, "Exited with success")
        }
    }

    private fun onopen() {
        if (sListener != null) sListener!!.onProcessStarted()
        if (BuildConfig.DEBUG) Log.d(TAG, "Device open")
    }

    private external fun open(args: String, descriptor: Int,
                              usbFsPath: String) // throws RtlTcpException;

    private fun printf_receiver(data: String) {
        if (sListener != null) sListener!!.onProcessStdOutWrite(data)
        if (BuildConfig.DEBUG) Log.d(TAG, data)
    }

    private fun printf_stderr_receiver(data: String) {
        if (sListener != null) sListener!!.onProcessStdOutWrite(data)
        if (BuildConfig.DEBUG) Log.w(TAG, data)
    }

    @Throws(Exception::class)
    fun start(args: String, fd: Int,
              uspfs_path: String, listener: RtlTcpProcessListener?) {
        object : Thread() {
            public override fun run() {
                val maxWait: Long = 10000
                var e: Exception? = null
                if (isNativeRunning) {
                    close()
                    var waited: Long = 0
                    while (isNativeRunning && waited < maxWait) {
                        SystemClock.sleep(100)
                        waited += 100
                    }
                    if (isNativeRunning && sListener != null) {
                        sListener!!.onProcessStopped(sExitCode, Exception(EXIT_CANNOT_RESTART.toString()))
                        return
                    }
                }
                sListener = listener
                sExitCode = EXIT_UNKNOWN
                open(args, fd, uspfs_path)
                if (isNativeRunning) {
                    close()
                    var waited: Long = 0
                    while (isNativeRunning && waited < maxWait) {
                        SystemClock.sleep(100)
                        waited += 100
                    }
                    if (isNativeRunning) sExitCode = EXIT_CANNOT_CLOSE
                }
                if (sExitCode != EXIT_OK) e = Exception(sExitCode.toString())
                if (sListener != null) sListener!!.onProcessStopped(sExitCode, e)
            }
        }.start()
    }

    fun stop() {
        if (!isNativeRunning) return
        close()
    }

    open interface RtlTcpProcessListener {
        fun onProcessStarted()

        /**
         * Whenever the process writes something to its stdout, this will get
         * called
         */
        fun onProcessStdOutWrite(line: String?)
        fun onProcessStopped(exitCode: Int, e: Exception?)
    }

    init {
        System.loadLibrary("rtltcp")
    }
}