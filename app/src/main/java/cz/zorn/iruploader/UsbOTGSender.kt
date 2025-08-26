package cz.zorn.iruploader
/*
import android.util.Log
import android.widget.Toast
import com.esmart.ir.otg.UsbHostManager
import com.esmart.ir.otg.a
import com.esmart.ir.otg.b
import com.esmart.ir.otg.c
import com.esmart.ir.otg.d
import com.esmart.ir.otg.e
import java.util.Collections
import java.util.PriorityQueue
import java.util.Timer
import kotlin.experimental.and
import kotlin.experimental.inv
import kotlin.jvm.internal.Intrinsics

@OptIn(ExperimentalUnsignedTypes::class)
fun sendIRDataToExternalDevice(mUsbHostManager: UsbHostManager, irdata: Array<Int>, frequency: Int) {
    Intrinsics.checkNotNullParameter(mUsbHostManager, "mUsbHostManager")
    Intrinsics.checkNotNullParameter(irdata, "irdata")

    val context = this.e
    if (context == null) {
        Log.e("IROTG", "请先初始化SDK-[initApp]")
        return
    }

    if (!this.d) {
        Toast.makeText(context, "试用版已到期", Toast.LENGTH_SHORT).show()
        return
    }

    val iIntValue = irdata.sumOf { it }
    if (iIntValue >= 1000000) {
        return
    }

    when (mUsbHostManager.getDeviceIdentify()) {
        UsbHostManager.DeviceIdentify.d571 -> {
            if (irdata.isEmpty() || this.c != 0 || irdata.size % 2 != 0) {
                Log.i("IROTG", "还在发码中 直接返回了， 当前为 tempIndex=${this.c}  ")
                return
            }

            val uByteArrA: UByteArray = b().a(irdata)
            val arrayList = uByteArrA.map { it.toByte() }.toMutableList()

            val size = arrayList.size
            val i2 = (size shr 8) and 255
            val i3 = size and 255
            val i4 = frequency + 524287
            val i5 = i4 and 255
            val i6 = (i4 shr 8) and 255
            val i7 = (i4 shr 16) and 255

            val arrayList2 = arrayList.map { UByte(it.toUByte().data) }.toMutableList()

            arrayList2.add(0, UByte(a(UByte(i5.toByte())).data.inv()))
            arrayList2.add(0, UByte(a(UByte(i7.toByte())).data.inv()))
            arrayList2.add(0, UByte(a(UByte(i6.toByte())).data.inv()))
            arrayList2.add(0, UByte(a(UByte(i3.toByte())).data.inv()))
            arrayList2.add(0, UByte(a(UByte(i2.toByte())).data.inv()))
            repeat(4) { arrayList2.add(0, UByte(-1)) }

            val size2 = (arrayList2.size + 62) / 63
            val size3 = arrayList2.size % 63

            this.f666b = ArrayList()
            for (i8 in 0 until size2) {
                val arrayList3 = if (i8 != size2 - 1 || size3 <= 0) {
                    val i9 = i8 * 63
                    arrayList2.subList(i9, i9 + 63)
                } else {
                    val i10 = i8 * 63
                    arrayList2.subList(i10, i10 + size3)
                }
                val uByteA = a(arrayList3 as ArrayList<UByte>)
                uByteA?.let { arrayList3.add(it) }
                this.f666b.add(arrayList3 as ArrayList<UByte>)
            }
            this.c = 0
            this.f665a.cancel()
            this.f665a = Timer()
            this.f665a.schedule(a.a(mUsbHostManager, this), 0L, 2L)
        }
        UsbHostManager.DeviceIdentify.d552, UsbHostManager.DeviceIdentify.d552_old -> {
            if (irdata.isEmpty() || this.c != 0 || irdata.size % 2 != 0) {
                Log.i("IROTG", "还在发码中 直接返回了， 当前为 tempIndex=${this.c}  ")
                return
            }
            if (irdata.size % 2 != 0) {
                Log.i("IROTG", "码值为奇数，不正确  ")
                return
            }

            val uByteArrA = b().a(irdata)
            val arrayList5 = uByteArrA.map { it.toInt().toChar() }
            val result = ArrayList<Byte>()
            val iArr = IntArray(256)
            for (cCharValue in arrayList5) {
                iArr[cCharValue.toInt()]++
            }

            val priorityQueue = PriorityQueue<e>()
            for (i in iArr.indices) {
                if (iArr[i] > 0) {
                    priorityQueue.offer(b.b(iArr[i], i.toChar()))
                }
            }

            while (priorityQueue.size > 1) {
                priorityQueue.offer(c(priorityQueue.poll(), priorityQueue.poll()))
            }
            val eVar = priorityQueue.poll()
            b.a.f402a = ArrayList()
            b.a.a(eVar, StringBuffer())
            Collections.sort(b.a.f402a, b.f())

            val size4 = (b.a.f402a.size shr 8) and 255
            val size5 = b.a.f402a.size and 255
            result.add(size4.toByte())
            result.add(size5.toByte())
            for (i in 0 until b.a.f402a.size) {
                val d = b.a.f402a[i]
                b.a.f403b[d.f407b] = d.c
                result.add(d.f407b.toByte())
                val i15 = (d.f406a shr 8) and 255
                val i16 = d.f406a and 255
                result.add(i15.toByte())
                result.add(i16.toByte())
            }

            val sb = StringBuilder()
            for (ch in arrayList5) {
                sb.append(b.a.f403b[ch])
            }
            val length = sb.length
            var length2 = length % 8
            var i = 0
            if (length2 > 0) {
                i = 8 - length2
                length2 = length / 8 + 1
                for (j in 0 until i) {
                    sb.append('0')
                }
            } else {
                length2 = length / 8
            }
            result.add(i.toByte())
            for (k in 0 until length2) {
                val i20 = k * 8
                result.add(Integer.parseInt(sb.substring(i20, i20 + 8), 2).toByte())
            }

            val size6 = result.size
            val i21 = (size6 shr 8) and 255
            val i22 = size6 and 255
            println("\n  协议的头 : $size6  协议的频率: $frequency  \n")

            val i23 = frequency + 524287
            val i24 = i23 and 255
            val i25 = (i23 shr 8) and 255
            val i26 = (i23 shr 16) and 255

            val arrayList7 = result.map { it.toUByte() }.toMutableList()
            arrayList7.add(0, UByte(a(UByte(i22.toByte())).data.inv()))
            arrayList7.add(0, UByte(a(UByte(i21.toByte())).data.inv()))
            arrayList7.add(0, UByte(a(UByte(i24.toByte())).data.inv()))
            arrayList7.add(0, UByte(a(UByte(i26.toByte())).data.inv()))
            arrayList7.add(0, UByte(a(UByte(i25.toByte())).data.inv()))
            repeat(4) { arrayList7.add(0, UByte(-1)) }

            val size7 = (arrayList7.size + 62) / 63
            val size8 = arrayList7.size % 63

            this.f666b = ArrayList()
            for (i27 in 0 until size7) {
                val arrayList8 = if (i27 != size7 - 1 || size8 <= 0) {
                    val i28 = i27 * 63
                    arrayList7.subList(i28, i28 + 63)
                } else {
                    val i29 = i27 * 63
                    arrayList7.subList(i29, i29 + size8)
                }
                val uByteA3 = a(arrayList8 as ArrayList<UByte>)
                uByteA3?.let { arrayList8.add(it) }
                this.f666b.add(arrayList8 as ArrayList<UByte>)
            }

            this.c = 0
            this.f665a.cancel()
            this.f665a = Timer()
            this.f665a.schedule(a.a(mUsbHostManager, this), 0L, 2L)
        }
        else -> {
            Log.d("IROTG", mUsbHostManager.getDeviceIdentify().name)
            Log.i("IROTG,", "未知设备,请查看相关资料")
        }
    }
}*/