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
 * Login dialog for Remote Control access.
 */
class RemoteAccessLoginDialog : DialogFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var authManager: RemoteAccessAuthManager

    private var views: DialogRemoteAccessLoginViews? = null

    var onUnlocked: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val views = DialogRemoteAccessLoginViews.from(inflater.inflate(R.layout.dialog_remote_access_login, container, false))
        this.views = views
        isCancelable = false
        return views.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = views ?: return

        views.btnUnlock.setOnClickListener {
            val password = views.passwordInput.text.toString()

            if (password.isEmpty()) {
                views.errorText.visibility = View.VISIBLE
                views.errorText.text = rh.gs(R.string.password_required)
                return@setOnClickListener
            }

            if (authManager.verifyPassword(password)) {
                // Success
                aapsLogger.info(LTag.NSCLIENT, "[RemoteAccess] Access granted")
                dismiss()
                onUnlocked?.invoke()
            } else {
                // Failed
                views.errorText.visibility = View.VISIBLE
                views.errorText.text = rh.gs(R.string.remote_access_invalid_password)
                views.passwordInput.text?.clear()
            }
        }

        views.btnCancel.setOnClickListener {
            aapsLogger.info(LTag.NSCLIENT, "[RemoteAccess] Access cancelled")
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        views = null
    }
}

/**
 * The views of `dialog_remote_access_login.xml`, found by hand.
 *
 * A Kotlin Multiplatform module never gets generated view binding classes, so there is no
 * `DialogRemoteAccessLoginBinding` to use. The names are the ones the generated class used.
 */
class DialogRemoteAccessLoginViews(
    val root: View,
    val btnUnlock: Button,
    val btnCancel: Button,
    val passwordInput: TextInputEditText,
    val errorText: TextView,
) {
    companion object {
        fun from(root: View): DialogRemoteAccessLoginViews = DialogRemoteAccessLoginViews(
            root = root,
            btnUnlock = root.findViewById(R.id.btn_unlock),
            btnCancel = root.findViewById(R.id.btn_cancel),
            passwordInput = root.findViewById(R.id.password_input),
            errorText = root.findViewById(R.id.error_text),
        )
    }
}
