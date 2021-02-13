package com.flightaware.android.flightfeeder.analyzers.dump978

import com.flightaware.android.flightfeeder.BuildConfig
import java.util.*

class DetectFramesThread : Thread() {
    private var mCenterDeltaPhi = 0
    private val mDemodBufA = IntArray(UPLINK_FRAME_BYTES)
    private val mDemodBufB = IntArray(UPLINK_FRAME_BYTES)
    private val mInterleaved = IntArray(UPLINK_FRAME_BYTES)
    private lateinit var mPhi: IntArray
    private var mRsErrorsA = 0
    private var mRsErrorsB = 0

    // check that there is a valid sync word starting at 'phi' that matches the
    // sync word 'pattern'. Place the dphi threshold to use for bit slicing in
    // 'mCenterDPhi'. Return 1 if the sync word is OK, 0 on failure
    private fun checkSyncWord(index: Int, pattern: Long): Boolean {
        var deltaPhiZeroTotal = 0
        var zeroBits = 0
        var deltaPhiOneTotal = 0
        var oneBits = 0
        var errorBits = 0
        var deltaPhi = 0
        var mask: Long = 0
        var i: Byte = 0

        // find mean dphi for zero and one bits; take the mean of the two as our
        // central value
        i = 0
        while (i < SYNC_BITS) {
            deltaPhi = phiDifference(mPhi[index + i * 2], mPhi[index + i * 2 + 1])
            mask = 1L shl 35 - i
            if (pattern and mask == mask) {
                oneBits++
                deltaPhiOneTotal += deltaPhi
            } else {
                zeroBits++
                deltaPhiZeroTotal += deltaPhi
            }
            i++
        }
        deltaPhiZeroTotal /= zeroBits
        deltaPhiOneTotal /= oneBits
        mCenterDeltaPhi = (deltaPhiOneTotal + deltaPhiZeroTotal) / 2

        // recheck sync word using our center value
        errorBits = 0
        i = 0
        while (i < SYNC_BITS && errorBits <= MAX_SYNC_ERRORS) {
            deltaPhi = phiDifference(mPhi[index + i * 2], mPhi[index + i * 2 + 1])
            mask = 1L shl 35 - i  // gib - shl is shift left op
            if (pattern and mask == mask) {
                // this should be a '1', above the center value
                if (deltaPhi < mCenterDeltaPhi) errorBits++
            } else {
                // this should be a '0', below the center value
                if (deltaPhi >= mCenterDeltaPhi) errorBits++
            }
            i++
        }
        return errorBits <= MAX_SYNC_ERRORS
    }

    // Demodulate an adsb frame
    // with the first sync bit in 'phi', storing the frame into 'to'
    // of length up to LONG_FRAME_BYTES. Set '*rs_errors' to the
    // number of corrected errors, or 9999 if demodulation failed.
    // Return 0 if demodulation failed, or the number of bits (not
    // samples) consumed if demodulation was OK.
    private fun demodAdsbFrame(index: Int, isA: Boolean): Int {
        if (!checkSyncWord(index, ADSB_SYNC_WORD)) {
            if (isA) mRsErrorsA = 9999 else mRsErrorsB = 9999
            return 0
        }
        val results = IntArray(2)
        if (isA) {
            demodFrame(index + SYNC_BITS * 2, mDemodBufA)
            Fec.correctAdsbFrame(mDemodBufA, results)
            mRsErrorsA = results[1]
        } else {
            demodFrame(index + SYNC_BITS * 2, mDemodBufB)
            Fec.correctAdsbFrame(mDemodBufB, results)
            mRsErrorsB = results[1]
        }
        val frameType = results[0]
        return if (frameType == 2) {
            SYNC_BITS + LONG_FRAME_BITS
        } else if (frameType == 1) {
            SYNC_BITS + SHORT_FRAME_BITS
        } else 0
    }

    // demodulate bytes from samples at 'mPhi[index]' into 'frame', using
    // 'mCenterDPhi' as the bit slicing threshold
    private fun demodFrame(index: Int, frame: IntArray) {
        var index = index
        var b: Int
        for (i in frame.indices) {
            b = 0
            if (phiDifference(mPhi[index], mPhi[index + 1]) > mCenterDeltaPhi) b = b or 0x80
            if (phiDifference(mPhi[index + 2], mPhi[index + 3]) > mCenterDeltaPhi) b = b or 0x40
            if (phiDifference(mPhi[index + 4], mPhi[index + 5]) > mCenterDeltaPhi) b = b or 0x20
            if (phiDifference(mPhi[index + 6], mPhi[index + 7]) > mCenterDeltaPhi) b = b or 0x10
            if (phiDifference(mPhi[index + 8], mPhi[index + 9]) > mCenterDeltaPhi) b = b or 0x08
            if (phiDifference(mPhi[index + 10], mPhi[index + 11]) > mCenterDeltaPhi) b = b or 0x04
            if (phiDifference(mPhi[index + 12], mPhi[index + 13]) > mCenterDeltaPhi) b = b or 0x02
            if (phiDifference(mPhi[index + 14], mPhi[index + 15]) > mCenterDeltaPhi) b = b or 0x01
            frame[i] = b
            index += 16
        }
    }

