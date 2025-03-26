package com.yellastrodev.ymtserial

import java.text.SimpleDateFormat
import java.util.*

val KEY_STATION_ID = "stId"
val KEY_COMMAND = "command"
val KEY_VALUE = "value"
val KEY_ONLINE_STATE = "onlineState"
val KEY_SIZE = "size"
val KEY_STATE = "state"
val KEY_TRAFFIC = "traffic"
val KEY_TIMEOUT = "X-Timeout"
val KEY_EVENT = "events"
val KEY_TIMESTAMP = "timestamp"
val KEY_TRAFFIC_LAST_DAY = "trafficLastDay"
val KEY_DATE = "date"
val KEY_NUM = "num"
val KEY_PATH = "path"
val KEY_TEXT = "text"
val KEY_AVAIBLE = "available"
val KEY_BANK_ID = "bankId"
val KEY_STATUS = "status"

val EVENT_TYPE = "type"

val EVENT_ADD_BANK = "add_bank"
val EVENT_REMOVE_BANK = "remove_bank"
val ENENT_CHANGE_NET = "change_net"
val EVENT_CHARGE = "charge"
val EVENT_SOME = "some"
val EVENT_MOTOR_STATE = "motor_state"
val EVENT_SLOTSTIL_BROKE = "slot_stilbroke"
val EVENT_CONNECTION = "connection"


val EVENT_CHARGE_OLD = "oldCharge"
val EVENT_CHARGE_NEW = "newCharge"
val EVENT_BANK_ID = "bankId"
val EVENT_SLOT_ID = "slotId"
val EVENT_ISSUE_COUNT = "issue_count"

val EVENT_BOOT_COMPLEATE = "boot compleate"

val EVENT_DESTROY = "destroy"
val EVENT_CREATE = "create"
val EVENT_OPEN_DEV_ACTIVITY = "open_dev_activity"
val EVENT_ERROR_OPEN_SERIALPORT = "error_open_serialport"

val EVENT_NETWORK = "network"

val STATE_KEYS_TRAFFIC = "traffic"
val PACKAGE_VERSION = "packageVersion"

val ROUT_UPLOADLOGS = "uploadLogs"
val ROUT_CHECKIN = "checkin"
val ROUT_STATIONINFO = "stationInfo"
val ROUT_STATIONLIST = "stationList"
val ROUT_GET_LOGS = "getLogs"
val ROUT_SET_QR = "setQR"
val ROUT_UPLOAD_FILE_FORSTATION = "upload"
val WALLPAPER_ROUT = "wallpaper"
val APK_ROUT = "apk"
val WEBZIP_ROUT = "webzip"

val ROUT_DOWNLOAD = "download"
val ROUT_RELEASE = "release"
val ROUT_FORCE = "force"
val ROUT_BLOCK_SLOT = "block_slot"
val ROUT_STATION = "/station"
val ROUT_UPDATE_BANK_STATUS = "updateBankStatus"
val ROUT_REBOOT = "reboot"
val ROUT_DISABLE_STATION = "disableStation"

val PATH_BASE = "uploads"
val PATH_WALLPAPERS = "${PATH_BASE}/wallpapers"
val PATH_APK = "${PATH_BASE}/apks"
val PATH_WEBZIP = "${PATH_BASE}/webzips"

val CMD_RELEASE = "release"
val CMD_FORCE = "force"
val CMD_GETLOGS = "getLogs"
val CMD_CHANGE_WALLPAPER = "wallpaper"
val CMD_CHANGE_QR = "QR_data"
val CMD_REBOOT = "reboot"
val CMD_UPDATE_APK = "update_apk"
const val CMD_CHANGE_WEBVIEW = "webview"


val CMD_CANCEL = "cancel" // внутренняя команда сервера о перезапуске соединения клиентом


val logFileDateFormat = SimpleDateFormat("yyyy.MM.dd_HH-mm-ss", Locale.getDefault())

class KEY_STORE {
}