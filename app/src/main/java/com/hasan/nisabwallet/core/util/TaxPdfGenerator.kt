package com.hasan.nisabwallet.core.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.hasan.nisabwallet.ui.screens.tax.TaxYearRecord
import com.hasan.nisabwallet.ui.screens.tax.detail.TaxAsset
import com.hasan.nisabwallet.ui.screens.tax.detail.TaxLiability
import com.hasan.nisabwallet.ui.screens.tax.detail.TaxProfile
import com.hasan.nisabwallet.ui.screens.tax.detail.TransactionAnalysisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Generates a printable approximation of the NBR "Statement of Assets, Liabilities
 * and Expenses" (IT-10BB) used with the Bangladesh income tax return.
 */
object TaxPdfGenerator {

    // ── Layout constants ──────────────────────────────────────────────
    private const val PAGE_WIDTH = 595   // A4 @ 72dpi
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_LEFT = 40f
    private const val MARGIN_RIGHT = 40f
    private const val BOTTOM_LIMIT = 792f // leave room for footer
    private const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT

    // Table column widths for the 4-column ledger tables (SL | Particulars | This Year | Last Year)
    private val COL_SL = 26f
    private val COL_PARTICULARS = 289f
    private val COL_AMOUNT_CURRENT = 110f
    private val COL_AMOUNT_PREVIOUS = CONTENT_WIDTH - COL_SL - COL_PARTICULARS - COL_AMOUNT_CURRENT

    suspend fun generate(
        context: Context,
        format: String,
        taxYear: TaxYearRecord,
        profile: TaxProfile,
        assets: List<TaxAsset>,
        liabilities: List<TaxLiability>,
        analysis: TransactionAnalysisResult
    ) = withContext(Dispatchers.IO) {

        val isCompact = format.contains("2024")
        val document = PdfDocument()
        val renderer = FormRenderer(document, format)

        renderer.drawFormHeader(profile, taxYear)

        val totalAssets = renderer.drawAssetsTable(assets, taxYear)
        val totalLiabilities = renderer.drawLiabilitiesTable(liabilities)
        val netWealth = totalAssets - totalLiabilities
        renderer.drawNetWealthSummary(totalAssets, totalLiabilities, netWealth)

        renderer.drawExpenseStatement(analysis, isCompact)
        renderer.drawReconciliation(taxYear, analysis, netWealth)
        renderer.drawVerification(profile)

        renderer.finish()
        savePdf(context, document, format, profile, taxYear)
    }

