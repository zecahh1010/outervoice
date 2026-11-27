package com.zecadev.outervoice;

import android.app.Application;

/** Install BLE before any Activity, Service or scan receiver, as Zeekr Shortcut does. */
public final class OuterVoiceApplication extends Application {
    @Override public void onCreate(){super.onCreate();SmartButtonsService.remember(this);SmartButtonsController.install(this);}
}
