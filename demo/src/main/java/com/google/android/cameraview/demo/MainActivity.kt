/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.cameraview.demo

import android.Manifest
import android.app.Dialog
import android.content.pm.PackageManager
import android.os.*
import android.support.annotation.StringRes
import android.support.v4.app.ActivityCompat
import android.support.v4.app.DialogFragment
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import com.google.android.cameraview.AspectRatio
import com.google.android.cameraview.CameraView
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream


/**
 * This demo app saves the taken picture to a constant file.
 * $ adb pull /sdcard/Android/data/com.google.android.cameraview.demo/files/Pictures/picture.jpg
 */
class MainActivity : AppCompatActivity(), ActivityCompat.OnRequestPermissionsResultCallback, AspectRatioFragment.Listener {

    private var mCurrentFlash: Int = 0

    private var mBackgroundHandler: Handler? = null

    private val mOnClickListener = View.OnClickListener { v ->
        when (v.id) {
            R.id.fab_take_picture -> if (camera_view != null) {
                camera_view!!.takePicture()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (camera_view != null) {
            camera_view!!.addCallback(mCallback)
        }

        fab_take_picture?.setOnClickListener(mOnClickListener)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            camera_view!!.start()
        } else if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {
            ConfirmationDialogFragment
                    .newInstance(R.string.camera_permission_confirmation,
                            arrayOf(Manifest.permission.CAMERA),
                            REQUEST_CAMERA_PERMISSION,
                            R.string.camera_permission_not_granted)
                    .show(supportFragmentManager, FRAGMENT_DIALOG)
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA),
                    REQUEST_CAMERA_PERMISSION)
        }
    }

    override fun onPause() {
        camera_view!!.stop()
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mBackgroundHandler != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                mBackgroundHandler!!.looper.quitSafely()
            } else {
                mBackgroundHandler!!.looper.quit()
            }
            mBackgroundHandler = null
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        when (requestCode) {
            REQUEST_CAMERA_PERMISSION -> {
                if (permissions.size != 1 || grantResults.size != 1) {
                    throw RuntimeException("Error on requesting camera permission.")
                }
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, R.string.camera_permission_not_granted,
                            Toast.LENGTH_SHORT).show()
                }
            }
        }// No need to start camera here; it is handled by onResume
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.aspect_ratio -> {
                val fragmentManager = supportFragmentManager
                if (camera_view != null && fragmentManager.findFragmentByTag(FRAGMENT_DIALOG) == null) {
                    val ratios = camera_view!!.supportedAspectRatios
                    val currentRatio = camera_view!!.aspectRatio
                    AspectRatioFragment.newInstance(ratios, currentRatio)
                            .show(fragmentManager, FRAGMENT_DIALOG)
                }
                return true
            }
            R.id.switch_flash -> {
                if (camera_view != null) {
                    mCurrentFlash = (mCurrentFlash + 1) % FLASH_OPTIONS.size
                    item.setTitle(FLASH_TITLES[mCurrentFlash])
                    item.setIcon(FLASH_ICONS[mCurrentFlash])
                    camera_view!!.flash = FLASH_OPTIONS[mCurrentFlash]
                }
                return true
            }
            R.id.switch_camera -> {
                if (camera_view != null) {
                    val facing = camera_view!!.facing
                    camera_view!!.facing = if (facing == CameraView.FACING_FRONT)
                        CameraView.FACING_BACK
                    else
                        CameraView.FACING_FRONT
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onAspectRatioSelected(ratio: AspectRatio) {
        if (camera_view != null) {
            Toast.makeText(this, ratio.toString(), Toast.LENGTH_SHORT).show()
            camera_view!!.setAspectRatio(ratio)
        }
    }

    private val backgroundHandler: Handler?
        get() {
            if (mBackgroundHandler == null) {
                val thread = HandlerThread("background")
                thread.start()
                mBackgroundHandler = Handler(thread.looper)
            }
            return mBackgroundHandler
        }

    private val mCallback = object : CameraView.Callback() {

        override fun onCameraOpened(cameraView: CameraView) {
            Log.d(TAG, "onCameraOpened")
        }

        override fun onCameraClosed(cameraView: CameraView) {
            Log.d(TAG, "onCameraClosed")
        }

        override fun onPictureTaken(cameraView: CameraView, data: ByteArray) {
            Log.d(TAG, "onPictureTaken " + data.size)
            Toast.makeText(cameraView.context, R.string.picture_taken, Toast.LENGTH_SHORT)
                    .show()
            backgroundHandler?.post {
                val file = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                        "picture.jpg")
                var os: OutputStream? = null
                try {
                    os = FileOutputStream(file)
                    os.write(data)
                    os.close()
                } catch (e: IOException) {
                    Log.w(TAG, "Cannot write to " + file, e)
                } finally {
                    if (os != null) {
                        try {
                            os.close()
                        } catch (e: IOException) {
                            // Ignore
                        }

                    }
                }
            }
        }

    }

    class ConfirmationDialogFragment : DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val args = arguments
            return AlertDialog.Builder(activity)
                    .setMessage(args.getInt(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok
                    ) { dialog, which ->
                        val permissions = args.getStringArray(ARG_PERMISSIONS) ?: throw IllegalArgumentException()
                        ActivityCompat.requestPermissions(activity,
                                permissions, args.getInt(ARG_REQUEST_CODE))
                    }
                    .setNegativeButton(android.R.string.cancel
                    ) { dialog, which ->
                        Toast.makeText(activity,
                                args.getInt(ARG_NOT_GRANTED_MESSAGE),
                                Toast.LENGTH_SHORT).show()
                    }
                    .create()
        }

        companion object {

            private val ARG_MESSAGE = "message"
            private val ARG_PERMISSIONS = "permissions"
            private val ARG_REQUEST_CODE = "request_code"
            private val ARG_NOT_GRANTED_MESSAGE = "not_granted_message"

            fun newInstance(@StringRes message: Int,
                            permissions: Array<String>, requestCode: Int, @StringRes notGrantedMessage: Int): ConfirmationDialogFragment {
                val fragment = ConfirmationDialogFragment()
                val args = Bundle()
                args.putInt(ARG_MESSAGE, message)
                args.putStringArray(ARG_PERMISSIONS, permissions)
                args.putInt(ARG_REQUEST_CODE, requestCode)
                args.putInt(ARG_NOT_GRANTED_MESSAGE, notGrantedMessage)
                fragment.arguments = args
                return fragment
            }
        }

    }

    companion object {

        private val TAG = "MainActivity"

        private val REQUEST_CAMERA_PERMISSION = 1

        private val FRAGMENT_DIALOG = "dialog"

        private val FLASH_OPTIONS = intArrayOf(CameraView.FLASH_AUTO, CameraView.FLASH_OFF, CameraView.FLASH_ON)

        private val FLASH_ICONS = intArrayOf(R.drawable.ic_flash_auto, R.drawable.ic_flash_off, R.drawable.ic_flash_on)

        private val FLASH_TITLES = intArrayOf(R.string.flash_auto, R.string.flash_off, R.string.flash_on)
    }

}
