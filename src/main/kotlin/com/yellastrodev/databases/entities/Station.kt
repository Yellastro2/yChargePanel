package com.yellastrodev.databases.entities

import org.json.JSONObject



/**
 * Определение класса Station.
 *
 * @property stId Уникальный идентификатор станции.
 * @property size Количество слотов на станции (по умолчанию 0).
 * @property lastDayTraffic Строка с информацией о трафике за последний день.
 * @property state Состояние станции, представленное в виде объекта JSON.
 * @property events Список событий, связанных со станцией.
 * @property timestamp Метка времени в секундах.
 * @property qrString Строка QR-кода для станции.
 * @property wallpaper Имя файла обоев в папке wallpaper.
 * @property blockedSlots Побитовое представление массива заблокированных слотов. 0x1 = заблокирован.
 */
data class Station(
    val stId: String,
    var size: Int = 0,
    var lastDayTraffic: String = "",
    var state: JSONObject = JSONObject(),
    val events: ArrayList<JSONObject> = ArrayList(),
    var timestamp: Int = 0,
    var qrString: String = "",
    var wallpaper: String = "",
    var blockedSlots: Array<SlotStatus> = Array(size) { SlotStatus.UNBLOCKED }
)
{

    enum class SlotStatus {
        BLOCKED,
        UNBLOCKED
    }
}