package com.equalizer.carservice

import androidx.annotation.OptIn
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.annotations.ExperimentalCarApi
import androidx.car.app.model.Action
import androidx.car.app.model.Action.APP_ICON
import androidx.car.app.model.CarIcon
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Tab
import androidx.car.app.model.TabContents
import androidx.car.app.model.TabTemplate
import androidx.car.app.model.TabTemplate.TabCallback
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat

class TabScreen(carContext: CarContext) : Screen(carContext) {


    private val firstTab = TabInfo("first_tab", R.string.plus_button, R.drawable.audio_plus_background)
    private val secondTab = TabInfo("second_tab", R.string.minus_button, R.drawable.audio_minus_background)

    private var activeContentId: String = firstTab.tabId

    private fun getFirstTabTemplate() : Template {
        return MessageTemplate.Builder("This is my first tab").build()
    }

    private fun getSecondTabTemplate() : Template {
        return MessageTemplate.Builder("This is my second tab").build()
    }

    private fun getActiveTabContent(): TabContents {
        return if (activeContentId == firstTab.tabId) {

            TabContents.Builder(getFirstTabTemplate()).build()
        } else {
            TabContents.Builder(getSecondTabTemplate()).build()
        }
    }



    private fun getTab(tabInfo: TabInfo) = Tab.Builder()
        .setTitle(carContext.getString(tabInfo.tabTitle))
        .setIcon(
            CarIcon.Builder(IconCompat.createWithResource(carContext, tabInfo.tabIcon)).build()
        ).setContentId(tabInfo.tabId).build()

    override fun onGetTemplate(): Template {
        APP_ICON.onClickDelegate
        val tabTemplate = TabTemplate.Builder(object : TabCallback {
            override fun onTabSelected(tabContentId: String) {
                activeContentId = tabContentId
                invalidate() //call invalidate() to get the new template to display
            }
        })
            .setHeaderAction(APP_ICON)
        tabTemplate.addTab(getTab(firstTab))
        tabTemplate.addTab(getTab(secondTab))
        tabTemplate.setTabContents(getActiveTabContent())
        return tabTemplate.setActiveTabContentId(activeContentId).build()
    }
}


class TestScreen(carContext: CarContext) : Screen(carContext) {
    @OptIn(ExperimentalCarApi::class)
    override fun onGetTemplate(): Template {
        return MessageTemplate.Builder("Test Screen")
            .setHeaderAction(Action.BACK)
            .build()
    }

}