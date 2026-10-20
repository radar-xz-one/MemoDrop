package com.memodrop

import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var canvas: HandwritingCanvas
    private lateinit var tearSound: MediaPlayer
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // レイアウトを設定
        setContentView(R.layout.activity_main)

        // キャンバスを取得
        canvas = findViewById(R.id.handwriting_canvas)

        // 効果音をロード
        tearSound = MediaPlayer.create(this, R.raw.tear_sound)

        // 削除リスナーを設定
        canvas.onDeleteListener = {
            playTearSound()
        }

        // 初回起動判定
        val prefs = getSharedPreferences("MemoDrop", Context.MODE_PRIVATE)
        val isFirstLaunch = prefs.getBoolean("is_first_launch", true)

        if (isFirstLaunch) {
            // 初回起動演出
            canvas.drawTutorialDoodle()

            // 1秒後に自動でちぎる
            handler.postDelayed({
                canvas.performTutorialTear()
            }, 1000)

            // 初回起動フラグをクリア
            prefs.edit().putBoolean("is_first_launch", false).apply()
        } else {
            // ローカルストレージから復元
            canvas.loadFromStorage()
        }
    }

    private fun playTearSound() {
        try {
            if (tearSound.isPlaying) {
                tearSound.stop()
                tearSound.prepare()
            }
            tearSound.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tearSound.release()
        handler.removeCallbacksAndMessages(null)
    }
}
