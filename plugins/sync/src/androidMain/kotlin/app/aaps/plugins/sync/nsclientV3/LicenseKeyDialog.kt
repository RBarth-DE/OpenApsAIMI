package app.aaps.plugins.sync.nsclientV3

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.plugins.sync.R
import androidx.fragment.app.DialogFragment
import com.google.android.material.textfield.TextInputEditText
import dev.zacsweers.metro.Inject

/**
 * Dialog for entering Remote Control premium license key.
 */
class LicenseKeyDialog : DialogFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var licenseValidator: LicenseKeyValidator

    private var views: DialogLicenseKeyViews? = null

    var onLicenseActivated: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val views = DialogLicenseKeyViews.from(inflater.inflate(R.layout.dialog_license_key, container, false))
        this.views = views
        isCancelable = false
        return views.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = views ?: return

        views.btnActivate.setOnClickListener {
            val licenseKey = views.licenseKeyInput.text.toString()

            if (licenseKey.isEmpty()) {
                views.errorText.visibility = View.VISIBLE
                views.errorText.text = rh.gs(R.string.license_key_required)
                return@setOnClickListener
            }

            if (licenseValidator.validateLicenseKey(licenseKey)) {
                // Success
                aapsLogger.info(LTag.NSCLIENT, "[License] License activated successfully")
                dismiss()
                onLicenseActivated?.invoke()
            } else {
                // Failed
                views.errorText.visibility = View.VISIBLE
                views.errorText.text = rh.gs(R.string.license_key_invalid)
                views.licenseKeyInput.text?.clear()
            }
        }

        views.btnCancel.setOnClickListener {
            aapsLogger.info(LTag.NSCLIENT, "[License] License activation cancelled")
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        views = null
    }
}

/**
 * The views of `dialog_license_key.xml`, found by hand.
 *
 * A Kotlin Multiplatform module never gets generated view binding classes, so there is no
 * `DialogLicenseKeyBinding` to use. The names are the ones the generated class used.
 */
class DialogLicenseKeyViews(
    val root: View,
    val btnActivate: Button,
    val btnCancel: Button,
    val licenseKeyInput: TextInputEditText,
    val errorText: TextView,
) {
    companion object {
        fun from(root: View): DialogLicenseKeyViews = DialogLicenseKeyViews(
            root = root,
            btnActivate = root.findViewById(R.id.btn_activate),
            btnCancel = root.findViewById(R.id.btn_cancel),
            licenseKeyInput = root.findViewById(R.id.license_key_input),
            errorText = root.findViewById(R.id.error_text),
        )
    }
}
