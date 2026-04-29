package app.aaps.plugins.automation

import app.aaps.core.interfaces.automation.AutomationStateInterface
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationStateServiceImpl @Inject constructor() : AutomationStateInterface {

    private val currentValues = mutableMapOf<String, String>()
    private val validValues = mutableMapOf<String, List<String>>()

    override fun inState(stateName: String, state: String): Boolean =
        currentValues[stateName] == state

    override fun setState(stateName: String, state: String) {
        val allowed = validValues[stateName]
        if (allowed != null && state !in allowed)
            throw IllegalStateException("Value '$state' is not valid for state '$stateName'. Valid: $allowed")
        currentValues[stateName] = state
    }

    override fun getState(stateName: String): String =
        currentValues[stateName] ?: ""

    override fun getAllStates(): List<Pair<String, String>> =
        validValues.keys.map { name -> name to (currentValues[name] ?: "") }

    override fun getStateValues(stateName: String): List<String> =
        validValues[stateName] ?: throw IllegalStateException("State '$stateName' does not exist")

    override fun setStateValues(stateName: String, values: List<String>) {
        validValues[stateName] = values
        if (currentValues[stateName] !in values)
            currentValues[stateName] = values.firstOrNull() ?: ""
    }

    override fun hasStateValues(stateName: String): Boolean =
        validValues.containsKey(stateName)

    override fun deleteState(stateName: String) {
        validValues.remove(stateName)
        currentValues.remove(stateName)
    }
}
