package com.flightaware.android.flightfeeder.analyzers.dump1090

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.text.TextUtils
import com.flightaware.android.flightfeeder.BuildConfig
import com.flightaware.android.flightfeeder.analyzers.Analyzer
import com.flightaware.android.flightfeeder.analyzers.dump1090.Decoder.CorrectionLevel
import marto.rtl_tcp_andro.core.RtlTcp
import marto.rtl_tcp_andro.core.RtlTcp.RtlTcpProcessListener
import kotlin.jvm.Throws

object Dump1090 : Analyzer() {
    private var sComputeThread: Thread? = null
    private var sDetectThread: Thread? = null

    @Volatile
    var sExit = false
    private val sListener: RtlTcpProcessListener = object : RtlTcpProcessListener {
        override fun onProcessStarted() {
            // Start threads in this order so that each consumer thread is
            // running before its upstream producer
            if (sDecodeThread == null) {
                sDecodeThread = DecodeFramesThread()
                sDecodeThread!!.start()
            }
            if (sDetectThread == null) {
                sDetectThread = DetectModeSThread()
                sDetectThread!!.start()
            }
            if (sComputeThread == null) {
                sComputeThread = ComputeMagnitudeVectorThread()
                sComputeThread!!.start()
            }
            if (Analyzer.Companion.sReadThread == null) {
                Analyzer.Companion.sReadThread = GetRtlSdrDataThread()
                Analyzer.Companion.sReadThread!!.start()
            }
        }

        override fun onProcessStdOutWrite(line: String?) {
            if (BuildConfig.DEBUG) println(line)
        }

        override fun onProcessStopped(exitCode: Int, e: Exception?) {
            if (Analyzer.Companion.sReadThread != null) {
                Analyzer.Companion.sReadThread!!.interrupt()
                Analyzer.Companion.sReadThread = null
            }
            if (sComputeThread != null) {
                sComputeThread!!.interrupt()
                sComputeThread = null
            }
            if (sDetectThread != null) {
                sDetectThread!!.interrupt()
                sDetectThread = null
            }
            if (sDecodeThread != null) {
                sDecodeThread!!.interrupt()
                sDecodeThread = null
            }
        }
    }

    @Throws(Exception::class)
    fun start(usbManager: UsbManager?, usbDevice: UsbDevice?) {
        val connection = usbManager!!.openDevice(usbDevice)
        val fileDescriptor = connection.fileDescriptor
        val deviceName: String = Analyzer.Companion.getDeviceName(usbDevice!!.deviceName)
        if (fileDescriptor == -1 || TextUtils.isEmpty(deviceName)) throw RuntimeException(
                "USB file descriptor or device name is invalid")
        sExit = false
        RtlTcp.start("-f 1090e6 -s 2.4e6", fileDescriptor, deviceName,
                sListener)
    }

    fun stop() {
        RtlTcp.stop()
        sExit = true
    }

    init {
        Decoder.init(CorrectionLevel.ONE_BIT)
    }
}