package ir.lizoo.courier.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.lizoo.courier.data.ApiResult
import ir.lizoo.courier.data.Graph
import ir.lizoo.courier.data.SettlementDto
import ir.lizoo.courier.data.SummaryDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EarningsUi(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val summary: SummaryDto? = null,
    val settlements: List<SettlementDto> = emptyList(),
    val error: String? = null,
    val info: String? = null
)

fun settlementError(code: String): String = when (code) {
    "below_minimum" -> "مبلغ قابل برداشت کمتر از حداقل تسویه است"
    "insufficient_balance" -> "موجودی قابل برداشت کافی نیست"
    "open_request_exists" -> "یک درخواست تسویه در جریان داری"
    "invalid_amount" -> "مبلغ نامعتبر است"
    else -> homeError(code)
}

class EarningsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Graph.repo(app.applicationContext)
    private val _ui = MutableStateFlow(EarningsUi())
    val ui: StateFlow<EarningsUi> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val s = repo.summary()
        val l = repo.mySettlements()
        _ui.update {
            var next = it.copy(loading = false)
            when (s) {
                is ApiResult.Ok -> next = next.copy(summary = s.data, error = null)
                is ApiResult.Fail -> next = next.copy(error = settlementError(s.error))
            }
            if (l is ApiResult.Ok) next = next.copy(settlements = l.data.settlements)
            next
        }
    }

    fun request() {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, error = null, info = null) }
        viewModelScope.launch {
            when (val r = repo.requestSettlement()) {
                is ApiResult.Ok -> {
                    _ui.update { it.copy(info = "درخواست تسویه ثبت شد") }
                    load()
                }
                is ApiResult.Fail -> {
                    _ui.update { it.copy(error = settlementError(r.error)) }
                    load()
                }
            }
            _ui.update { it.copy(busy = false) }
        }
    }
}
