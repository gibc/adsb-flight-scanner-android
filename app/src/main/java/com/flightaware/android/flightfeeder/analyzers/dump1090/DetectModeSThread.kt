package com.flightaware.android.flightfeeder.analyzers.dump1090

import com.flightaware.android.flightfeeder.BuildConfig
import com.flightaware.android.flightfeeder.analyzers.AvrFormatExporter
import com.flightaware.android.flightfeeder.analyzers.AvrFormatMessageQueue
import com.flightaware.android.flightfeeder.analyzers.BeastFormatExporter
import com.flightaware.android.flightfeeder.analyzers.BeastFormatMessageQueue

class DetectModeSThread : Thread() {
    private var mClockCount: Long = 0
    private lateinit var mVector: IntArray

    // Work out the best phase offset to use for the given message.
    private fun best_phase(index: Int): Int {
        var test: Int
        var best = -1

        // minimum correlation quality we will accept
        var bestval = (mVector[index + 0] + mVector[index + 1]
                + mVector[index + 2] + mVector[index + 3] + mVector[index + 4] + mVector[index + 5])

        // empirical testing suggests that 4..8 is the best range to test for
        // here (testing a wider range runs the danger of picking the wrong
        // phase for a message that would otherwise be successfully decoded -
        // the correlation functions can match well with a one symbol / half bit
        // offset)

        // this is consistent with the peak detection which should produce
        // the first data symbol with phase offset 4..8
        test = correlate_check_4(index + 0)
        if (test > bestval) {
            bestval = test
            best = 4
        }
        test = correlate_check_0(index + 1)
        if (test > bestval) {
            bestval = test
            best = 5
        }
        test = correlate_check_1(index + 1)
        if (test > bestval) {
            bestval = test
            best = 6
        }
        test = correlate_check_2(index + 1)
        if (test > bestval) {
            bestval = test
            best = 7
        }
        test = correlate_check_3(index + 1)
        if (test > bestval) {
            bestval = test
            best = 8
        }
        return best
    }

    //
    // These functions work out the correlation quality for the 10 symbols (5
    // bits) starting at m[0] + given phase offset.
    // This is used to find the right phase offset to use for decoding.
    private fun correlate_check_0(index: Int): Int {
        return (Math.abs(correlate_phase0(index + 0))
                + Math.abs(correlate_phase2(index + 2))
                + Math.abs(correlate_phase4(index + 4))
                + Math.abs(correlate_phase1(index + 7))
                + Math.abs(correlate_phase3(index + 9)))
    }

    // 2.4MHz sampling rate version
    //
    // When sampling at 2.4MHz we have exactly 6 samples per 5 symbols.
    // Each symbol is 500ns wide, each sample is 416.7ns wide
    //
    // We maintain a phase offset that is expressed in units of 1/5 of a sample
    // i.e. 1/6 of a symbol, 83.333ns
    // Each symbol we process advances the phase offset by 6 i.e. 6/5 of a
    // sample, 500ns
    //
    // The correlation functions below correlate a 1-0 pair of symbols (i.e.
    // manchester encoded 1 bit)
    // starting at the given sample, and assuming that the symbol starts at a
    // fixed 0-5 phase offset within
    // m[0]. They return a correlation value, generally interpreted as >0 = 1
    // bit, <0 = 0 bit
    // TODO check if there are better (or more balanced) correlation functions
    // to use here
    private fun correlate_check_1(index: Int): Int {
        return (Math.abs(correlate_phase1(index + 0))
                + Math.abs(correlate_phase3(index + 2))
                + Math.abs(correlate_phase0(index + 5))
                + Math.abs(correlate_phase2(index + 7))
                + Math.abs(correlate_phase4(index + 9)))
    }

    private fun correlate_check_2(index: Int): Int {
        return (Math.abs(correlate_phase2(index + 0))
                + Math.abs(correlate_phase4(index + 2))
                + Math.abs(correlate_phase1(index + 5))
                + Math.abs(correlate_phase3(index + 7))
                + Math.abs(correlate_phase0(index + 10)))
    }

    private fun correlate_check_3(index: Int): Int {
        return (Math.abs(correlate_phase3(index + 0))
                + Math.abs(correlate_phase0(index + 3))
                + Math.abs(correlate_phase2(index + 5))
                + Math.abs(correlate_phase4(index + 7))
                + Math.abs(correlate_phase1(index + 10)))
    }

    private fun correlate_check_4(index: Int): Int {
        return (Math.abs(correlate_phase4(index + 0))
                + Math.abs(correlate_phase1(index + 3))
                + Math.abs(correlate_phase3(index + 5))
                + Math.abs(correlate_phase0(index + 8))
                + Math.abs(correlate_phase2(index + 10)))
    }

