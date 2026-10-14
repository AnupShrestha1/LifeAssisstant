package com.anujstha.lifeassistant.ui.expenses

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import com.anujstha.lifeassistant.R
import com.anujstha.lifeassistant.domain.model.PaymentMethod
import com.anujstha.lifeassistant.domain.model.TransactionType
import com.anujstha.lifeassistant.domain.usecase.BudgetLevel
import com.anujstha.lifeassistant.util.Money

@StringRes
fun TransactionType.labelRes(): Int = when (this) {
    TransactionType.EXPENSE -> R.string.type_expense
    TransactionType.INCOME -> R.string.type_income
}

@StringRes
fun PaymentMethod.labelRes(): Int = when (this) {
    PaymentMethod.CASH -> R.string.payment_cash
    PaymentMethod.CARD -> R.string.payment_card
    PaymentMethod.BANK_TRANSFER -> R.string.payment_bank_transfer
    PaymentMethod.MOBILE_WALLET -> R.string.payment_mobile_wallet
    PaymentMethod.OTHER -> R.string.payment_other
}

@Composable
fun BudgetLevel.color(): Color = when (this) {
    BudgetLevel.OK -> MaterialTheme.colorScheme.primary
    BudgetLevel.WARNING -> MaterialTheme.colorScheme.tertiary
    BudgetLevel.EXCEEDED -> MaterialTheme.colorScheme.error
}

/** Formats minor units for display in the current locale. */
@Composable
fun rememberMoneyFormatter(): (Long, String) -> String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { { minor, currency -> Money.format(minor, currency, locale) } }
}
