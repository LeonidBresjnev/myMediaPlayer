package com.equalizer.carservice

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.annotations.ExperimentalCarApi
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.Accelerometer
import androidx.car.app.hardware.info.CarHardwareLocation
import androidx.car.app.hardware.info.CarSensors
import androidx.car.app.hardware.info.Compass
import androidx.car.app.hardware.info.EnergyLevel
import androidx.car.app.hardware.info.Gyroscope
import androidx.car.app.hardware.info.Mileage
import androidx.car.app.hardware.info.Model
import androidx.car.app.hardware.info.Speed
import com.google.android.material.animation.AnimatableView.Listener

@ExperimentalCarApi
class CarSensors(carContext: CarContext) {
    val carInfo = carContext.getCarService(CarHardwareManager::class.java).carInfo
    val carSensors = carContext.getCarService(CarHardwareManager::class.java).carSensors

    private fun log(msg: String) {
        Log.d("CarInfo", msg)
    }
    val modelListener = OnCarDataAvailableListener<Model> { data ->
        data.name.value?.let { log(it) }
        data.name.value?.let{ log(it) }
        data.manufacturer.value?.let{ log(it) }

    }

    val energylistener = OnCarDataAvailableListener<EnergyLevel> { data ->
        log("Range remaining: ${data.rangeRemainingMeters.value} meters")
        if (data.rangeRemainingMeters.status == CarValue.STATUS_SUCCESS) {
            val rangeRemaining = data.rangeRemainingMeters.value
            log("Range remaining: $rangeRemaining meters")
        } else {
            // Handle error
            log("error")
        }
    }

    val speedListener = OnCarDataAvailableListener<Speed> { data ->
        if (data.rawSpeedMetersPerSecond.status == CarValue.STATUS_SUCCESS) {
            log("speed: ${data.rawSpeedMetersPerSecond.value} meters, ${data.displaySpeedMetersPerSecond.value}")
        }
        if (data.speedDisplayUnit.status == CarValue.STATUS_SUCCESS) {
            log("speed: ${data.speedDisplayUnit.value}")
        }
        if (data.displaySpeedMetersPerSecond.status == CarValue.STATUS_SUCCESS) {
            log("speed: ${data.displaySpeedMetersPerSecond.value}")

        }
    }
/*
    val mileageListener = OnCarDataAvailableListener<Mileage> { data ->
        if (data.odometerMeters.status == CarValue.STATUS_SUCCESS) {
            log("mileage (odometerMeters): ${data.odometerMeters.value}")
        }

        if (data.distanceDisplayUnit.status == CarValue.STATUS_SUCCESS) {
            log("mileage (DisplayUnit): ${data.distanceDisplayUnit.value}")
        }
    }*/

    val compassListener = OnCarDataAvailableListener<Compass> { data ->
        log("compass: ${data.orientations.value}")
        if (data.orientations.status == CarValue.STATUS_SUCCESS) {
            log("compass: ${data.orientations.value}")
        }
    }

    val locationListener = OnCarDataAvailableListener<CarHardwareLocation> { data ->
        if (data.location.status == CarValue.STATUS_SUCCESS) {
            log("location: ${data.location.value}")
        }

    }

    val accelerometerListener = OnCarDataAvailableListener<Accelerometer> { data ->
        if (data.forces.status == CarValue.STATUS_SUCCESS) {
            log("accelerometer: ${data.forces.value}")
        }
    }

    val gyroListener = OnCarDataAvailableListener<Gyroscope> { data ->
        log("gyro: ${data.rotations.value}")
        if (data.rotations.status == CarValue.STATUS_SUCCESS) {
            log("gyro: ${data.rotations.value}")
        }
    }

    init {
        carSensors.addGyroscopeListener(CarSensors.UPDATE_RATE_FASTEST, carContext.mainExecutor, gyroListener)
        carSensors.addAccelerometerListener(CarSensors.UPDATE_RATE_FASTEST, carContext.mainExecutor, accelerometerListener)
        carSensors.addCarHardwareLocationListener(CarSensors.UPDATE_RATE_FASTEST, carContext.mainExecutor, locationListener)
        carSensors.addCompassListener(CarSensors.UPDATE_RATE_FASTEST, carContext.mainExecutor, compassListener)
        carInfo.addEnergyLevelListener(carContext.mainExecutor, energylistener)
        carInfo.addSpeedListener(carContext.mainExecutor, speedListener)
       // carInfo.addMileageListener(carContext.mainExecutor, mileageListener)
    }
}