    private fun correlate_phase0(index: Int): Int {
        return slice_phase0(index) * 26
    }

    private fun correlate_phase1(index: Int): Int {
        return slice_phase1(index) * 38
    }

    private fun correlate_phase2(index: Int): Int {
        return slice_phase2(index) * 38
    }

    private fun correlate_phase3(index: Int): Int {
        return slice_phase3(index) * 26
    }

    private fun correlate_phase4(index: Int): Int {
        return slice_phase4(index) * 19
    }

    /*
	 * Detect a Mode S messages inside the magnitude buffer. Every detected Mode
	 * S message is convert it into a stream of bits and passed to the decoder
	 * function.
	 */
    private fun detectModeS() {
        val scanLength = mVector.size - 38
        var j = 0
        var high = 0
        var index = 0
        var base_signal: Long
        var base_noise: Long
        var phase: Int
        var msg: IntArray? = null
        var theByte: Int
        var i = 0
        while (i < scanLength) {

            // Look for a message starting at around sample 0 with phase offset
            // 3..7

            // Ideal sample values for preambles with different phase
            // Xn is the first data symbol with phase offset N
            //
            // sample#: 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0
            // phase 3: 2/4\0/5\1 0 0 0 0/5\1/3 3\0 0 0 0 0 0 X4
            // phase 4: 1/5\0/4\2 0 0 0 0/4\2 2/4\0 0 0 0 0 0 0 X0
            // phase 5: 0/5\1/3 3\0 0 0 0/3 3\1/5\0 0 0 0 0 0 0 X1
            // phase 6: 0/4\2 2/4\0 0 0 0 2/4\0/5\1 0 0 0 0 0 0 X2
            // phase 7: 0/3 3\1/5\0 0 0 0 1/5\0/4\2 0 0 0 0 0 0 X3
            //

            // quick check: we must have a rising edge 0->1 and a falling edge
            // 12->13
            if (!(mVector[i] < mVector[i + 1] && mVector[i + 12] > mVector[i + 13])) {
                i++
                continue
            }
            if (mVector[i + 1] > mVector[i + 2] && // 1
                    mVector[i + 2] < mVector[i + 3] && mVector[i + 3] > mVector[i + 4] && // 3
                    mVector[i + 8] < mVector[i + 9] && mVector[i + 9] > mVector[i + 10] && // 9
                    mVector[i + 10] < mVector[i + 11]) { // 11-12
                // peaks at 1,3,9,11-12: phase 3
                high = (mVector[i + 1] + mVector[i + 3] + mVector[i + 9]
                        + mVector[i + 11] + mVector[i + 12]) / 4
                base_signal = mVector[i + 1] + mVector[i + 3] + mVector[i + 9].toLong()
                base_noise = mVector[i + 5] + mVector[i + 6] + mVector[i + 7].toLong()
            } else if (mVector[i + 1] > mVector[i + 2] && // 1
                    mVector[i + 2] < mVector[i + 3] && mVector[i + 3] > mVector[i + 4] && // 3
                    mVector[i + 8] < mVector[i + 9] && mVector[i + 9] > mVector[i + 10] && // 9
                    mVector[i + 11] < mVector[i + 12]) { // 12
                // peaks at 1,3,9,12: phase 4
                high = (mVector[i + 1] + mVector[i + 3] + mVector[i + 9] + mVector[i + 12]) / 4
                base_signal = (mVector[i + 1] + mVector[i + 3] + mVector[i + 9]
                        + mVector[i + 12]).toLong()
                base_noise = (mVector[i + 5] + mVector[i + 6] + mVector[i + 7]
                        + mVector[i + 8]).toLong()
            } else if (mVector[i + 1] > mVector[i + 2] && // 1
                    mVector[i + 2] < mVector[i + 3] && mVector[i + 4] > mVector[i + 5] && // 3-4
                    mVector[i + 8] < mVector[i + 9] && mVector[i + 10] > mVector[i + 11] && // 9-10
                    mVector[i + 11] < mVector[i + 12]) { // 12
                // peaks at 1,3-4,9-10,12: phase 5
                high = (mVector[i + 1] + mVector[i + 3] + mVector[i + 4]
                        + mVector[i + 9] + mVector[i + 10] + mVector[i + 12]) / 4
                base_signal = mVector[i + 1] + mVector[i + 12].toLong()
                base_noise = mVector[i + 6] + mVector[i + 7].toLong()
            } else if (mVector[i + 1] > mVector[i + 2] && // 1
                    mVector[i + 3] < mVector[i + 4] && mVector[i + 4] > mVector[i + 5] && // 4
                    mVector[i + 9] < mVector[i + 10] && mVector[i + 10] > mVector[i + 11] && // 10
                    mVector[i + 11] < mVector[i + 12]) { // 12
                // peaks at 1,4,10,12: phase 6
                high = (mVector[i + 1] + mVector[i + 4] + mVector[i + 10] + mVector[i + 12]) / 4
                base_signal = (mVector[i + 1] + mVector[i + 4] + mVector[i + 10]
                        + mVector[i + 12]).toLong()
                base_noise = (mVector[i + 5] + mVector[i + 6] + mVector[i + 7]
                        + mVector[i + 8]).toLong()
            } else if (mVector[i + 2] > mVector[i + 3] && // 1-2
                    mVector[i + 3] < mVector[i + 4] && mVector[i + 4] > mVector[i + 5] && // 4
                    mVector[i + 9] < mVector[i + 10] && mVector[i + 10] > mVector[i + 11] && // 10
                    mVector[i + 11] < mVector[i + 12]) { // 12
                // peaks at 1-2,4,10,12: phase 7
                high = (mVector[i + 1] + mVector[i + 2] + mVector[i + 4]
                        + mVector[i + 10] + mVector[i + 12]) / 4
                base_signal = (mVector[i + 4] + mVector[i + 10]
                        + mVector[i + 12]).toLong()
                base_noise = mVector[i + 6] + mVector[i + 7] + mVector[i + 8].toLong()
            } else {
                // no suitable peaks
                i++
                continue
            }

            // Check for enough signal
            if (base_signal * 2 < 3 * base_noise) {
                i++
                // about 3.5dB SNR
                continue
            }

            // Check that the "quiet" bits 6,7,15,16,17 are actually quiet
            if (mVector[i + 5] >= high || mVector[i + 6] >= high || mVector[i + 7] >= high || mVector[i + 8] >= high || mVector[i + 14] >= high || mVector[i + 15] >= high || mVector[i + 16] >= high || mVector[i + 17] >= high || mVector[i + 18] >= high) {
                i++
                continue
            }

            // Crosscorrelate against the first few bits to find a likely phase
            // offset
            phase = best_phase(i + 19)
            if (phase < 0) {
                i++
                continue  // nothing satisfactory
            }

            /*
			 * Decode all the next 112 bits, regardless of the actual message
			 * size. We'll check the actual message type later.
			 */index = i + 19 + phase / 5
            phase = phase % 5
            msg = null
            var bytelen = MODES_LONG_MSG_BYTES.toInt()
            j = 0
            while (j < bytelen && index < scanLength) {
                theByte = 0
                when (phase) {
                    0 -> {
                        theByte = ((if (slice_phase0(index) > 0) 0x80 else 0)
                                or (if (slice_phase2(index + 2) > 0) 0x40 else 0)
                                or (if (slice_phase4(index + 4) > 0) 0x20 else 0)
                                or (if (slice_phase1(index + 7) > 0) 0x10 else 0)
                                or (if (slice_phase3(index + 9) > 0) 0x08 else 0)
                                or (if (slice_phase0(index + 12) > 0) 0x04 else 0)
                                or (if (slice_phase2(index + 14) > 0) 0x02 else 0)
                                or if (slice_phase4(index + 16) > 0) 0x01 else 0)
                        phase = 1
                        index += 19
                    }
                    1 -> {
                        theByte = ((if (slice_phase1(index) > 0) 0x80 else 0)
                                or (if (slice_phase3(index + 2) > 0) 0x40 else 0)
                                or (if (slice_phase0(index + 5) > 0) 0x20 else 0)
                                or (if (slice_phase2(index + 7) > 0) 0x10 else 0)
                                or (if (slice_phase4(index + 9) > 0) 0x08 else 0)
                                or (if (slice_phase1(index + 12) > 0) 0x04 else 0)
                                or (if (slice_phase3(index + 14) > 0) 0x02 else 0)
                                or if (slice_phase0(index + 17) > 0) 0x01 else 0)
                        phase = 2
                        index += 19
                    }
                    2 -> {
                        theByte = ((if (slice_phase2(index) > 0) 0x80 else 0)
                                or (if (slice_phase4(index + 2) > 0) 0x40 else 0)
                                or (if (slice_phase1(index + 5) > 0) 0x20 else 0)
                                or (if (slice_phase3(index + 7) > 0) 0x10 else 0)
                                or (if (slice_phase0(index + 10) > 0) 0x08 else 0)
                                or (if (slice_phase2(index + 12) > 0) 0x04 else 0)
                                or (if (slice_phase4(index + 14) > 0) 0x02 else 0)
                                or if (slice_phase1(index + 17) > 0) 0x01 else 0)
                        phase = 3
                        index += 19
                    }
                    3 -> {
                        theByte = ((if (slice_phase3(index) > 0) 0x80 else 0)
                                or (if (slice_phase0(index + 3) > 0) 0x40 else 0)
                                or (if (slice_phase2(index + 5) > 0) 0x20 else 0)
                                or (if (slice_phase4(index + 7) > 0) 0x10 else 0)
                                or (if (slice_phase1(index + 10) > 0) 0x08 else 0)
                                or (if (slice_phase3(index + 12) > 0) 0x04 else 0)
                                or (if (slice_phase0(index + 15) > 0) 0x02 else 0)
                                or if (slice_phase2(index + 17) > 0) 0x01 else 0)
                        phase = 4
                        index += 19
                    }
                    4 -> {
                        theByte = ((if (slice_phase4(index) > 0) 0x80 else 0)
                                or (if (slice_phase1(index + 3) > 0) 0x40 else 0)
                                or (if (slice_phase3(index + 5) > 0) 0x20 else 0)
                                or (if (slice_phase0(index + 8) > 0) 0x10 else 0)
                                or (if (slice_phase2(index + 10) > 0) 0x08 else 0)
                                or (if (slice_phase4(index + 12) > 0) 0x04 else 0)
                                or (if (slice_phase1(index + 15) > 0) 0x02 else 0)
                                or if (slice_phase3(index + 17) > 0) 0x01 else 0)
                        phase = 0
                        index += 20
                    }
                }
                if (j == 0) {
                    when (theByte shr 3) {
                        0, 4, 5, 11 -> {
                            bytelen = MODES_SHORT_MSG_BYTES.toInt()
                            msg = IntArray(MODES_SHORT_MSG_BYTES.toInt())
                        }
                        16, 17, 18, 20, 21, 24 -> msg = IntArray(MODES_LONG_MSG_BYTES.toInt())
                        else -> {
                            bytelen = 1 // unknown DF, give up immediately
                            j++
                            continue
                        }
                    }
                }
                msg!![j] = theByte
                j++
            }
            if (msg == null || j != msg.size) {
                i++
                continue
            }

            // When we reach this point, we may have a Mode S message on our
            // hands. It may still be broken and the CRC may not be correct,
            // but this can be handled by the next layer.
            // set the clock count to the base value plus the sample number * 5
            val modeS = ModeSMessage(msg, mClockCount + i * 5)
            var signal_power: Double
            var scaled_signal_power: Long = 0
            val signal_len = msg.size * 8 * 12 / 5
            var k: Int
            k = 0
            while (k < signal_len) {
                val mag = mVector[i + 19 + k]
                scaled_signal_power += mag * mag.toLong()
                ++k
            }
            signal_power = scaled_signal_power / 65535.0 / 65535.0
            modeS.signalLevel = signal_power / signal_len
            ModeSMessageQueue.offer(modeS)
            if (AvrFormatExporter.sIsEnabled) AvrFormatMessageQueue.offer(modeS)
            if (BeastFormatExporter.sIsEnabled) BeastFormatMessageQueue.offer(modeS)

            // move the loop pointer so we don't rescan this part
            i += signal_len
            i++
        }
    }

