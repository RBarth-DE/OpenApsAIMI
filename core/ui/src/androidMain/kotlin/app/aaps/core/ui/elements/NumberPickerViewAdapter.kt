package app.aaps.core.ui.elements

import android.view.View
import android.widget.Button
import android.widget.EditText
import com.google.android.material.textfield.TextInputLayout
import app.aaps.core.ui.R

/**
 * NumberPickerViewAdapter binds the shared attributes of the horizontal and vertical number-picker
 * layouts to one common view adapter. Both layouts use the same view ids (display, decrement,
 * increment, textInputLayout), so plain findViewById is enough — the KMP build has no viewBinding.
 */
class NumberPickerViewAdapter(root: View) {

    val editText: EditText = root.findViewById(R.id.display)
    val minusButton: Button = root.findViewById(R.id.decrement)
    val plusButton: Button = root.findViewById(R.id.increment)
    var textInputLayout: TextInputLayout = root.findViewById(R.id.textInputLayout)
}
