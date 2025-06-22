package com.dasc.pecustrack.utils

import android.app.Activity
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.airbnb.lottie.LottieAnimationView
import com.dasc.pecustrack.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class LoadingDialog(private var activity: Activity) {
    private var alertDialog: AlertDialog? = null
    fun showLoadingDialog(message: String?, animationId: Int) {
        val builder = MaterialAlertDialogBuilder(activity)
        val inflater = activity.layoutInflater
        val view = inflater.inflate(R.layout.loading_dialog, null)
        val textView = view.findViewById<TextView>(R.id.loading_text)
        textView.text = message
        val lottieAnimationView = view.findViewById<LottieAnimationView>(R.id.lottie_animation)
        lottieAnimationView.setAnimation(animationId)
        builder.setView(view)
        builder.setCancelable(false)
        alertDialog = builder.create()
        alertDialog!!.show()
    }

    fun dismiss() {
        if (alertDialog?.isShowing == true)
            alertDialog?.dismiss()
    }
}