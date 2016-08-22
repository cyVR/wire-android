/**
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient

import android.content.Context
import android.media.AudioManager
import android.os.{PowerManager, Vibrator}
import android.support.multidex.MultiDexApplication
import com.waz.api.{NetworkMode, ZMessagingApi, ZMessagingApiFactory}
import com.waz.service.{MediaManagerService, PreferenceService, ZMessaging}
import com.waz.utils.events.{EventContext, Signal, Subscription}
import com.waz.zclient.calling.{CallPermissionsController, CurrentCallController}
import com.waz.zclient.camera.{AndroidCameraFactory, GlobalCameraController}
import com.waz.zclient.controllers.global.{KeyboardController, AccentColorController}
import com.waz.zclient.messages.MessageViewFactory

object WireApplication {
  var APP_INSTANCE: WireApplication = _

  lazy val Global = new Module {
    bind[Signal[Option[ZMessaging]]] to ZMessaging.currentUi.currentZms
    bind[Signal[ZMessaging]] to inject[Signal[Option[ZMessaging]]].collect { case Some(z) => z }
    bind[PreferenceService] to new PreferenceService(inject[Context])
    bind[AccentColorController] to new AccentColorController()
    bind[GlobalCallingController] to new GlobalCallingController(inject[Context])
    bind[GlobalCameraController] to new GlobalCameraController(inject[Context], new AndroidCameraFactory)(EventContext.Global)
    bind[MediaManagerService] to ZMessaging.currentGlobal.mediaManager
    bind[MessageViewFactory] to new MessageViewFactory()

    //Global android services
    bind[PowerManager] to inject[Context].getSystemService(Context.POWER_SERVICE).asInstanceOf[PowerManager]
    bind[Vibrator] to inject[Context].getSystemService(Context.VIBRATOR_SERVICE).asInstanceOf[Vibrator]
    bind[AudioManager] to inject[Context].getSystemService(Context.AUDIO_SERVICE).asInstanceOf[AudioManager]
  }

  def services(ctx: WireContext) = new Module {
    bind [ZMessagingApi] to new ZMessagingApiProvider(ctx).api
    bind [Signal[ZMessaging]] to inject[ZMessagingApi].asInstanceOf[com.waz.api.impl.ZMessagingApi].ui.currentZms.collect{case Some(zms)=> zms }
    bind [Signal[NetworkMode]]
  }

  def controllers(implicit ctx: WireContext) = new Module {
    bind[KeyboardController] to new KeyboardController()
    bind[CurrentCallController] to new CurrentCallController()
    bind[CallPermissionsController] to new CallPermissionsController()

    bind[PermissionActivity] to ctx.asInstanceOf[PermissionActivity]
    bind[PermissionsController] to new PermissionsController(new PermissionsWrapper)
  }
}

class WireApplication extends MultiDexApplication with WireContext with Injectable {
  type NetworkSignal = Signal[NetworkMode]
  import WireApplication._
  WireApplication.APP_INSTANCE = this

  override def eventContext: EventContext = EventContext.Global

  lazy val module: Injector = Global :: AppModule

  def contextModule(ctx: WireContext): Injector = controllers(ctx) :: services(ctx) :: ContextModule(ctx)
}

class ZMessagingApiProvider(ctx: WireContext) {
  val api = ZMessagingApiFactory.getInstance(ctx)

  api.onCreate(ctx)

  ctx.eventContext.register(new Subscription {
    override def subscribe(): Unit = api.onResume()
    override def unsubscribe(): Unit = api.onPause()
    override def enable(): Unit = ()
    override def disable(): Unit = ()
    override def destroy(): Unit = api.onDestroy()
    override def disablePauseWithContext(): Unit = ()
  })
}
