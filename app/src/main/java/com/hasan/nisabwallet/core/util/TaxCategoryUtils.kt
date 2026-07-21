package com.hasan.nisabwallet.core.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

data class TaxCategory(
    val id: String,
    val name: String,
    val nbrCode: String,
    val description: String,
    val suggestedKeywords: List<String> = emptyList(),
    val depreciable: Boolean = false,
    val depreciationRate: Int = 0
)

object TaxCategoryUtils {

    val PERSONAL_EXPENSES = listOf(
        TaxCategory("food_groceries", "Food & Groceries", "A.1", "Household food items", listOf("food", "grocery", "mart", "restaurant", "meal")),
        TaxCategory("clothing", "Clothing & Footwear", "A.2", "Clothes, shoes", listOf("cloth", "fashion", "dress", "shirt", "shoe")),
        TaxCategory("house_rent", "House Rent", "A.3", "Residential rent paid", listOf("rent", "flat rent", "apartment")),
        TaxCategory("utilities", "Utilities", "A.4", "Electricity, gas, internet", listOf("electric", "gas", "water", "internet", "wifi", "phone", "bill", "utility")),
        TaxCategory("transport", "Transport & Fuel", "A.5", "Public transport, fuel", listOf("transport", "fuel", "petrol", "uber", "pathao", "bus")),
        TaxCategory("medical", "Medical & Healthcare", "A.6", "Doctor, medicine", listOf("health", "medical", "doctor", "medicine", "hospital", "clinic")),
        TaxCategory("education", "Education", "A.7", "School/university fees", listOf("education", "school", "university", "tuition", "course")),
        TaxCategory("entertainment", "Entertainment & Recreation", "A.8", "Movies, sports, vacation", listOf("entertainment", "movie", "sport", "vacation", "netflix", "subscription")),
        TaxCategory("household", "Household Items", "A.9", "Furniture, appliances", listOf("furniture", "appliance", "household", "repair")),
        TaxCategory("personal_care", "Personal Care", "A.10", "Salon, cosmetics", listOf("salon", "barber", "cosmetic", "beauty")),
        TaxCategory("donation", "Donations & Charity", "A.11", "Charitable donations, zakat", listOf("donation", "charity", "zakat", "sadaqah", "donate")),
        TaxCategory("insurance", "Insurance Premium", "A.12", "Life, health insurance", listOf("insurance", "premium", "policy")),
        TaxCategory("miscellaneous", "Miscellaneous Personal Expenses", "A.13", "Other personal expenses", listOf("misc", "miscellaneous", "other"))
    )

    val TAX_PAID = listOf(
        TaxCategory("income_tax", "Income Tax Paid", "B.1", "Tax deducted at source (TDS)", listOf("tax", "income tax", "tds", "advance tax")),
        TaxCategory("vat_tax", "VAT/Other Tax Paid", "B.2", "VAT, customs duty", listOf("vat", "value added tax"))
    )

    val INVESTMENTS = listOf(
        TaxCategory("life_insurance", "Life Insurance Premium", "C.1", "Eligible for tax rebate", listOf("life insurance", "lic")),
        TaxCategory("dps", "DPS/Fixed Deposit", "C.2", "Bank DPS, FDR", listOf("dps", "fixed deposit", "fdr", "term deposit")),
        TaxCategory("savings_cert", "Savings Certificates", "C.3", "Sanchayapatra", listOf("sanchaya", "sanchayapatra", "bond")),
        TaxCategory("stock_market", "Stock Market Investment", "C.4", "Shares, mutual funds", listOf("stock", "share", "mutual fund")),
        TaxCategory("provident_fund", "Provident Fund", "C.5", "Contribution to PF", listOf("provident fund", "pf", "gpf")),
        TaxCategory("other_investment", "Other Approved Investments", "C.6", "Other govt approved", listOf("investment", "saving"))
    )

    val LOAN_REPAYMENT = listOf(
        TaxCategory("loan_principal", "Loan Principal Repayment", "D.1", "Principal amount", listOf("loan", "emi", "installment", "repayment")),
        TaxCategory("loan_interest", "Loan Interest Payment", "D.2", "Interest portion", listOf("interest", "loan interest"))
    )

    val INCOME_TAX_CATEGORIES = listOf(
        TaxCategory("salary", "Salary/Wages", "INC-1", "Employment income", listOf("salary", "wage", "pay")),
        TaxCategory("bonus", "Bonus/Allowance", "INC-2", "Festival bonus", listOf("bonus", "allowance")),
        TaxCategory("business", "Business/Professional Income", "INC-3", "Self-employment", listOf("business", "sales", "freelance")),
        TaxCategory("investment_income", "Investment Income", "INC-4", "Dividends, interest", listOf("dividend", "interest", "profit")),
        TaxCategory("rental_income", "Rental Income", "INC-5", "Income from property", listOf("rent income", "rental income")),
        TaxCategory("other_income", "Other Income", "INC-6", "Any other taxable income", listOf("other income"))
    )

    val ALL_EXPENSE_TAX_CATEGORIES = PERSONAL_EXPENSES + TAX_PAID + INVESTMENTS + LOAN_REPAYMENT

    fun getCurrentIncomeYear(): String {
        val cal = Calendar.getInstance()
        val currentMonth = cal.get(Calendar.MONTH) // 0-11
        val currentYear = cal.get(Calendar.YEAR)
        
        return if (currentMonth >= Calendar.JULY) {
            "$currentYear-${(currentYear + 1).toString().takeLast(2)}"
        } else {
            "${currentYear - 1}-${currentYear.toString().takeLast(2)}"
        }
    }

    fun getFiscalYearDates(incomeYear: String): Pair<String, String> {
        val startYear = incomeYear.split("-")[0]
        val endYear = startYear.toInt() + 1
        return Pair("$startYear-07-01", "$endYear-06-30")
    }

    fun getTaxYear(incomeYear: String): String {
        val startYear = incomeYear.split("-")[0]
        return (startYear.toInt() + 1).toString()
    }

    fun getFilingDeadline(taxYear: String): String {
        return "$taxYear-11-30"
    }

    fun getDaysUntilDeadline(deadline: String): Int {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val deadlineDate = sdf.parse(deadline) ?: return 0
            val today = System.currentTimeMillis()
            ceil((deadlineDate.time - today) / (1000.0 * 60 * 60 * 24)).toInt()
        } catch (e: Exception) { 0 }
    }
}