package co.kyald.coronavirustracking.data.repository

import androidx.lifecycle.MutableLiveData
import co.kyald.coronavirustracking.data.database.dao.jhu.S2CoronaDao
import co.kyald.coronavirustracking.data.database.model.CoronaEntity
import co.kyald.coronavirustracking.data.database.model.jhu.S2CoronaEntity
import co.kyald.coronavirustracking.data.database.model.worldometers.S4CoronaEntity
import co.kyald.coronavirustracking.data.network.category.CoronaS2Api
import co.kyald.coronavirustracking.utils.Constants
import co.kyald.coronavirustracking.utils.InternetChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext


class CoronaS2Repository(
    private val s2CoronaDao: S2CoronaDao,
    private val coronaS2Service: CoronaS2Api
) {

    var coronaLiveDataS2: MutableLiveData<List<CoronaEntity>> = MutableLiveData()

    var coronaCountryLiveDataS2: MutableLiveData<CoronaEntity> = MutableLiveData()

    var isFinished: MutableLiveData<Map<String, Boolean>> = MutableLiveData()

    var totalCases: MutableLiveData<Map<String, String>> = MutableLiveData()

    fun getCoronaDataS2(): List<S2CoronaEntity> = s2CoronaDao.getAllCases()

    suspend fun callCoronaDataS2() = coronaS2Service.fetchJHUCSSE()

    private suspend fun callCoronaCountryDataS2(countryName: String) = coronaS2Service.fetchCountry(countryName = countryName)

    fun fetchCoronaDataS2(coroutineContext: CoroutineContext = Dispatchers.IO) {

        isFinished.postValue(
            mapOf(
                "done" to false,
                "internet" to false
            )
        )

        InternetChecker(object : InternetChecker.Consumer {
            override fun accept(internet: Boolean) {

                CoroutineScope(coroutineContext).launch {

                    if (internet) {

                        val response = callCoronaDataS2()

                        if (response.isSuccessful) {
                            s2CoronaDao.deleteAll()

                            response.body()?.let { it ->
                                coronaLiveDataS2.postValue(listCoronaEntityData(it))
                                s2CoronaDao.save(it)
                            }

                            totalCases.postValue(
                                mapOf(
                                    "confirmed" to s2CoronaDao.getTotalConfirmedCase().toString(),
                                    "recovered" to s2CoronaDao.getTotalRecoveredCase().toString(),
                                    "deaths" to s2CoronaDao.getTotalDeathCase().toString()
                                )
                            )

                        }

                    } else {


                        coronaLiveDataS2.postValue(listCoronaEntityData(s2CoronaDao.getAllCases()))

                        totalCases.postValue(
                            mapOf(
                                "confirmed" to s2CoronaDao.getTotalConfirmedCase().toString(),
                                "recovered" to s2CoronaDao.getTotalRecoveredCase().toString(),
                                "deaths" to s2CoronaDao.getTotalDeathCase().toString()
                            )
                        )

                        isFinished.postValue(
                            mapOf(
                                "done" to true,
                                "internet" to false
                            )
                        )
                    }
                }
            }
        })
    }

    fun listCoronaEntityData(data: List<S2CoronaEntity>): List<CoronaEntity> {

        val coronaEntity: MutableList<CoronaEntity> = mutableListOf()

        data.forEach {

            try {
                coronaEntity.add(
                    CoronaEntity(
                        data_source = Constants.DATA_SOURCE.DATA_S2.value,
                        data_source_name = "John Hopkins CSSE",
                        info = CoronaEntity.DataInfo(
                            country = "${it.country} ${

                            if(!it.province.isNullOrEmpty()) {
                                "("+it.province+")"
                            } else {
                                ""
                            }
                            }",
                            latitude = it.coordinates.latitude?.toDouble(),
                            longitude = it.coordinates.longitude?.toDouble(),
                            case_actives = 0,
                            case_confirms = it.stats.confirmed?.toLong(),
                            case_deaths = it.stats.deaths?.toLong(),
                            case_recovered = it.stats.recovered?.toLong(),
                            flags = ""
                        )
                    )
                )
            } catch (nfe: NumberFormatException) {
                coronaEntity.add(
                    CoronaEntity(
                        data_source = Constants.DATA_SOURCE.DATA_S2.value,
                        data_source_name = "John Hopkins CSSE",
                        info = CoronaEntity.DataInfo(
                            country = "${it.country} ${

                            if(!it.province.isNullOrEmpty()) {
                                "("+it.province+")"
                            } else {
                                ""
                            }
                            }",
                            latitude = 0.0,
                            longitude = 0.0,
                            case_actives = 0,
                            case_confirms = it.stats.confirmed?.toLong(),
                            case_deaths = it.stats.deaths?.toLong(),
                            case_recovered = it.stats.recovered?.toLong(),
                            flags = ""
                        )
                    )
                )
            }

            isFinished.postValue(
                mapOf(
                    "done" to true,
                    "internet" to true
                )
            )
        }

        return coronaEntity
    }

    fun coronaEntityData(it: S4CoronaEntity): CoronaEntity {
        return  CoronaEntity(
            data_source = Constants.DATA_SOURCE.DATA_S4.value,
            data_source_name = "worldometers",
            info = CoronaEntity.DataInfo(
                country = it.country,
                latitude = it.countryInfo.info_lat ?: 0.0,
                longitude = it.countryInfo.info_long ?: 0.0,
                case_actives = it.active,
                case_confirms = it.cases,
                case_deaths = it.deaths,
                case_recovered = it.recovered,
                flags = it.countryInfo.info_flag
            )
        )
    }


    fun fetchCoronaCountryDataS2(
        coroutineContext: CoroutineContext = Dispatchers.IO,
        countryName: String
    ) {

        InternetChecker(object : InternetChecker.Consumer {
            override fun accept(internet: Boolean) {

                CoroutineScope(coroutineContext).launch {

                    if (internet) {

                        val response = callCoronaCountryDataS2(countryName)

                        if (response.isSuccessful) {
                            response.body()?.let { it ->
                                coronaCountryLiveDataS2.postValue(coronaEntityData(it))
                            }
                        }

                    } else {

//                        coronaCountryLiveDataS2.postValue(coronaEntityData(s2CoronaDao.getAllCases()))

                    }
                }
            }
        })
    }

}