    // Demodulate an uplink frame
    // with the first sync bit in 'phi', storing the frame into 'to'
    // of length up to UPLINK_FRAME_BYTES. Set '*rs_errors' to the
    // number of corrected errors, or 9999 if demodulation failed.
    // Return 0 if demodulation failed, or the number of bits (not
    // samples) consumed if demodulation was OK.
    private fun demodUplinkFrame(index: Int, isA: Boolean): Int {
        if (!checkSyncWord(index, UPLINK_SYNC_WORD)) {
            if (isA) mRsErrorsA = 9999 else mRsErrorsB = 9999
            return 0
        }
        demodFrame(index + SYNC_BITS * 2, mInterleaved)
        val results = IntArray(2)

        // deinterleave and correct
        if (isA) {
            Fec.correctUplinkFrame(mInterleaved, mDemodBufA, results)
            mRsErrorsA = results[1]
        } else {
            Fec.correctUplinkFrame(mInterleaved, mDemodBufB, results)
            mRsErrorsB = results[1]
        }
        val frametype = results[0]
        return if (frametype == 1) {
            UPLINK_FRAME_BITS + SYNC_BITS
        } else 0
    }

    private fun handleAdsbFrame(frame: IntArray, errors: Int) {
        if (frame[0] shr 3 == 0) FrameQueue.offer(Arrays.copyOf(frame, SHORT_FRAME_DATA_BYTES)) else FrameQueue.offer(Arrays.copyOf(frame, LONG_FRAME_DATA_BYTES))
    }

    private fun handleUplinkFrame(frame: IntArray, errors: Int) {
        // UatFrameQueue.offer(Arrays.copyOf(frame, UPLINK_FRAME_DATA_BYTES));
    }

    private fun phiDifference(from: Int, to: Int): Int {
        val difference = to - from // lies in the range -65535 .. +65535
        return if (difference >= 32768) // +32768..+65535
            difference - 65536 // -> -32768..-1: always in range
        else if (difference < -32768) // -65535..-32769
            difference + 65536 // -> +1..32767: always in range
        else difference
    }

    private fun processPhi() {
        var syncA: Long = 0
        var syncB: Long = 0
        var startBit = 0
        var isAdsbSyncA = false
        var isUplinkSyncA = false
        var index = 0
        var skipA = 0
        var skipB = 0

        // We expect samples at twice the UAT bitrate.
        // We look at phase difference between pairs of adjacent samples, i.e.
        // sample 1 - sample 0 -> sync0
        // sample 2 - sample 1 -> sync1
        // sample 3 - sample 2 -> sync0
        // sample 4 - sample 3 -> sync1
        // ...
        //
        // We accumulate bits into two buffers, sync0 and sync1.
        // Then we compare those buffers to the expected 36-bit sync word that
        // should be at the start of each UAT frame. When (if) we find it,
        // that tells us which sample to start decoding from.

        // Stop when we run out of remaining samples for a max-sized frame.
        val lenbits = mPhi.size / 2 - SYNC_BITS - UPLINK_FRAME_BITS
        var bit = 0
        while (bit < lenbits) {
            syncA = syncA shl 1 or if (phiDifference(mPhi[bit * 2],
                            mPhi[bit * 2 + 1]) > mCenterDeltaPhi) 1 else (0 and SYNC_MASK.toInt()).toLong()
            syncB = syncB shl 1 or if (phiDifference(mPhi[bit * 2 + 1],
                            mPhi[bit * 2 + 2]) > mCenterDeltaPhi) 1 else (0 and SYNC_MASK.toInt()).toLong()
            if (bit < SYNC_BITS) {
                bit++
                continue  // haven't fully populated sync0/1 yet
            }

            // see if we have (the start of) a valid sync word
            // It would be nice to look at popcount(expected ^ sync)
            // so we can tolerate some errors, but that turns out
            // to be very expensive to do on every sample

            // when we find a match, try to demodulate both with that match
            // and with the next position, and pick the one with fewer
            // errors.

            // check for downlink frames:
            isAdsbSyncA = syncWordFuzzyCompare(syncA, ADSB_SYNC_WORD)
            if (isAdsbSyncA || syncWordFuzzyCompare(syncB, ADSB_SYNC_WORD)) {
                startBit = bit - SYNC_BITS + 1
                index = startBit * 2 + if (isAdsbSyncA) 0 else 1
                mRsErrorsB = -1
                mRsErrorsA = mRsErrorsB
                skipA = demodAdsbFrame(index, true)
                skipB = demodAdsbFrame(index + 1, false)
                if (skipA > 0 && mRsErrorsA <= mRsErrorsB) {
                    handleAdsbFrame(mDemodBufA, mRsErrorsA)
                    bit = startBit + skipA
                } else if (skipB > 0 && mRsErrorsB <= mRsErrorsA) {
                    handleAdsbFrame(mDemodBufB, mRsErrorsB)
                    bit = startBit + skipB
                } else {
                    // demod failed
                }
                bit++
                continue
            }

            // check for uplink frames
            isUplinkSyncA = syncWordFuzzyCompare(syncA, UPLINK_SYNC_WORD)
            if (isUplinkSyncA || syncWordFuzzyCompare(syncB, UPLINK_SYNC_WORD)) {
                startBit = bit - SYNC_BITS + 1
                index = startBit * 2 + if (isUplinkSyncA) 0 else 1
                mRsErrorsB = -1
                mRsErrorsA = mRsErrorsB
                skipA = demodUplinkFrame(index, true)
                skipB = demodUplinkFrame(index + 1, false)
                if (skipA > 0 && mRsErrorsA <= mRsErrorsB) {
                    handleUplinkFrame(mDemodBufA, mRsErrorsA)
                    bit = startBit + skipA
                } else if (skipB > 0 && mRsErrorsB <= mRsErrorsA) {
                    handleUplinkFrame(mDemodBufB, mRsErrorsB)
                    bit = startBit + skipB
                } else {
                    // demod failed
                }
            }
            bit++
        }
    }

