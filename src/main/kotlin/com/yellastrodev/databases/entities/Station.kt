package com.yellastrodev.databases.entities

import org.json.JSONObject



/**
 * Определение класса Station.
 *
 * @property stId Уникальный идентификатор станции.
 * @property size Количество слотов на станции (по умолчанию 0).
 * @property lastDayTraffic Строка с информацией о трафике за последний день.
 * @property state Состояние станции, представленное в виде объекта JSON. Пример:
 * { // индекс начинается с 1 так как порт станции возвращает их номера также
 *     "1": {
 *         "date": 1738412825755,
 *         "some": "0",
 *         "bankId": "F2000C35E1",
 *         "charge": "70",
 *         "slotId": "1",
 *         "type": "add_bank",
 *         "motor_state": "0"
 *     },
 *     "4": {
 *         "date": 1738354729308,
 *         "some": "0",
 *         "bankId": "F2000C35E7",
 *         "charge": "100",
 *         "slotId": "12",
 *         "type": "add_bank",
 *         "motor_state": "0"
 *     }
 * }
 * @property events Список событий, связанных со станцией.
 * @property timestamp Метка времени в секундах.
 * @property qrString Строка QR-кода для станции.
 * @property wallpaper Имя файла обоев в папке uploads/wallpaper.
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
    var blockedSlots: Array<Status> = Array(size) { Status.AVAILABLE },
    var status: Status = Status.AVAILABLE,
    var apkVersion: String = ""
)
{


    enum class Status {
        BLOCKED,
        AVAILABLE
    }
}