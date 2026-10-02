package com.example.voxelcraft

import android.app.Activity
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout

class MainActivity : Activity() {

    private lateinit var glView: GLSurfaceView
    private lateinit var renderer: GameRenderer
    private val shared = Shared()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        renderer = GameRenderer(shared)
        glView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 0, 16, 0)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }

        val root = FrameLayout(this)
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        root.addView(glView, FrameLayout.LayoutParams(match, match))
        root.addView(HudView(this, shared), FrameLayout.LayoutParams(match, match))
        setContentView(root)
        hideSystemUi()
    }

    override fun onResume() {
        super.onResume()
        glView.onResume()
        hideSystemUi()
    }

    override fun onPause() {
        glView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        renderer.world.shutdown()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }
}
