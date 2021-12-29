package com.senex.androidlab1.player

import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.IBinder
import com.senex.androidlab1.interfaces.IPlayerControlService
import com.senex.androidlab1.models.Track
import com.senex.androidlab1.player.PlayerNotificationHandler.Companion.getNotificationActionExtra
import com.senex.androidlab1.repository.TrackRepository
import com.senex.androidlab1.utils.log
import com.senex.androidlab1.player.PlayerNotificationHandler.Action as PlayerNotificationAction

class PlayerControlService : Service(), PlayerControlServiceBinder {
    private val playerNotificationHandler: PlayerNotificationHandler by lazy {
        PlayerNotificationHandler(applicationContext)
    }

    private var currentState = PlayerState.NOT_STARTED
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var currentTrack: Track

    override fun getCurrentTrack() = currentTrack

    private val playerControlServiceBinder: IPlayerControlService.Stub =
        object : IPlayerControlService.Stub(), PlayerControlServiceBinder by this { }

    override fun onBind(intent: Intent): IBinder {
        if (currentState == PlayerState.NOT_STARTED) {
            setTrack(TrackRepository.getRandomTrack())
        }
        return playerControlServiceBinder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        intent.getNotificationActionExtra()?.let {
            log("Received command from notification: $it")

            when (it) {
                PlayerNotificationAction.Play -> resume()
                PlayerNotificationAction.Pause -> pause()
                PlayerNotificationAction.Next -> next()
                PlayerNotificationAction.Previous -> previous()
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun setTrack(track: Track) {
        currentTrack = track
        mediaPlayer = MediaPlayer.create(
            this,
            currentTrack.trackRes
        )

        updatePlayerState(PlayerState.PAUSED)
    }

    override fun resumeOrPauseIfCurrentOrPlayNew(trackId: Long) {
        if (isTrackCurrent(trackId)) {
            if (isNotPlaying) {
                resume()
            } else {
                pause()
            }
        } else {
            play(trackId)
        }
    }

    override fun resumeOrPause() {
        if (isNotPlaying) {
            resume()
        } else {
            pause()
        }
    }

    private fun play(track: Track) {
        stop()

        currentTrack = track
        mediaPlayer = MediaPlayer.create(
            this,
            currentTrack.trackRes
        )
        mediaPlayer.start()

        updatePlayerState(PlayerState.PLAYING)
    }

    override fun play(trackId: Long) =
        play(TrackRepository.get(trackId)!!)

    private fun resume() {
        mediaPlayer.start()

        updatePlayerState(PlayerState.PLAYING)
    }

    private fun pause() {
        mediaPlayer.pause()

        updatePlayerState(PlayerState.PAUSED)
    }

    override fun stop() {
        mediaPlayer.apply {
            stop()
            release()
        }

        updatePlayerState(PlayerState.STOPPED)
    }

    override fun previous() = handleTrackUpdate(
        TrackRepository.getPrevFor(currentTrack.id)
    )

    override fun next() = handleTrackUpdate(
        TrackRepository.getNextFor(currentTrack.id)
    )

    private fun handleTrackUpdate(newTrack: Track) {
        if (isPlaying) {
            play(newTrack)
        } else {
            setTrack(newTrack)
        }
    }

    override fun getTrackElapsedDurationMillis() =
        mediaPlayer.currentPosition

    private val isPlaying
        get() = currentState == PlayerState.PLAYING
                && mediaPlayer.isPlaying

    private val isNotPlaying
        get() = !isPlaying

    private fun isTrackCurrent(track: Track) =
        track == currentTrack

    private fun isTrackCurrent(trackId: Long) =
        isTrackCurrent(TrackRepository.get(trackId)!!)

    private fun updatePlayerState(newPlayerState: PlayerState) {
        log("Player state updated to: $newPlayerState")

        currentState = newPlayerState
        notifySubscribers()

        playerNotificationHandler.setPlayerNotification(
            currentTrack,
            currentState
        )
    }

    private val stateSubscribersList = mutableListOf<OnStateChangeListener>()

    override fun subscribeForStateChange(
        listener: OnStateChangeListener,
    ) {
        stateSubscribersList.add(listener)
        listener.onStateChange(currentState)
    }

    override fun unsubscribeFromStateChange(
        listener: OnStateChangeListener,
    ) {
        stateSubscribersList.removeIf { callback ->
            callback == listener
        }
    }

    private fun notifySubscribers() {
        for (callback in stateSubscribersList) {
            callback.onStateChange(currentState)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        playerNotificationHandler.cancelPlayerNotification()
        stop()
    }
}

interface PlayerControlServiceBinder {
    fun getCurrentTrack(): Track

    fun resumeOrPauseIfCurrentOrPlayNew(trackId: Long)

    fun resumeOrPause()

    fun play(trackId: Long)

    fun previous()

    fun next()

    fun stop()

    fun getTrackElapsedDurationMillis(): Int

    fun subscribeForStateChange(
        listener: OnStateChangeListener
    )

    fun unsubscribeFromStateChange(
        listener: OnStateChangeListener
    )
}

