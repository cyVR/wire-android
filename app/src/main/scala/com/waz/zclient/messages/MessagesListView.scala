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
package com.waz.zclient.messages

import android.content.Context
import android.support.v7.widget.{LinearLayoutManager, RecyclerView}
import android.util.AttributeSet
import android.view.ViewGroup
import com.waz.ZLog._
import com.waz.model.MessageData
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.controllers.context.ScrollController
import com.waz.zclient.{Injectable, Injector, ViewHelper}

class MessagesListView(context: Context, attrs: AttributeSet, style: Int) extends RecyclerView(context, attrs, style) with ViewHelper {

  import MessagesListView._
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  val scrollController = inject[ScrollController]
  val messagesLoaded = Signal(false)

  val layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)

  def onLoaded() = {
    messagesLoaded ! true
    scrollController.lastVisibleItemIndex ! layoutManager.findLastCompletelyVisibleItemPosition
  }

  setHasFixedSize(true)
  setLayoutManager(layoutManager)
  setAdapter(new MessagesListAdapter(onLoaded()))

  scrollController.scrollPosition.zip(messagesLoaded).on(Threading.Ui) {
    case (pos, true) =>
      verbose(s"Scrolling to pos: $pos")
//      layoutManager.scrollToPositionWithOffset(pos, 0) //TODO may have to calculate different offset for images and large assets?
    case _ => //to early to scroll
  }
}

object MessagesListView {
  private implicit val Tag: LogTag = logTagFor[MessagesListView]
}

case class MessageViewHolder(view: MessageView) extends RecyclerView.ViewHolder(view)

class MessagesListAdapter(onMessagesLoaded: => Unit)(implicit inj: Injector, ev: EventContext) extends RecyclerView.Adapter[MessageViewHolder]() with Injectable { adapter =>
  import MessagesListAdapter._

  verbose("MessagesListAdapter created")

  val zms = inject[Signal[ZMessaging]]
  val selectedConv = zms.flatMap(_.convsStats.selectedConversationId).collect { case Some(convId) => convId }
  var messages: RecyclerCursor = _

  zms.zip(selectedConv).on(Threading.Ui) { case (zs, conv) =>
    Option(messages).foreach(_.close())
    messages = new RecyclerCursor(conv, zs, adapter)
    notifyDataSetChanged()
    onMessagesLoaded
  }

  override def getItemCount: Int = if (messages == null) 0 else messages.count

  override def getItemViewType(position: Int): Int = MessageView.viewType(messages(position).message.msgType)

  override def onBindViewHolder(holder: MessageViewHolder, position: Int): Unit = {
    zms.currentValue.foreach { zms =>
      selectedConv.currentValue.foreach(zms.convsUi.setLastRead(_, messages(position).message))
    }

    holder.view.set(position, messages(position).message, if (position == 0) None else Some(messages(position - 1).message))
  }

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder =
    MessageViewHolder(MessageView(parent, viewType))
}

object MessagesListAdapter {
  private implicit val Tag: LogTag = logTagFor[MessagesListAdapter]

  implicit val MessageOrdering: Ordering[MessageData] = Ordering.by(_.time.toEpochMilli)
}
