package com.flightaware.android.flightfeeder.analyzers.dump978

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.text.TextUtils
import com.flightaware.android.flightfeeder.BuildConfig
import com.flightaware.android.flightfeeder.analyzers.Analyzer
import marto.rtl_tcp_andro.core.RtlTcp
import marto.rtl_tcp_andro.core.RtlTcp.RtlTcpProcessListener
import kotlin.jvm.Throws

object Dump978 : Analyzer() {
    private var sConvertThread: Thread? = null
    private var sDetectThread: Thread? = null

    @Volatile
    var sExit = false
    private val sListener: RtlTcpProcessListener = object : RtlTcpProcessListener {
        override fun onProcessStarted() {
            // gib - decoder pipeline of processor threads
            // gib - rtl -> phi -> detect frames -> decode frames
            // Start threads in this order so that each consumer thread is
            // running before its upstream producer
            if (Analyzer.Companion.sDecodeThread == null) {
                Analyzer.Companion.sDecodeThread = DecodeFramesThread()
                Analyzer.Companion.sDecodeThread!!.start()
            }
            if (sDetectThread == null) {
                sDetectThread = DetectFramesThread()
                sDetectThread!!.start()
            }
            if (sConvertThread == null) {
                sConvertThread = ConvertToPhiThread()
                sConvertThread!!.start()
            }
            if (Analyzer.Companion.sReadThread == null) {
                // gib - if debug build, read data from test data file
                // if (BuildConfig.DEBUG)
                // sReadThread = new ReadTestDataFileThread();
                // else
                Analyzer.Companion.sReadThread = GetRtlSdrDataThread()
                Analyzer.Companion.sReadThread!!.start()
            }
        }

        override fun onProcessStdOutWrite(line: String?) {
            if (BuildConfig.DEBUG) println(line)
        }

        override fun onProcessStopped(exitCode: Int, e: Exception?) {
            sExit = true
            if (Analyzer.Companion.sReadThread != null) {
                Analyzer.Companion.sReadThread!!.interrupt()
                Analyzer.Companion.sReadThread = null
            }
            if (sConvertThread != null) {
                sConvertThread!!.interrupt()
                sConvertThread = null
            }
            if (sDetectThread != null) {
                sDetectThread!!.interrupt()
                sDetectThread = null
            }
            if (Analyzer.Companion.sDecodeThread != null) {
                Analyzer.Companion.sDecodeThread!!.interrupt()
                Analyzer.Companion.sDecodeThread = null
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
        RtlTcp.start("-f 978e6 -s 2083334", fileDescriptor, deviceName,
                sListener)
    }

    fun stop() {
        RtlTcp.stop()
        sExit = true
    }

    init {
        Fec.init()
    }
}