    private fun savePdf(
        context: Context,
        document: PdfDocument,
        format: String,
        profile: TaxProfile,
        taxYear: TaxYearRecord
    ) {
        val fileName = "${format}_${profile.tin.ifBlank { "User" }}_${taxYear.taxYear.replace("-", "_")}.pdf"

        // Handle saving based on Android API Version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ (API 29 and above): Use MediaStore
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/NisabWallet")
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    document.writeTo(outputStream)
                }
            }
        } else {
            // Android 9 and below (API 28 and below): Use traditional File IO
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val nisabDir = File(downloadsDir, "NisabWallet")

            if (!nisabDir.exists()) {
                nisabDir.mkdirs()
            }

            val file = File(nisabDir, fileName)
            FileOutputStream(file).use { outputStream ->
                document.writeTo(outputStream)
            }
        }

        document.close()
    }

    // ── Category bucketing helpers ────────────────────────────────────
    private data class LedgerLine(val label: String, val amount: Double)

    private fun bucketAssets(assets: List<TaxAsset>): List<LedgerLine> {
        val buckets = linkedMapOf(
            "business" to ("Business Capital" to 0.0),
            "property" to ("Non-Agricultural Property (Land/House, at cost)" to 0.0),
            "agri" to ("Agricultural Property (at cost)" to 0.0),
            "investment" to ("Investments (Shares, Bonds, Savings Certificate, DPS etc.)" to 0.0),
            "vehicle" to ("Motor Vehicle (at cost)" to 0.0),
            "jewellery" to ("Jewellery" to 0.0),
            "furniture" to ("Furniture" to 0.0),
            "electronics" to ("Electronic Equipment" to 0.0),
            "cash" to ("Cash in Hand & at Bank" to 0.0),
            "other" to ("Other Assets" to 0.0)
        )

        assets.forEach { asset ->
            val key = classifyAsset(asset.assetType)
            val (label, current) = buckets.getValue(key)
            buckets[key] = label to (current + asset.currentValue)
        }

        return buckets.values.filter { it.second != 0.0 }.map { LedgerLine(it.first, it.second) }
    }

    private fun classifyAsset(assetType: String): String {
        val t = assetType.lowercase()
        return when {
            t.contains("business") -> "business"
            t.contains("land") || t.contains("house") || t.contains("property") || t.contains("real_estate") -> "property"
            t.contains("agri") -> "agri"
            t.contains("invest") || t.contains("share") || t.contains("bond") || t.contains("dps") ||
                    t.contains("savings_cert") || t.contains("stock") || t.contains("fdr") -> "investment"
            t.contains("vehicle") || t.contains("car") || t.contains("motor") -> "vehicle"
            t.contains("jewel") || t.contains("gold") -> "jewellery"
            t.contains("furniture") -> "furniture"
            t.contains("electronic") || t.contains("gadget") || t.contains("appliance") -> "electronics"
            t.contains("cash") || t.contains("bank") -> "cash"
            else -> "other"
        }
    }

    private fun bucketLiabilities(liabilities: List<TaxLiability>): List<LedgerLine> {
        val buckets = linkedMapOf(
            "mortgage" to ("Mortgages / Loan Against Property" to 0.0),
            "bank_loan" to ("Bank Loan" to 0.0),
            "other" to ("Other Loans / Unsecured Liabilities" to 0.0)
        )

        liabilities.forEach { liability ->
            val key = classifyLiability(liability.liabilityType)
            val (label, current) = buckets.getValue(key)
            buckets[key] = label to (current + liability.principal)
        }

        return buckets.values.filter { it.second != 0.0 }.map { LedgerLine(it.first, it.second) }
    }

    private fun classifyLiability(liabilityType: String): String {
        val t = liabilityType.lowercase()
        return when {
            t.contains("mortgage") -> "mortgage"
            t.contains("bank") || t.contains("loan") -> "bank_loan"
            else -> "other"
        }
    }

    // ── Core page/table rendering engine ──────────────────────────────

    private class FormRenderer(private val document: PdfDocument, private val format: String) {

        private var pageNumber = 0
        private lateinit var page: PdfDocument.Page
        private lateinit var canvas: Canvas
        private var y = 0f

        private val titlePaint = Paint().apply {
            color = Color.BLACK; textSize = 13f; isFakeBoldText = true; isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        private val subtitlePaint = Paint(titlePaint).apply { textSize = 11f }
        private val labelPaint = Paint().apply { color = Color.BLACK; textSize = 9.5f; isAntiAlias = true }
        private val boldPaint = Paint(labelPaint).apply { isFakeBoldText = true }
        private val smallPaint = Paint(labelPaint).apply { textSize = 8f; color = Color.DKGRAY }
        private val sectionHeaderPaint = Paint().apply {
            color = Color.WHITE; textSize = 10.5f; isFakeBoldText = true; isAntiAlias = true
        }
        private val borderPaint = Paint().apply {
            color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 0.8f; isAntiAlias = true
        }
        private val sectionBgPaint = Paint().apply { color = Color.rgb(55, 65, 81); style = Paint.Style.FILL }
        private val headerBgPaint = Paint().apply { color = Color.rgb(229, 231, 235); style = Paint.Style.FILL }
        private val dashedPaint = Paint().apply {
            color = Color.DKGRAY; style = Paint.Style.STROKE; strokeWidth = 0.8f
            pathEffect = DashPathEffect(floatArrayOf(2f, 2f), 0f)
        }

        init { startNewPage() }

        private fun startNewPage() {
            if (::page.isInitialized) document.finishPage(page)
            pageNumber++
            val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            page = document.startPage(info)
            canvas = page.canvas
            y = 36f
            drawFooter()
            if (pageNumber > 1) {
                canvas.drawText("(Continued)", MARGIN_LEFT, y, smallPaint)
                y += 14f
            }
        }

        private fun drawFooter() {
            val footerPaint = Paint(smallPaint).apply { textAlign = Paint.Align.CENTER }
            canvas.drawText(
                "Page $pageNumber — Generated via NisabWallet — Not a substitute for the official NBR e-Return / paper form",
                PAGE_WIDTH / 2f, PAGE_HEIGHT - 24f, footerPaint
            )
        }

        private fun ensureSpace(required: Float) {
            if (y + required > BOTTOM_LIMIT) startNewPage()
        }

        fun finish() {
            document.finishPage(page)
        }

        // ── Header ──
        fun drawFormHeader(profile: TaxProfile, taxYear: TaxYearRecord) {
            val cx = PAGE_WIDTH / 2f
            canvas.drawText("GOVERNMENT OF THE PEOPLE'S REPUBLIC OF BANGLADESH", cx, y, subtitlePaint); y += 16f
            canvas.drawText("NATIONAL BOARD OF REVENUE", cx, y, titlePaint); y += 18f

            val formLabel = if (format.contains("2024")) "IT-10BB2024" else "IT-10BB"
            canvas.drawText("$formLabel — STATEMENT OF ASSETS, LIABILITIES AND EXPENSES", cx, y, titlePaint); y += 14f
            canvas.drawText("[As required under section 166 of the Income Tax Act / Rule 25]", cx, y, smallPaint); y += 22f

            val boxTop = y
            val boxHeight = 76f
            canvas.drawRect(MARGIN_LEFT, boxTop, MARGIN_LEFT + CONTENT_WIDTH, boxTop + boxHeight, borderPaint)
            var iy = boxTop + 16f
            val col2X = MARGIN_LEFT + CONTENT_WIDTH / 2f

            canvas.drawText("Name of the Assessee:", MARGIN_LEFT + 8f, iy, boldPaint)
            canvas.drawText(profile.taxpayerName.ifBlank { "________________________" }, MARGIN_LEFT + 130f, iy, labelPaint)
            canvas.drawText("TIN:", col2X + 8f, iy, boldPaint)
            canvas.drawText(profile.tin.ifBlank { "________________" }, col2X + 40f, iy, labelPaint)
            iy += 18f

            canvas.drawText("Assessment Year:", MARGIN_LEFT + 8f, iy, boldPaint)
            canvas.drawText(taxYear.taxYear, MARGIN_LEFT + 130f, iy, labelPaint)
            canvas.drawText("Status:", col2X + 8f, iy, boldPaint)
            canvas.drawText("Individual", col2X + 55f, iy, labelPaint)
            iy += 18f

            canvas.drawText("Income Year:", MARGIN_LEFT + 8f, iy, boldPaint)
            canvas.drawText(taxYear.incomeYear, MARGIN_LEFT + 130f, iy, labelPaint)
            canvas.drawText("Filing Deadline:", col2X + 8f, iy, boldPaint)
            canvas.drawText(formatDate(taxYear.filingDeadline), col2X + 90f, iy, labelPaint)
            iy += 18f

            canvas.drawText(
                "Fiscal Period: ${formatDate(taxYear.fiscalYearStart)} to ${formatDate(taxYear.fiscalYearEnd)}",
                MARGIN_LEFT + 8f, iy, labelPaint
            )

            y = boxTop + boxHeight + 20f
        }

        // ── Section header bar ──
        private fun drawSectionBar(text: String) {
            ensureSpace(26f)
            canvas.drawRect(MARGIN_LEFT, y, MARGIN_LEFT + CONTENT_WIDTH, y + 20f, sectionBgPaint)
            canvas.drawText(text, MARGIN_LEFT + 6f, y + 14f, sectionHeaderPaint)
            y += 20f
        }

        // ── 4-column ledger table ──
        private fun drawTableHeader() {
            ensureSpace(20f)
            val top = y
            canvas.drawRect(MARGIN_LEFT, top, MARGIN_LEFT + CONTENT_WIDTH, top + 18f, headerBgPaint)
            var x = MARGIN_LEFT
            canvas.drawText("SL", x + 6f, top + 13f, boldPaint); x += COL_SL
            canvas.drawText("Particulars", x + 6f, top + 13f, boldPaint); x += COL_PARTICULARS
            val amtCurPaint = Paint(boldPaint).apply { textAlign = Paint.Align.RIGHT }
            canvas.drawText("This Year (BDT)", x + COL_AMOUNT_CURRENT - 6f, top + 13f, amtCurPaint); x += COL_AMOUNT_CURRENT
            canvas.drawText("Last Year (BDT)", x + COL_AMOUNT_PREVIOUS - 6f, top + 13f, amtCurPaint)
            drawRowBorders(top, 18f)
            y = top + 18f
        }

        private fun drawRowBorders(top: Float, height: Float) {
            var x = MARGIN_LEFT
            canvas.drawRect(x, top, x + COL_SL, top + height, borderPaint); x += COL_SL
            canvas.drawRect(x, top, x + COL_PARTICULARS, top + height, borderPaint); x += COL_PARTICULARS
            canvas.drawRect(x, top, x + COL_AMOUNT_CURRENT, top + height, borderPaint); x += COL_AMOUNT_CURRENT
            canvas.drawRect(x, top, x + COL_AMOUNT_PREVIOUS, top + height, borderPaint)
        }

        private fun drawTableRow(sl: String, label: String, amount: Double?, bold: Boolean = false, blankPrevYear: Boolean = true) {
            ensureSpace(18f)
            val top = y
            val rowH = 18f
            val p = if (bold) boldPaint else labelPaint
            var x = MARGIN_LEFT
            canvas.drawText(sl, x + 6f, top + 13f, p); x += COL_SL
            canvas.drawText(truncate(label, COL_PARTICULARS - 10f, p), x + 6f, top + 13f, p); x += COL_PARTICULARS

            val amtPaint = Paint(p).apply { textAlign = Paint.Align.RIGHT }
            if (amount != null) {
                canvas.drawText(CurrencyFormatter.formatBDT(amount), x + COL_AMOUNT_CURRENT - 6f, top + 13f, amtPaint)
            }
            x += COL_AMOUNT_CURRENT
            if (blankPrevYear) {
                canvas.drawLine(x + 10f, top + 13f, x + COL_AMOUNT_PREVIOUS - 10f, top + 13f, dashedPaint)
            }

            drawRowBorders(top, rowH)
            y = top + rowH
        }

        private fun truncate(text: String, maxWidth: Float, paint: Paint): String {
            if (paint.measureText(text) <= maxWidth) return text
            var t = text
            while (t.isNotEmpty() && paint.measureText("$t…") > maxWidth) t = t.dropLast(1)
            return "$t…"
        }

        fun drawAssetsTable(assets: List<TaxAsset>, taxYear: TaxYearRecord): Double {
            drawSectionBar("PART A — STATEMENT OF ASSETS (Wealth Statement)")
            drawTableHeader()
            val lines = bucketAssets(assets)
            if (lines.isEmpty()) {
                drawTableRow("-", "No assets recorded", null)
            } else {
                lines.forEachIndexed { i, line -> drawTableRow((i + 1).toString(), line.label, line.amount) }
            }
            val total = lines.sumOf { it.amount }
            drawTableRow("", "Total Assets (A)", total, bold = true, blankPrevYear = false)
            y += 12f
            return total
        }

        fun drawLiabilitiesTable(liabilities: List<TaxLiability>): Double {
            drawSectionBar("PART B — STATEMENT OF LIABILITIES")
            drawTableHeader()
            val lines = bucketLiabilities(liabilities)
            if (lines.isEmpty()) {
                drawTableRow("-", "No liabilities recorded", null)
            } else {
                lines.forEachIndexed { i, line -> drawTableRow((i + 1).toString(), line.label, line.amount) }
            }
            val total = lines.sumOf { it.amount }
            drawTableRow("", "Total Liabilities (B)", total, bold = true, blankPrevYear = false)
            y += 12f
            return total
        }

        fun drawNetWealthSummary(totalAssets: Double, totalLiabilities: Double, netWealth: Double) {
            drawSectionBar("PART C — NET WEALTH")
            ensureSpace(60f)
            val rows = listOf(
                "Total Assets (A)" to totalAssets,
                "Total Liabilities (B)" to totalLiabilities,
                "Net Wealth as on last date of this Income Year (A − B)" to netWealth
            )
            rows.forEachIndexed { idx, (label, amt) ->
                val bold = idx == rows.lastIndex
                val p = if (bold) boldPaint else labelPaint
                val top = y
                canvas.drawRect(MARGIN_LEFT, top, MARGIN_LEFT + CONTENT_WIDTH, top + 18f, borderPaint)
                canvas.drawText(label, MARGIN_LEFT + 8f, top + 13f, p)
                val amtPaint = Paint(p).apply { textAlign = Paint.Align.RIGHT }
                canvas.drawText("BDT ${CurrencyFormatter.formatBDT(amt)}", MARGIN_LEFT + CONTENT_WIDTH - 8f, top + 13f, amtPaint)
                y = top + 18f
            }
            y += 12f
        }

        fun drawExpenseStatement(analysis: TransactionAnalysisResult, compact: Boolean) {
            drawSectionBar("PART D — STATEMENT OF PERSONAL & FAMILY EXPENSES DURING THE YEAR")
            drawTableHeader()

            if (compact) {
                var sl = 1
                val rows = listOf(
                    "Personal & Family Living Expenses" to analysis.personal.values.sum(),
                    "Tax Paid (Advance Tax / Tax Deducted at Source)" to analysis.taxPaid.values.sum(),
                    "Investment Made During the Year" to analysis.investments.values.sum(),
                    "Loan / Installment Repayment" to analysis.loanRepayment.values.sum()
                ).filter { it.second != 0.0 }

                if (rows.isEmpty()) drawTableRow("-", "No categorised expenses recorded", null)
                else rows.forEach { (label, amt) -> drawTableRow((sl++).toString(), label, amt) }

                val total = rows.sumOf { it.second }
                drawTableRow("", "Total Expenses", total, bold = true, blankPrevYear = false)
            } else {
                var sl = 1
                val groups = listOf(
                    "Personal & Family Expenses" to analysis.personal,
                    "Tax Paid" to analysis.taxPaid,
                    "Investments" to analysis.investments,
                    "Loan Repayment" to analysis.loanRepayment
                )
                var any = false
                groups.forEach { (groupLabel, map) ->
                    if (map.isNotEmpty()) {
                        any = true
                        map.entries.sortedByDescending { it.value }.forEach { (catId, amt) ->
                            val cat = TaxCategoryUtils.ALL_EXPENSE_TAX_CATEGORIES.find { it.id == catId }
                            drawTableRow((sl++).toString(), "$groupLabel — ${cat?.name ?: catId}", amt)
                        }
                    }
                }
                if (!any) drawTableRow("-", "No categorised expenses recorded", null)
                val total = groups.sumOf { it.second.values.sum() }
                drawTableRow("", "Total Expenses", total, bold = true, blankPrevYear = false)
            }
            y += 12f
        }

        fun drawReconciliation(taxYear: TaxYearRecord, analysis: TransactionAnalysisResult, netWealth: Double) {
            drawSectionBar("PART E — RECONCILIATION OF NET WEALTH")
            ensureSpace(140f)

            val totalExpenses = analysis.totalExpenses
            val totalIncome = analysis.totalIncome

            val rows = mutableListOf<Triple<String, String, Boolean>>()
            rows.add(Triple("Net wealth as on last date of this income year", "BDT ${CurrencyFormatter.formatBDT(netWealth)}", true))
            rows.add(Triple("Net wealth as on last date of the preceding income year", "________________ (carry forward from last year's statement)", false))
            rows.add(Triple("Increase / (decrease) in net wealth during the year", "________________", false))
            rows.add(Triple("Add: Personal & family expenditure during the year", "BDT ${CurrencyFormatter.formatBDT(totalExpenses)}", true))
            rows.add(Triple("Total source of fund required", "________________", false))
            rows.add(Triple("Less: Income shown in Return / Total Income during the year", "BDT ${CurrencyFormatter.formatBDT(totalIncome)}", true))
            rows.add(Triple("Difference, if any (to be explained)", "________________", false))

            rows.forEach { (label, value, filled) ->
                ensureSpace(18f)
                val top = y
                canvas.drawRect(MARGIN_LEFT, top, MARGIN_LEFT + CONTENT_WIDTH, top + 18f, borderPaint)
                canvas.drawText(label, MARGIN_LEFT + 8f, top + 13f, labelPaint)
                val p = Paint(if (filled) boldPaint else labelPaint).apply { textAlign = Paint.Align.RIGHT }
                canvas.drawText(value, MARGIN_LEFT + CONTENT_WIDTH - 8f, top + 13f, p)
                y = top + 18f
            }
            y += 6f
            canvas.drawText("Note: Rows left blank require figures from the previous year's filed statement, which this app does not retain.", MARGIN_LEFT, y, smallPaint)
            y += 20f
        }

        fun drawVerification(profile: TaxProfile) {
            ensureSpace(110f)
            drawSectionBar("VERIFICATION")
            ensureSpace(90f)
            val text = "I, ${profile.taxpayerName.ifBlank { "___________________" }}, TIN ${profile.tin.ifBlank { "___________" }}, " +
                    "solemnly declare that to the best of my knowledge and belief the information given in this statement is correct and complete."
            y = drawWrappedText(text, MARGIN_LEFT, y + 12f, CONTENT_WIDTH, labelPaint)
            y += 40f

            val lineY = y
            canvas.drawLine(MARGIN_LEFT, lineY, MARGIN_LEFT + 180f, lineY, borderPaint)
            canvas.drawText("Signature of the Assessee", MARGIN_LEFT, lineY + 14f, smallPaint)

            canvas.drawLine(MARGIN_LEFT + 220f, lineY, MARGIN_LEFT + 340f, lineY, borderPaint)
            canvas.drawText("Date", MARGIN_LEFT + 220f, lineY + 14f, smallPaint)

            canvas.drawLine(MARGIN_LEFT + 370f, lineY, MARGIN_LEFT + CONTENT_WIDTH, lineY, borderPaint)
            canvas.drawText("Place", MARGIN_LEFT + 370f, lineY + 14f, smallPaint)
            y = lineY + 30f
        }

        private fun drawWrappedText(text: String, x: Float, startY: Float, maxWidth: Float, paint: Paint): Float {
            var currentY = startY
            val words = text.split(" ")
            var line = StringBuilder()
            for (word in words) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(candidate) > maxWidth) {
                    ensureSpace(16f)
                    canvas.drawText(line.toString(), x, currentY, paint)
                    currentY += 14f
                    line = StringBuilder(word)
                } else {
                    line = StringBuilder(candidate)
                }
            }
            if (line.isNotEmpty()) {
                ensureSpace(16f)
                canvas.drawText(line.toString(), x, currentY, paint)
                currentY += 14f
            }
            return currentY
        }
    }

    private fun formatDate(dateStr: String): String = try {
        SimpleDateFormat("dd MMM yyyy", Locale.US).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr)!!)
    } catch (_: Exception) {
        dateStr
    }
}