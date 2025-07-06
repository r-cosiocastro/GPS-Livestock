package com.rafaelcosio.gpslivestock.utils

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import com.airbnb.lottie.LottieAnimationView
import com.rafaelcosio.gpslivestock.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class CustomAlertDialog(private val context: Context) {
    private val dialogView = LayoutInflater.from(context).inflate(R.layout.alert_dialog_lottie, null)
    private val lottieAnimationView = dialogView.findViewById<LottieAnimationView>(R.id.lottie_animation)
    private val dialogBuilder: MaterialAlertDialogBuilder = MaterialAlertDialogBuilder(context)
    private var title: String = ""
    private var message: String = ""
    private var positiveButtonLabel: String = "Aceptar"
    private var positiveButtonListener: DialogInterface.OnClickListener? = null
    private var negativeButtonLabel: String = ""
    private var negativeButtonListener: DialogInterface.OnClickListener? = null
    private var neutralButtonLabel: String = ""
    private var neutralButtonListener: DialogInterface.OnClickListener? = null
    private var animationResId: Int = 0
    private var iconResId = 0
    private var loopAnimation: Boolean = true
    private var isCancelable: Boolean = true
    private var countDownSeconds: Int = 0

    fun setTitle(title: String): CustomAlertDialog {
        this.title = title
        return this
    }

    fun setCancelable(isCancelable: Boolean): CustomAlertDialog {
        this.isCancelable = isCancelable
        return this
    }

    fun setMessage(message: String): CustomAlertDialog {
        this.message = message
        return this
    }

    fun setIcon(iconResId: Int): CustomAlertDialog {
        this.iconResId = iconResId
        return this
    }

    fun setAnimation(animationResId: Int, loopAnimation: Boolean): CustomAlertDialog {
        this.animationResId = animationResId
        this.loopAnimation = loopAnimation
        return this
    }

    fun setPositiveButton(text: String, listener: DialogInterface.OnClickListener): CustomAlertDialog {
        positiveButtonLabel = text
        positiveButtonListener = listener
        return this
    }

    fun setNeutralButton(text: String, listener: DialogInterface.OnClickListener): CustomAlertDialog {
        neutralButtonLabel = text
        neutralButtonListener = listener
        return this
    }

    fun setNegativeButton(text: String, listener: DialogInterface.OnClickListener): CustomAlertDialog {
        negativeButtonLabel = text
        negativeButtonListener = listener
        return this
    }

    fun setCountDownSeconds(seconds: Int): CustomAlertDialog {
        countDownSeconds = seconds
        return this
    }

    fun show() {
        dialogBuilder.create()
        dialogBuilder.setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveButtonLabel, positiveButtonListener)
        if (negativeButtonLabel.isNotEmpty() && negativeButtonListener != null) {
            dialogBuilder.setNegativeButton(negativeButtonLabel, negativeButtonListener)
        }
        if (neutralButtonLabel.isNotEmpty() && neutralButtonListener != null) {
            dialogBuilder.setNeutralButton(neutralButtonLabel, neutralButtonListener)
        }
        dialogBuilder.setCancelable(isCancelable)

        if (iconResId != 0) {
            dialogBuilder.setIcon(iconResId)
        }

        dialogBuilder.setView(dialogView)
        val dialog: AlertDialog = dialogBuilder.create()
        lottieAnimationView.setAnimation(animationResId)
        lottieAnimationView.repeatCount = if (loopAnimation) -1 else 0


        dialog.show()
        if (countDownSeconds > 0) {

            val handler = Handler(Looper.getMainLooper())
            val title = title
            val runnable = object : Runnable {
                var seconds = countDownSeconds
                override fun run() {
                    dialog.getButton(Dialog.BUTTON_POSITIVE).isEnabled = false
                    dialog.getButton(Dialog.BUTTON_POSITIVE).text = "$positiveButtonLabel ($seconds)"
                    dialog.setTitle("$title ($seconds)")
                    if (seconds-- > 0) {
                        handler.postDelayed(this, 1000)
                    } else {
                        dialog.getButton(Dialog.BUTTON_POSITIVE).isEnabled = true
                        dialog.getButton(Dialog.BUTTON_POSITIVE).text = positiveButtonLabel
                        dialog.setTitle(title)
                    }
                }
            }
            handler.post(runnable)
        }
    }

    companion object {
        fun showErrorDialog(context: Context, errorMessage: String): CustomAlertDialog {
            return CustomAlertDialog(context)
                .setTitle("Ha ocurrido un error inesperado")
                .setMessage(errorMessage)
                .setAnimation(R.raw.error_animation, true)
                .setPositiveButton("Aceptar") { dialog: DialogInterface, _: Int -> dialog.dismiss() }
        }

        fun showWarningDialog(context: Context, errorMessage: String): CustomAlertDialog {
            return CustomAlertDialog(context)
                .setTitle("Atención")
                .setMessage(errorMessage)
                .setAnimation(R.raw.warning, false)
                .setPositiveButton("Aceptar") { dialog: DialogInterface, _: Int -> dialog.dismiss() }
        }
    }
}