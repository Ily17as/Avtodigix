package com.example.avtodigix.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.avtodigix.R
import com.example.avtodigix.databinding.ActivityReportBinding
import com.example.avtodigix.storage.AppDatabase
import com.example.avtodigix.storage.ScanSnapshotRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportActivity : AppCompatActivity() {
    private lateinit var binding: ActivityReportBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val snapshotId = intent.getLongExtra(EXTRA_SNAPSHOT_ID, -1)
        if (snapshotId <= 0L) {
            finish()
            return
        }

        val repository = ScanSnapshotRepository(AppDatabase.create(this).scanSnapshotDao())
        lifecycleScope.launch {
            val snapshot = repository.getSnapshot(snapshotId) ?: run {
                finish()
                return@launch
            }
            val reportText = buildSnapshotReport(this@ReportActivity, snapshot)
            val time = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                .format(Date(snapshot.timestampMillis))
            binding.reportDetailTitle.text = time
            binding.reportDetailSummary.text = getString(R.string.report_dtc_count, snapshot.dtcList.size)
            binding.reportDetailMetrics.text = getString(
                R.string.report_metrics_block,
                snapshot.keyMetrics.entries.joinToString("\n") { "${it.key}: ${it.value}" }.ifBlank { "—" }
            )
            binding.reportDetailDtcs.text = getString(
                R.string.report_dtc_block,
                snapshot.dtcList.joinToString("\n").ifBlank { getString(R.string.dtc_none) }
            )

            binding.reportDetailShare.setOnClickListener {
                val intent = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.report_history_title))
                    .putExtra(Intent.EXTRA_TEXT, reportText)
                startActivity(Intent.createChooser(intent, getString(R.string.action_share)))
            }
        }
    }

    companion object {
        const val EXTRA_SNAPSHOT_ID = "snapshot_id"
    }
}
