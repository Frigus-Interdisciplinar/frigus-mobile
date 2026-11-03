package com.example.frigus_mobile.ui.forgotpassword

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.api.BffException
import com.example.frigus_mobile.data.repository.PasswordRecoveryRepository
import com.example.frigus_mobile.utils.ValidationUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

enum class RecoveryStep { EMAIL, CODE, PASSWORD, COMPLETE, CODE_UNCERTAIN, RESET_UNCERTAIN }
data class PasswordRecoveryState(
    val step: RecoveryStep = RecoveryStep.EMAIL,
    val busy: Boolean = false,
    val email: String = "",
    val message: String? = null,
    val requestUncertain: Boolean = false
)

class ForgotPasswordViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PasswordRecoveryRepository()
    private val mutableState = MutableLiveData(PasswordRecoveryState())
    val state: LiveData<PasswordRecoveryState> = mutableState
    private var verifiedCode: String? = null

    fun submit(emailInput: String, codeInput: String, password: String, confirmation: String) {
        val current = mutableState.value ?: return
        if (current.busy) return
        when (current.step) {
            RecoveryStep.EMAIL -> {
                val email = emailInput.trim()
                if (!ValidationUtils.isEmailValido(email)) return message(R.string.auth_invalid_email)
                execute(current.copy(email = email), RecoveryStep.CODE) { repository.request(email) }
            }
            RecoveryStep.CODE -> {
                val code = codeInput.trim()
                if (!Regex("[0-9]{6}").matches(code)) return message(R.string.recovery_invalid_code)
                execute(current, RecoveryStep.PASSWORD, onSuccess = { verifiedCode = code }) {
                    repository.verify(current.email, code)
                }
            }
            RecoveryStep.PASSWORD -> {
                if (!ValidationUtils.isSenhaValida(password)) return message(R.string.recovery_password_requirements)
                if (password != confirmation) return message(R.string.recovery_password_mismatch)
                val code = verifiedCode ?: return
                execute(current, RecoveryStep.COMPLETE, onSuccess = { verifiedCode = null }) {
                    repository.reset(current.email, code, password)
                }
            }
            else -> Unit
        }
    }

    private fun execute(previous: PasswordRecoveryState, next: RecoveryStep,
                        onSuccess: () -> Unit = {}, operation: suspend () -> Unit) {
        mutableState.value = previous.copy(busy = true, message = null)
        viewModelScope.launch {
            try {
                operation()
                onSuccess()
                mutableState.value = previous.copy(step = next, busy = false, requestUncertain = false,
                    message = if (next == RecoveryStep.CODE) text(R.string.recovery_request_accepted) else null)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                val error = (failure as? BffException)?.error
                val uncertain = error?.status == null || error.status == 408 || error.status >= 500
                mutableState.value = when {
                    uncertain && previous.step == RecoveryStep.EMAIL -> previous.copy(step = RecoveryStep.CODE,
                        busy = false, requestUncertain = true, message = text(R.string.recovery_request_uncertain))
                    uncertain && previous.step == RecoveryStep.CODE -> previous.copy(step = RecoveryStep.CODE_UNCERTAIN,
                        busy = false, message = text(R.string.recovery_code_uncertain))
                    uncertain && previous.step == RecoveryStep.PASSWORD -> {
                        verifiedCode = null
                        previous.copy(step = RecoveryStep.RESET_UNCERTAIN, busy = false,
                            message = text(R.string.recovery_reset_uncertain))
                    }
                    else -> previous.copy(busy = false, message = error?.userMessage(text(R.string.recovery_operation_failed))
                        ?: text(R.string.recovery_operation_failed))
                }
            }
        }
    }

    private fun message(id: Int) { mutableState.value = mutableState.value?.copy(message = text(id)) }
    private fun text(id: Int) = getApplication<Application>().getString(id)
}
