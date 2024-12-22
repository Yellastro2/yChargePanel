package com.yellastrodev.ymtserial

import java.text.SimpleDateFormat
import java.util.*

val EVENT_TYPE = "type"

val EVENT_ADD_BANK = "add_bank"
val EVENT_REMOVE_BANK = "remove_bank"
val ENENT_CHANGE_NET = "change_net"
val EVENT_CHARGE = "charge"
val EVENT_SOME = "some"
val EVENT_MOTOR_STATE = "motor_state"
val EVENT_SLOTSTIL_BROKE = "slot_stilbroke"


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

val ROUT_UPLOADLOGS = "uploadLogs"
val ROUT_CHECKIN = "checkin"

val CMD_RELEASE = "release"
val CMD_GETLOGS = "getLogs"
val CMD_CHANGE_WALLPAPER = "wallpaper"


val logFileDateFormat = SimpleDateFormat("yyyy.MM.dd_HH-mm-ss", Locale.getDefault())

class KEY_STORE {
}