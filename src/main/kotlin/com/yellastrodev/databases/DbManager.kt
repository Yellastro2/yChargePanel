package com.yellastrodev.databases

import com.yellastrodev.databases.entities.Powerbank
import com.yellastrodev.databases.entities.Station

val database: DbManager = PostgreeManager()



interface DbManager {

    fun updateStation(station: Station)

    fun getStationById(stId: String): Station?

    /**
     * возвращает пару (список станций - одна страница , количество станций прошедших фильтр - без учета страницы)
     */
    fun getStations(limit: Int = 20, offset: Int = 0, filter: String = "", onlineSeconds: Int = 0): Pair<List<Station>, Int>

    /**
     * возвращает (количество станций в базе , станции с timestamp > fTimestamp) - общее и онлайн
     */
    fun getStationCount(fTimestamp: Int = 0): Pair<Int, Int>


    fun updatePowerbank(powerbank: Powerbank)
    fun getPowerbankById(id: String): Powerbank?

    /**
     * Возвращает список объектов Powerbank по их ID
     */
    fun getPowerbanksByIds(ids: List<String>): List<Powerbank>

}