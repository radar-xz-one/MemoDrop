package com.memodrop

import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var canvas: HandwritingCanvas
    private var tearSound: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // レイアウトを設定
        setContentView(R.layout.activity_main)

        // キャンバスを取得
        canvas = findViewById(R.id.handwriting_canvas)

        // 効果音をロード（現在は無効化）
        tearSound = null

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
            tearSound?.let { sound ->
                if (sound.isPlaying) {
                    sound.stop()
                    sound.prepare()
                }
                sound.start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tearSound?.release()
        handler.removeCallbacksAndMessages(null)
    }
}