    override fun run() {
        if (BuildConfig.DEBUG) println("Started analyzing vectors")
        while (!Dump1090.sExit) {
            val vector = MagnitudeVectorQueue.take() ?: continue
            mVector = vector
            detectModeS()

            // advance clock count assuming a 12MHz clock
            mClockCount += vector.size * 5.toLong()
        }
        if (BuildConfig.DEBUG) println("Stopped analyzing vectors")
    }

    // nb: the correlation functions sum to zero, so we do not need to adjust
    // for the DC offset in the input signal
    // (adding any constant value to all of m[0..3] does not change the result)
    private fun slice_phase0(index: Int): Int {
        return 5 * mVector[index + 0] - 3 * mVector[index + 1] - (2
                * mVector[index + 2])
    }

    private fun slice_phase1(index: Int): Int {
        return 4 * mVector[index + 0] - mVector[index + 1] - (3
                * mVector[index + 2])
    }

    private fun slice_phase2(index: Int): Int {
        return 3 * mVector[index + 0] + mVector[index + 1] - 4 * mVector[index + 2]
    }

    private fun slice_phase3(index: Int): Int {
        return 2 * mVector[index + 0] + 3 * mVector[index + 1] - 5 * mVector[index + 2]
    }

    private fun slice_phase4(index: Int): Int {
        return mVector[index + 0] + 5 * mVector[index + 1] - (5
                * mVector[index + 2]) - mVector[index + 3]
    }

    companion object {
        private const val MODES_LONG_MSG_BYTES: Byte = 14
        private const val MODES_SHORT_MSG_BYTES: Byte = 7
    }

    init {
        name = "DetectModeSThread"
    }
}