    override fun run() {
        if (BuildConfig.DEBUG) println("Started analyzing phi")
        while (!Dump978.sExit) {
            val phi = PhiQueue.take() ?: continue
            mPhi = phi
            processPhi()
        }
        if (BuildConfig.DEBUG) println("Stopped analyzing phi")
    }

    private fun syncWordFuzzyCompare(syncWord: Long, compareTo: Long): Boolean {
        if (syncWord == compareTo) return true
        var diff = syncWord xor compareTo // guaranteed nonzero

        // This is a bit-twiddling popcount hack, tweaked as we only care about
        // "<N" or ">=N" set bits for fixed N - so we can bail out early after
        // seeing N set bits.
        //
        // It relies on starting with a nonzero value with zero or more trailing
        // clear bits after the last set bit:
        //
        // 010101010101010000
        // ^
        // Subtracting one, will flip the bits starting at the last set bit:
        //
        // 010101010101001111
        // ^
        // then we can use that as a bitwise-and mask to clear the lowest set
        // bit:
        //
        // 010101010101000000
        // ^
        // And repeat until the value is zero or we have seen too many set bits.

        // >= 1 bit
        diff = diff and diff - 1 // clear lowest set bit
        if (diff == 0L) return true // 1 bit error

        // >= 2 bits
        diff = diff and diff - 1 // clear lowest set bit
        if (diff == 0L) return true // 2 bit errors

        // >= 3 bits
        diff = diff and diff - 1 // clear lowest set bit
        if (diff == 0L) return true // 3 bit errors

        // >= 4 bits
        diff = diff and diff - 1 // clear lowest set bit
        return if (diff == 0L) true else false // 4 bit errors

        // > 4 bit errors, give up
    }

    companion object {
        private const val ADSB_SYNC_WORD = 0xEACDDA4E2L
        private const val LONG_FRAME_DATA_BITS = 272
        private const val LONG_FRAME_BITS = LONG_FRAME_DATA_BITS + 112
        protected const val LONG_FRAME_DATA_BYTES = LONG_FRAME_DATA_BITS / 8
        private const val MAX_SYNC_ERRORS = 4
        private const val SHORT_FRAME_DATA_BITS = 144
        private const val SHORT_FRAME_BITS = SHORT_FRAME_DATA_BITS + 96
        protected const val SHORT_FRAME_DATA_BYTES = SHORT_FRAME_DATA_BITS / 8
        private const val SYNC_BITS = 36
        private const val SYNC_MASK = 0xFFFFFFFFFL
        private const val UPLINK_BLOCK_DATA_BITS = 576
        private const val UPLINK_BLOCK_BITS = UPLINK_BLOCK_DATA_BITS + 160
        private const val UPLINK_FRAME_BLOCKS: Byte = 6
        private const val UPLINK_FRAME_BITS = (UPLINK_FRAME_BLOCKS
                * UPLINK_BLOCK_BITS)
        private const val UPLINK_FRAME_BYTES = UPLINK_FRAME_BITS / 8
        private const val UPLINK_FRAME_DATA_BITS = (UPLINK_FRAME_BLOCKS
                * UPLINK_BLOCK_DATA_BITS)
        const val UPLINK_FRAME_DATA_BYTES = UPLINK_FRAME_DATA_BITS / 8
        private const val UPLINK_SYNC_WORD = 0x153225B1DL
    }

    init {
        name = "DetectFramesThread"
    }
}