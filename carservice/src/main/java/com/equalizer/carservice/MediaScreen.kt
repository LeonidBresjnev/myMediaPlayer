package com.equalizer.carservice

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Template


class MediaScreen(
    carContext: CarContext,
): Screen(carContext) {



    override fun onGetTemplate(): Template {
        return ListTemplate.Builder()
            .setTitle("Media")
         /*   .setActionStrip(actionStrip)
            .setSingleList(singleList)
            .addAction(if (isPlaying!=Status.PLAYING) playPause else stopAction)*/
            .addAction(Action.BACK)
            .build()
    }


}
