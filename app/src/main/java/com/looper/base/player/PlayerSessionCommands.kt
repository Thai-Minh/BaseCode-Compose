package com.looper.base.player

import android.content.Context
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import com.looper.base.R

object PlayerSessionCommands {

    const val ACTION_TOGGLE_FAVORITE = "com.looper.base.player.TOGGLE_FAVORITE"
    const val ACTION_TOGGLE_SHUFFLE = "com.looper.base.player.TOGGLE_SHUFFLE"
    const val ACTION_CYCLE_REPEAT = "com.looper.base.player.CYCLE_REPEAT"
    const val EXTRA_AUDIO_SESSION_ID = "audio_session_id"

    private val toggleFavorite = SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY)
    private val toggleShuffle = SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)
    private val cycleRepeat = SessionCommand(ACTION_CYCLE_REPEAT, Bundle.EMPTY)

    val all: List<SessionCommand> = listOf(toggleFavorite, toggleShuffle, cycleRepeat)

    @Player.RepeatMode
    fun nextRepeatMode(@Player.RepeatMode current: Int): Int = when (current) {
        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
        else -> Player.REPEAT_MODE_OFF
    }

    fun buttons(
        context: Context,
        isFavorite: Boolean,
        shuffleEnabled: Boolean,
        @Player.RepeatMode repeatMode: Int,
    ): List<CommandButton> = listOf(
        CommandButton.Builder(
            if (isFavorite) {
                CommandButton.ICON_HEART_FILLED
            } else {
                CommandButton.ICON_HEART_UNFILLED
            }
        )
            .setDisplayName(
                context.getString(
                    if (isFavorite) {
                        R.string.player_remove_favorite
                    } else {
                        R.string.player_add_favorite
                    }
                )
            )
            .setSessionCommand(toggleFavorite)
            .build(),
        CommandButton.Builder(
            if (shuffleEnabled) {
                CommandButton.ICON_SHUFFLE_ON
            } else {
                CommandButton.ICON_SHUFFLE_OFF
            }
        )
            .setDisplayName(
                context.getString(
                    if (shuffleEnabled) {
                        R.string.player_shuffle_off
                    } else {
                        R.string.player_shuffle_on
                    }
                )
            )
            .setSessionCommand(toggleShuffle)
            .build(),
        CommandButton.Builder(
            when (repeatMode) {
                Player.REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE
                Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL
                else -> CommandButton.ICON_REPEAT_OFF
            }
        )
            .setDisplayName(
                context.getString(
                    when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> R.string.player_repeat_one
                        Player.REPEAT_MODE_ALL -> R.string.player_repeat_all
                        else -> R.string.player_repeat_off
                    }
                )
            )
            .setSessionCommand(cycleRepeat)
            .build(),
    )
}
